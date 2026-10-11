package com.example.MedRational.Services;

import com.example.MedRational.DTOs.CreateWorkshopProjectDTO;
import com.example.MedRational.DTOs.WorkshopProjectResponseDTO;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.WorkshopProject;
import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Entities.enums.ProjectType;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Repositories.WorkshopAttachmentRepository;
import com.example.MedRational.Repositories.WorkshopProjectRepository;
import com.example.MedRational.Services.Implementations.WorkshopProjectServiceImpl;
import com.example.MedRational.Services.Interfaces.R2StorageService;
import com.example.MedRational.Services.Interfaces.WorkshopNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        WorkshopProject closingSoon = project(ProjectStatus.OPEN);
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

    @Test
    void reminderQueriesLookTenMinutesAhead() {
        LocalDateTime before = LocalDateTime.now();

        service.processAutomaticStateTransitions();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(projectRepository).findProjectsDueForOpeningReminder(threshold.capture());
        assertTrue(!threshold.getValue().isBefore(before.plusMinutes(10)));
        assertTrue(threshold.getValue().isBefore(before.plusMinutes(11)));
    }
}
