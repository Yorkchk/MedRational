package com.example.MedRational.Services;

import com.example.MedRational.DTOs.CreateWorkshopProjectDTO;
import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.DTOs.WorkshopProjectResponseDTO;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.WorkshopAttachment;
import com.example.MedRational.Entities.WorkshopProject;
import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Entities.enums.ProjectType;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Repositories.WorkshopAttachmentRepository;
import com.example.MedRational.Repositories.WorkshopProjectRepository;
import com.example.MedRational.Services.Implementations.WorkshopProjectServiceImpl;
import com.example.MedRational.Services.Interfaces.R2StorageService;
import com.example.MedRational.Services.Interfaces.WorkshopNotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkshopProjectServiceImplTest {

    private static final long AUTHOR_ID = 1L;
    private static final long PROJECT_ID = 5L;
    private static final String LINK = "https://forms.example.com/workshop";

    private final WorkshopProjectRepository projectRepository = mock(WorkshopProjectRepository.class);
    private final WorkshopAttachmentRepository attachmentRepository = mock(WorkshopAttachmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final R2StorageService r2StorageService = mock(R2StorageService.class);
    private final WorkshopNotificationService notificationService = mock(WorkshopNotificationService.class);

    private final WorkshopProjectServiceImpl service = new WorkshopProjectServiceImpl(
            projectRepository, attachmentRepository, userRepository, r2StorageService, notificationService);

    private final User author = User.builder().id(AUTHOR_ID).email("admin@example.com").build();

    @BeforeEach
    void setUp() {
        when(projectRepository.findProjectsDueForOpeningReminder(any())).thenReturn(List.of());
        when(projectRepository.findProjectsReadyToOpen(any())).thenReturn(List.of());
        when(projectRepository.findProjectsDueForClosingReminder(any())).thenReturn(List.of());
        when(projectRepository.findProjectsReadyToClose(any())).thenReturn(List.of());
    }

    private WorkshopProject project(ProjectStatus status) {
        return WorkshopProject.builder()
                .id(PROJECT_ID)
                .author(author)
                .title("Suturing")
                .description("Hands-on")
                .projectType(ProjectType.FIRST_COME_FIRST_SERVED)
                .status(status)
                .formLink(LINK)
                .capacity(30)
                .registrationOpensAt(LocalDateTime.now().minusHours(1))
                .registrationClosesAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    @Test
    void createKeepsTheCapacity() {
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(author));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CreateWorkshopProjectDTO request = CreateWorkshopProjectDTO.builder()
                .title(" Suturing ")
                .description("Hands-on")
                .projectType(ProjectType.FIRST_COME_FIRST_SERVED)
                .formLink(LINK)
                .capacity(25)
                .registrationOpensAt(LocalDateTime.now().plusDays(1))
                .registrationClosesAt(LocalDateTime.now().plusDays(2))
                .build();

        WorkshopProjectResponseDTO created = service.createProject(request, AUTHOR_ID);

        ArgumentCaptor<WorkshopProject> saved = ArgumentCaptor.forClass(WorkshopProject.class);
        verify(projectRepository).save(saved.capture());
        assertEquals(25, saved.getValue().getCapacity());
        assertEquals(25, created.getCapacity());
        assertEquals("Suturing", created.getTitle());
        assertEquals(ProjectStatus.UPCOMING, created.getStatus());
        verify(notificationService).notifyProjectCreated(saved.getValue());
    }

    private CreateWorkshopProjectDTO edit(LocalDateTime opensAt, LocalDateTime closesAt) {
        return CreateWorkshopProjectDTO.builder()
                .title(" Advanced suturing ")
                .description(" Bring your kit ")
                .projectType(ProjectType.FIRST_COME_FIRST_SERVED)
                .formLink("https://forms.example.com/advanced")
                .capacity(12)
                .registrationOpensAt(opensAt)
                .registrationClosesAt(closesAt)
                .build();
    }

    private WorkshopProject stored(WorkshopProject project) {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return project;
    }

    @Test
    void updateChangesTheDetails() {
        WorkshopProject project = stored(project(ProjectStatus.OPEN));

        WorkshopProjectResponseDTO updated = service.updateProject(
                PROJECT_ID, edit(project.getRegistrationOpensAt(), project.getRegistrationClosesAt()));

        assertEquals("Advanced suturing", updated.getTitle());
        assertEquals("Bring your kit", updated.getDescription());
        assertEquals("https://forms.example.com/advanced", updated.getFormLink());
        assertEquals(12, updated.getCapacity());
        verify(projectRepository).save(project);
    }

    @Test
    void unchangedDatesKeepAManuallyForcedStatus() {
        // Open by its dates, but an admin closed it early because the form was full
        WorkshopProject project = stored(project(ProjectStatus.CLOSED));

        service.updateProject(PROJECT_ID, edit(project.getRegistrationOpensAt(), project.getRegistrationClosesAt()));

        assertEquals(ProjectStatus.CLOSED, project.getStatus());
    }

    @Test
    void movingTheDatesAheadReopensTheScheduleAndItsReminders() {
        WorkshopProject project = project(ProjectStatus.CLOSED);
        project.setOpeningReminderSent(true);
        project.setLaunchNotificationSent(true);
        project.setClosingReminderSent(true);
        project.setClosedNotificationSent(true);
        stored(project);

        service.updateProject(PROJECT_ID, edit(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2)));

        assertEquals(ProjectStatus.UPCOMING, project.getStatus());
        assertFalse(project.isOpeningReminderSent());
        assertFalse(project.isLaunchNotificationSent());
        assertFalse(project.isClosingReminderSent());
        assertFalse(project.isClosedNotificationSent());
    }

    @Test
    void extendingTheDeadlineOfAnOpenProjectOnlyResetsTheClosingReminders() {
        WorkshopProject project = project(ProjectStatus.CLOSED);
        project.setRegistrationClosesAt(LocalDateTime.now().minusMinutes(5));
        project.setClosingReminderSent(true);
        project.setClosedNotificationSent(true);
        stored(project);

        service.updateProject(PROJECT_ID, edit(project.getRegistrationOpensAt(), LocalDateTime.now().plusHours(3)));

        assertEquals(ProjectStatus.OPEN, project.getStatus());
        assertTrue(project.isOpeningReminderSent());
        assertTrue(project.isLaunchNotificationSent());
        assertFalse(project.isClosingReminderSent());
        assertFalse(project.isClosedNotificationSent());
    }

    @Test
    void closingByHandAnnouncesItOnce() {
        WorkshopProject project = stored(project(ProjectStatus.OPEN));

        service.updateProjectStatus(PROJECT_ID, ProjectStatus.CLOSED);

        assertEquals(ProjectStatus.CLOSED, project.getStatus());
        verify(notificationService).notifyRegistrationClosed(project);
        // The scheduler must not send its own closing alerts later
        assertTrue(project.isClosingReminderSent());
        assertTrue(project.isClosedNotificationSent());
    }

    @Test
    void openingByHandAnnouncesItAndKeepsTheClosingReminder() {
        WorkshopProject project = project(ProjectStatus.UPCOMING);
        project.setRegistrationOpensAt(LocalDateTime.now().plusDays(1));
        project.setRegistrationClosesAt(LocalDateTime.now().plusDays(2));
        stored(project);

        service.updateProjectStatus(PROJECT_ID, ProjectStatus.OPEN);

        assertEquals(ProjectStatus.OPEN, project.getStatus());
        verify(notificationService).notifyRegistrationOpened(project);
        assertTrue(project.isOpeningReminderSent());
        assertTrue(project.isLaunchNotificationSent());
        assertFalse(project.isClosingReminderSent());
    }

    // The scheduler only closes OPEN projects whose closedNotificationSent is false: marking the
    // passed deadline as handled keeps the admin's reopen instead of closing it again a minute later
    @Test
    void reopeningAfterTheDeadlineIsNotUndoneByTheScheduler() {
        WorkshopProject project = project(ProjectStatus.CLOSED);
        project.setRegistrationClosesAt(LocalDateTime.now().minusMinutes(5));
        project.setClosingReminderSent(true);
        project.setClosedNotificationSent(true);
        stored(project);

        service.updateProjectStatus(PROJECT_ID, ProjectStatus.OPEN);

        assertEquals(ProjectStatus.OPEN, project.getStatus());
        assertTrue(project.isClosingReminderSent());
        assertTrue(project.isClosedNotificationSent());
    }

    @Test
    void backToUpcomingAfterOpeningStaysUpcoming() {
        WorkshopProject project = project(ProjectStatus.OPEN);
        project.setOpeningReminderSent(true);
        project.setLaunchNotificationSent(true);
        stored(project);

        service.updateProjectStatus(PROJECT_ID, ProjectStatus.UPCOMING);

        assertEquals(ProjectStatus.UPCOMING, project.getStatus());
        // Opening already passed: handled, so the scheduler doesn't reopen it
        assertTrue(project.isOpeningReminderSent());
        assertTrue(project.isLaunchNotificationSent());
        // The deadline is still ahead
        assertFalse(project.isClosingReminderSent());
        assertFalse(project.isClosedNotificationSent());
    }

    @Test
    void backToUpcomingBeforeOpeningStillOpensOnTime() {
        // Opened early by hand, then set back: the scheduler should open it at its date
        WorkshopProject project = project(ProjectStatus.OPEN);
        project.setRegistrationOpensAt(LocalDateTime.now().plusDays(1));
        project.setRegistrationClosesAt(LocalDateTime.now().plusDays(2));
        project.setOpeningReminderSent(true);
        project.setLaunchNotificationSent(true);
        stored(project);

        service.updateProjectStatus(PROJECT_ID, ProjectStatus.UPCOMING);

        assertFalse(project.isOpeningReminderSent());
        assertFalse(project.isLaunchNotificationSent());
        assertFalse(project.isClosingReminderSent());
        assertFalse(project.isClosedNotificationSent());
    }

    @Test
    void openingEarlyStillClosesAtTheDeadline() {
        WorkshopProject project = project(ProjectStatus.UPCOMING);
        project.setRegistrationOpensAt(LocalDateTime.now().plusDays(1));
        project.setRegistrationClosesAt(LocalDateTime.now().plusDays(2));
        stored(project);

        service.updateProjectStatus(PROJECT_ID, ProjectStatus.OPEN);

        assertFalse(project.isClosedNotificationSent());
    }

    @Test
    void backToUpcomingAnnouncesNothing() {
        stored(project(ProjectStatus.OPEN));

        service.updateProjectStatus(PROJECT_ID, ProjectStatus.UPCOMING);

        verify(notificationService, never()).notifyRegistrationOpened(any());
        verify(notificationService, never()).notifyRegistrationClosed(any());
    }

    @Test
    void choosingTheCurrentStatusAnnouncesNothing() {
        stored(project(ProjectStatus.CLOSED));

        service.updateProjectStatus(PROJECT_ID, ProjectStatus.CLOSED);

        verify(notificationService, never()).notifyRegistrationClosed(any());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateRejectsClosingBeforeOpening() {
        stored(project(ProjectStatus.UPCOMING));
        LocalDateTime opensAt = LocalDateTime.now().plusDays(2);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProject(PROJECT_ID, edit(opensAt, opensAt.minusHours(1))));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void updatingAMissingProjectIsNotFound() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.updateProject(
                PROJECT_ID, edit(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2))));
    }

    // The admin who created it deleted their account: the project stays, with no author
    @Test
    void aProjectWithoutAuthorStillShows() {
        WorkshopProject project = project(ProjectStatus.OPEN);
        project.setAuthor(null);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        WorkshopProjectResponseDTO shown = service.getProjectById(PROJECT_ID, false);

        assertEquals("Suturing", shown.getTitle());
        assertNull(shown.getAuthorId());
        assertNull(shown.getAuthorEmail());
    }

    @Test
    void studentsDoNotSeeTheLinkBeforeRegistrationOpens() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.UPCOMING)));

        assertNull(service.getProjectById(PROJECT_ID, false).getFormLink());
        assertEquals(LINK, service.getProjectById(PROJECT_ID, true).getFormLink());
    }

    @Test
    void studentsSeeTheLinkWhileRegistrationIsOpen() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.OPEN)));

        assertEquals(LINK, service.getProjectById(PROJECT_ID, false).getFormLink());
    }

    @Test
    void studentsDoNotSeeTheLinkOnceRegistrationCloses() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.CLOSED)));

        assertNull(service.getProjectById(PROJECT_ID, false).getFormLink());
    }

    @Test
    void schedulerOpensDueProjectsAndAnnouncesThem() {
        WorkshopProject due = project(ProjectStatus.UPCOMING);
        when(projectRepository.findProjectsReadyToOpen(any())).thenReturn(List.of(due));

        service.processAutomaticStateTransitions();

        assertEquals(ProjectStatus.OPEN, due.getStatus());
        assertTrue(due.isLaunchNotificationSent());
        verify(notificationService).notifyRegistrationOpened(due);
    }

    @Test
    void schedulerClosesDueProjectsAndAnnouncesThem() {
        WorkshopProject due = project(ProjectStatus.OPEN);
        when(projectRepository.findProjectsReadyToClose(any())).thenReturn(List.of(due));

        service.processAutomaticStateTransitions();

        assertEquals(ProjectStatus.CLOSED, due.getStatus());
        assertTrue(due.isClosedNotificationSent());
        verify(notificationService).notifyRegistrationClosed(due);
    }

    @Test
    void schedulerSendsTenMinuteRemindersOnceAndLeavesTheStatusAlone() {
        WorkshopProject openingSoon = project(ProjectStatus.UPCOMING);
        openingSoon.setRegistrationOpensAt(LocalDateTime.now().plusMinutes(9));
        openingSoon.setRegistrationClosesAt(LocalDateTime.now().plusHours(2));
        WorkshopProject closingSoon = project(ProjectStatus.OPEN);
        closingSoon.setRegistrationClosesAt(LocalDateTime.now().plusMinutes(9));
        when(projectRepository.findProjectsDueForOpeningReminder(any())).thenReturn(List.of(openingSoon));
        when(projectRepository.findProjectsDueForClosingReminder(any())).thenReturn(List.of(closingSoon));

        service.processAutomaticStateTransitions();

        verify(notificationService).notifyOpeningSoon(openingSoon);
        verify(notificationService).notifyClosingSoon(closingSoon);
        assertTrue(openingSoon.isOpeningReminderSent());
        assertTrue(closingSoon.isClosingReminderSent());
        assertEquals(ProjectStatus.UPCOMING, openingSoon.getStatus());
        assertEquals(ProjectStatus.OPEN, closingSoon.getStatus());
        verify(notificationService, never()).notifyRegistrationOpened(any());
        verify(notificationService, never()).notifyRegistrationClosed(any());
    }

    // E.g. a project whose dates are 12 hours in the past, set back to UPCOMING: every query
    // returns it in the same run. It must be caught up silently, not get four alerts at once
    @Test
    void projectWhoseWholeWindowPassedClosesWithoutAnyAlert() {
        WorkshopProject stale = project(ProjectStatus.UPCOMING);
        stale.setRegistrationOpensAt(LocalDateTime.now().minusHours(12));
        stale.setRegistrationClosesAt(LocalDateTime.now().minusHours(12).plusMinutes(1));
        when(projectRepository.findProjectsDueForOpeningReminder(any())).thenReturn(List.of(stale));
        when(projectRepository.findProjectsReadyToOpen(any())).thenReturn(List.of(stale));

        service.processAutomaticStateTransitions();

        assertEquals(ProjectStatus.CLOSED, stale.getStatus());
        assertTrue(stale.isOpeningReminderSent());
        assertTrue(stale.isLaunchNotificationSent());
        assertTrue(stale.isClosingReminderSent());
        assertTrue(stale.isClosedNotificationSent());
        verifyNoInteractions(notificationService);
    }

    @Test
    void reminderWhoseMomentPassedIsSkipped() {
        // Opening was 5 minutes ago and the project is still UPCOMING: open it, but don't say "opens in 10 min"
        WorkshopProject late = project(ProjectStatus.UPCOMING);
        late.setRegistrationOpensAt(LocalDateTime.now().minusMinutes(5));
        when(projectRepository.findProjectsDueForOpeningReminder(any())).thenReturn(List.of(late));
        when(projectRepository.findProjectsReadyToOpen(any())).thenReturn(List.of(late));

        service.processAutomaticStateTransitions();

        verify(notificationService, never()).notifyOpeningSoon(any());
        verify(notificationService).notifyRegistrationOpened(late);
        assertEquals(ProjectStatus.OPEN, late.getStatus());
    }

    @Test
    void creatingAProjectThatAlreadyOpenedIsRejected() {
        CreateWorkshopProjectDTO request = edit(LocalDateTime.now().minusHours(12), LocalDateTime.now().minusHours(11));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createProject(request, AUTHOR_ID));
        assertEquals("Registration opening time must be in the future.", error.getMessage());
        verify(projectRepository, never()).save(any());
        verify(notificationService, never()).notifyProjectCreated(any());
    }

    @Test
    void reminderQueriesLookTenMinutesAhead() {
        LocalDateTime before = LocalDateTime.now();

        service.processAutomaticStateTransitions();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(projectRepository).findProjectsDueForOpeningReminder(threshold.capture());
        assertTrue(!threshold.getValue().isBefore(before.plusMinutes(10)));
        assertTrue(threshold.getValue().isBefore(before.plusMinutes(11)));
    }

    // --- attachments ------------------------------------------------------------------------------

    private static final long ATTACHMENT_ID = 9L;
    private static final String ATTACHMENT_KEY = "WorkshopAttachments/Project_5/ab12cd34-program.pdf";

    private WorkshopAttachment attachment(String previewStorageKey) {
        return WorkshopAttachment.builder()
                .id(ATTACHMENT_ID)
                .fileName("program.pdf")
                // The private R2 endpoint, which answers 400 to the unsigned requests of a download
                .fileUrl("https://account.r2.cloudflarestorage.com/bucket/" + ATTACHMENT_KEY)
                .previewStorageKey(previewStorageKey)
                .build();
    }

    @Test
    void attachmentIsDownloadedThroughTheBackendFromItsStorageKey() {
        when(attachmentRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(attachment(null)));
        Resource stored = new ByteArrayResource(new byte[]{1, 2});
        when(r2StorageService.downloadFileAsResource(ATTACHMENT_KEY)).thenReturn(stored);

        DownloadableFile file = service.openAttachment(ATTACHMENT_ID);

        assertEquals("program.pdf", file.getFileName());
        assertEquals("application/pdf", file.getContentType());
        assertSame(stored, file.getResource());
    }

    @Test
    void downloadingAMissingAttachmentIsNotFound() {
        when(attachmentRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.openAttachment(ATTACHMENT_ID));
    }

    @Test
    void attachmentsSayWhetherTheyCanBePreviewed() {
        WorkshopProject project = project(ProjectStatus.OPEN);
        WorkshopAttachment pdf = attachment(null);
        WorkshopAttachment zip = attachment(null);
        zip.setFileName("bundle.zip");
        project.setAttachments(List.of(pdf, zip));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        WorkshopProjectResponseDTO dto = service.getProjectById(PROJECT_ID, false);

        assertTrue(dto.getAttachments().get(0).isPreviewable());
        assertFalse(dto.getAttachments().get(1).isPreviewable());
    }

    @Test
    void removingAnAttachmentAlsoDeletesItsCachedPreview() {
        WorkshopAttachment attachment = attachment("Previews/" + ATTACHMENT_KEY + ".pdf");
        when(attachmentRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(attachment));

        service.removeAttachment(ATTACHMENT_ID);

        verify(r2StorageService).deleteFiles(List.of(ATTACHMENT_KEY, "Previews/" + ATTACHMENT_KEY + ".pdf"));
        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void deletingAProjectAlsoDeletesTheCachedPreviews() {
        WorkshopProject project = project(ProjectStatus.OPEN);
        project.setAttachments(new ArrayList<>(List.of(attachment("Previews/p.pdf"))));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        service.deleteProject(PROJECT_ID);

        verify(r2StorageService).deleteFiles(List.of(ATTACHMENT_KEY, "Previews/p.pdf"));
    }
}
