package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.CreateWorkshopProjectDTO;
import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.DTOs.WorkshopAttachmentResponseDTO;
import com.example.MedRational.DTOs.WorkshopProjectResponseDTO;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.WorkshopAttachment;
import com.example.MedRational.Entities.WorkshopProject;
import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Preview.PreviewKind;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Repositories.WorkshopAttachmentRepository;
import com.example.MedRational.Repositories.WorkshopProjectRepository;
import com.example.MedRational.Services.Interfaces.R2StorageService;
import com.example.MedRational.Services.Interfaces.WorkshopNotificationService;
import com.example.MedRational.Services.Interfaces.WorkshopProjectService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkshopProjectServiceImpl implements WorkshopProjectService {

    private final WorkshopProjectRepository workshopProjectRepository;
    private final WorkshopAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final R2StorageService r2StorageService;
    private final WorkshopNotificationService workshopNotificationService;

    @Override
    @Transactional
    public WorkshopProjectResponseDTO createProject(CreateWorkshopProjectDTO request, Long authorId) {
        if (request.getRegistrationClosesAt().isBefore(request.getRegistrationOpensAt())) {
            throw new IllegalArgumentException("Registration closing time cannot be before opening time.");
        }
        // A new project's registration is still ahead; a past time is almost always a mistyped date or AM/PM
        if (!request.getRegistrationOpensAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Registration opening time must be in the future.");
        }

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new EntityNotFoundException("Author user not found with id: " + authorId));

        ProjectStatus initialStatus = statusFor(
                request.getRegistrationOpensAt(), request.getRegistrationClosesAt(), LocalDateTime.now());

        WorkshopProject project = WorkshopProject.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .projectType(request.getProjectType())
                .status(initialStatus)
                .formLink(request.getFormLink())
                .capacity(request.getCapacity())
                .registrationOpensAt(request.getRegistrationOpensAt())
                .registrationClosesAt(request.getRegistrationClosesAt())
                .author(author)
                .attachments(new ArrayList<>())
                .build();

        // Students hear about it while it's upcoming, not only once registration opens
        WorkshopProject saved = workshopProjectRepository.save(project);
        workshopNotificationService.notifyProjectCreated(saved);
        return mapToDTO(saved, true);
    }

    @Override
    @Transactional
    public WorkshopProjectResponseDTO updateProject(Long projectId, CreateWorkshopProjectDTO request) {
        if (request.getRegistrationClosesAt().isBefore(request.getRegistrationOpensAt())) {
            throw new IllegalArgumentException("Registration closing time cannot be before opening time.");
        }

        WorkshopProject project = workshopProjectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Workshop project not found with id: " + projectId));

        project.setTitle(request.getTitle().trim());
        project.setDescription(request.getDescription().trim());
        project.setProjectType(request.getProjectType());
        project.setFormLink(request.getFormLink());
        project.setCapacity(request.getCapacity());

        // New dates mean a new schedule: the status follows them and the reminders are sent again for
        // moments still ahead. Unchanged dates keep the status, so a manual override (e.g. closed early
        // because the form is full) survives editing the description
        boolean datesChanged = !request.getRegistrationOpensAt().equals(project.getRegistrationOpensAt())
                || !request.getRegistrationClosesAt().equals(project.getRegistrationClosesAt());
        if (datesChanged) {
            LocalDateTime now = LocalDateTime.now();
            project.setRegistrationOpensAt(request.getRegistrationOpensAt());
            project.setRegistrationClosesAt(request.getRegistrationClosesAt());
            project.setStatus(statusFor(project.getRegistrationOpensAt(), project.getRegistrationClosesAt(), now));

            boolean opened = !project.getRegistrationOpensAt().isAfter(now);
            boolean closed = !project.getRegistrationClosesAt().isAfter(now);
            project.setOpeningReminderSent(opened);
            project.setLaunchNotificationSent(opened);
            project.setClosingReminderSent(closed);
            project.setClosedNotificationSent(closed);
        }

        return mapToDTO(workshopProjectRepository.save(project), true);
    }

    @Override
    @Transactional
    public WorkshopAttachmentResponseDTO attachFileToProject(Long projectId, MultipartFile file) throws IOException {
        WorkshopProject project = workshopProjectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Workshop project not found with id: " + projectId));

        String folderPrefix = "WorkshopAttachments/Project_" + projectId;
        String storageKey = r2StorageService.uploadFile(file, folderPrefix);
        String publicUrl = r2StorageService.buildPublicUrl(storageKey);

        WorkshopAttachment attachment = WorkshopAttachment.builder()
                .project(project)
                .fileName(file.getOriginalFilename())
                .fileUrl(publicUrl)
                .build();

        return mapAttachmentToDTO(attachmentRepository.save(attachment));
    }

    @Override
    @Transactional
    public List<WorkshopAttachmentResponseDTO> attachMultipleFiles(Long projectId, List<MultipartFile> files) throws IOException {
        List<WorkshopAttachmentResponseDTO> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            responses.add(attachFileToProject(projectId, file));
        }
        return responses;
    }

    @Override
    @Transactional
    public void removeAttachment(Long attachmentId) {
        WorkshopAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found with id: " + attachmentId));

        List<String> keys = storageKeysOf(attachment);
        if (!keys.isEmpty()) {
            r2StorageService.deleteFiles(keys);
        }

        attachmentRepository.delete(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadableFile openAttachment(Long attachmentId) {
        String notFound = "Attachment not found with id: " + attachmentId;
        WorkshopAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException(notFound));
        String key = attachment.storageKey();
        if (key == null) {
            throw new EntityNotFoundException(notFound);
        }

        // The bucket is private, so the file is streamed through the backend
        String fileName = attachment.getFileName();
        PreviewKind kind = PreviewKind.detect(fileName, null);
        String contentType = switch (kind) {
            case IMAGE -> PreviewKind.imageContentType(fileName, null);
            case PDF -> "application/pdf";
            default -> "application/octet-stream";
        };
        return new DownloadableFile(fileName, contentType, r2StorageService.downloadFileAsResource(key));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkshopProjectResponseDTO getProjectById(Long projectId, boolean isAdmin) {
        WorkshopProject project = workshopProjectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Workshop project not found with id: " + projectId));
        return mapToDTO(project, isAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkshopProjectResponseDTO> getProjectsByStatus(ProjectStatus status, Pageable pageable, boolean isAdmin) {
        return workshopProjectRepository.findByStatusOrderByRegistrationOpensAtAsc(status, pageable)
                .map(project -> mapToDTO(project, isAdmin));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkshopProjectResponseDTO> getAllProjects(Pageable pageable, boolean isAdmin) {
        return workshopProjectRepository.findAll(pageable)
                .map(project -> mapToDTO(project, isAdmin));
    }

    @Override
    @Transactional
    public WorkshopProjectResponseDTO updateProjectStatus(Long projectId, ProjectStatus targetStatus) {
        WorkshopProject project = workshopProjectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Workshop project not found with id: " + projectId));

        if (project.getStatus() == targetStatus) {
            return mapToDTO(project, true);
        }
        project.setStatus(targetStatus);

        // A manual status stays until the next date still ahead: moments already passed are marked
        // handled so the scheduler doesn't undo the admin's choice, while future ones stay due.
        // A manual open or close is announced like the scheduler does (WORKSHOP_LAUNCH / WORKSHOP_CLOSED),
        // and marked sent so the scheduler doesn't repeat it. Back to UPCOMING announces nothing
        LocalDateTime now = LocalDateTime.now();
        boolean closePassed = !project.getRegistrationClosesAt().isAfter(now);
        if (targetStatus == ProjectStatus.OPEN) {
            project.setOpeningReminderSent(true);
            project.setLaunchNotificationSent(true);
            project.setClosingReminderSent(closePassed);
            project.setClosedNotificationSent(closePassed);
            workshopNotificationService.notifyRegistrationOpened(project);
        } else if (targetStatus == ProjectStatus.UPCOMING) {
            boolean openPassed = !project.getRegistrationOpensAt().isAfter(now);
            project.setOpeningReminderSent(openPassed);
            project.setLaunchNotificationSent(openPassed);
            project.setClosingReminderSent(closePassed);
            project.setClosedNotificationSent(closePassed);
        } else if (targetStatus == ProjectStatus.CLOSED) {
            project.setClosingReminderSent(true);
            project.setClosedNotificationSent(true);
            workshopNotificationService.notifyRegistrationClosed(project);
        }

        return mapToDTO(workshopProjectRepository.save(project), true);
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId) {
        WorkshopProject project = workshopProjectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Workshop project not found with id: " + projectId));

        List<String> keysToDelete = project.getAttachments().stream()
                .flatMap(a -> storageKeysOf(a).stream())
                .toList();

        if (!keysToDelete.isEmpty()) {
            r2StorageService.deleteFiles(keysToDelete);
        }

        workshopProjectRepository.delete(project);
    }

    // At second 0 of every minute: picked times are whole minutes, so a project switches right on time
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    @Override
    public void processAutomaticStateTransitions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tenMinutesFromNow = now.plusMinutes(10);

        // Each alert is sent only while its moment is still ahead or just happened. A project whose
        // times already passed (e.g. created in the past, or set back to UPCOMING by hand) is caught
        // up silently instead of receiving every alert at once

        // 1. Check: 10 minutes BEFORE launch
        List<WorkshopProject> openingSoon = workshopProjectRepository.findProjectsDueForOpeningReminder(tenMinutesFromNow);
        for (WorkshopProject project : openingSoon) {
            project.setOpeningReminderSent(true);
            if (project.getRegistrationOpensAt().isAfter(now)) {
                workshopNotificationService.notifyOpeningSoon(project);
                log.info("Dispatched 10-min opening reminder for project ID {}", project.getId());
            }
        }

        // 2. Check: Launch moment (UPCOMING -> OPEN, or straight to CLOSED when its window already passed)
        List<WorkshopProject> toOpen = workshopProjectRepository.findProjectsReadyToOpen(now);
        for (WorkshopProject project : toOpen) {
            project.setOpeningReminderSent(true);
            project.setLaunchNotificationSent(true);
            if (!project.getRegistrationClosesAt().isAfter(now)) {
                project.setStatus(ProjectStatus.CLOSED);
                project.setClosingReminderSent(true);
                project.setClosedNotificationSent(true);
                log.info("Project ID {} registration window already passed, closed without notifications", project.getId());
                continue;
            }
            project.setStatus(ProjectStatus.OPEN);
            workshopNotificationService.notifyRegistrationOpened(project);
            log.info("Project ID {} transitioned to OPEN and launch notifications sent", project.getId());
        }

        // 3. Check: 10 minutes BEFORE closing
        List<WorkshopProject> closingSoon = workshopProjectRepository.findProjectsDueForClosingReminder(tenMinutesFromNow);
        for (WorkshopProject project : closingSoon) {
            project.setClosingReminderSent(true);
            if (project.getRegistrationClosesAt().isAfter(now)) {
                workshopNotificationService.notifyClosingSoon(project);
                log.info("Dispatched 10-min closing reminder for project ID {}", project.getId());
            }
        }

        // 4. Check: Closing moment (OPEN -> CLOSED)
        List<WorkshopProject> toClose = workshopProjectRepository.findProjectsReadyToClose(now);
        for (WorkshopProject project : toClose) {
            project.setStatus(ProjectStatus.CLOSED);
            project.setClosingReminderSent(true);
            project.setClosedNotificationSent(true);
            workshopNotificationService.notifyRegistrationClosed(project);
            log.info("Project ID {} transitioned to CLOSED and closed notifications sent", project.getId());
        }
    }

    // Inside com.example.MedRational.Services.Implementations.WorkshopProjectServiceImpl

    private WorkshopProjectResponseDTO mapToDTO(WorkshopProject project, boolean isAdmin) {
        List<WorkshopAttachmentResponseDTO> attachments = project.getAttachments() != null
                ? project.getAttachments().stream().map(this::mapAttachmentToDTO).collect(Collectors.toList())
                : List.of();

        // The form link is strictly protected until registration/application is actively OPEN
        String visibleFormLink = (isAdmin || project.getStatus() == ProjectStatus.OPEN)
                ? project.getFormLink()
                : null;

        // No author once the admin who created it deleted their account
        User author = project.getAuthor();
        return WorkshopProjectResponseDTO.builder()
                .id(project.getId())
                .authorId(author != null ? author.getId() : null)
                .authorEmail(author != null ? author.getEmail() : null)
                .title(project.getTitle())
                .description(project.getDescription())
                .projectType(project.getProjectType())
                .status(project.getStatus())
                .formLink(visibleFormLink)
                .capacity(project.getCapacity())
                .registrationOpensAt(project.getRegistrationOpensAt())
                .registrationClosesAt(project.getRegistrationClosesAt())
                .createdAt(project.getCreatedAt())
                .attachments(attachments)
                .build();
    }

    private ProjectStatus statusFor(LocalDateTime opensAt, LocalDateTime closesAt, LocalDateTime now) {
        if (now.isBefore(opensAt)) {
            return ProjectStatus.UPCOMING;
        }
        if (now.isBefore(closesAt)) {
            return ProjectStatus.OPEN;
        }
        return ProjectStatus.CLOSED;
    }

    private WorkshopAttachmentResponseDTO mapAttachmentToDTO(WorkshopAttachment attachment) {
        return WorkshopAttachmentResponseDTO.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .previewable(PreviewKind.detect(attachment.getFileName(), null).isPreviewable())
                .build();
    }

    // The file and its cached preview, when there is one
    private List<String> storageKeysOf(WorkshopAttachment attachment) {
        return Stream.of(attachment.storageKey(), attachment.getPreviewStorageKey())
                .filter(k -> k != null && !k.isEmpty())
                .toList();
    }
}