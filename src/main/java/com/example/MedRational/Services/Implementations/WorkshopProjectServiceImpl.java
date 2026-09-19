package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.CreateWorkshopProjectDTO;
import com.example.MedRational.DTOs.WorkshopAttachmentResponseDTO;
import com.example.MedRational.DTOs.WorkshopProjectResponseDTO;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.WorkshopAttachment;
import com.example.MedRational.Entities.WorkshopProject;
import com.example.MedRational.Entities.enums.ProjectStatus;
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

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new EntityNotFoundException("Author user not found with id: " + authorId));

        LocalDateTime now = LocalDateTime.now();
        ProjectStatus initialStatus;
        if (now.isBefore(request.getRegistrationOpensAt())) {
            initialStatus = ProjectStatus.UPCOMING;
        } else if (now.isBefore(request.getRegistrationClosesAt())) {
            initialStatus = ProjectStatus.OPEN;
        } else {
            initialStatus = ProjectStatus.CLOSED;
        }

        WorkshopProject project = WorkshopProject.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .projectType(request.getProjectType())
                .status(initialStatus)
                .formLink(request.getFormLink())
                .registrationOpensAt(request.getRegistrationOpensAt())
                .registrationClosesAt(request.getRegistrationClosesAt())
                .author(author)
                .attachments(new ArrayList<>())
                .build();

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

        String key = extractStorageKey(attachment.getFileUrl());
        if (key != null) {
            r2StorageService.deleteFile(key);
        }

        attachmentRepository.delete(attachment);
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

        project.setStatus(targetStatus);
        return mapToDTO(workshopProjectRepository.save(project), true);
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId) {
        WorkshopProject project = workshopProjectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Workshop project not found with id: " + projectId));

        List<String> keysToDelete = project.getAttachments().stream()
                .map(a -> extractStorageKey(a.getFileUrl()))
                .filter(k -> k != null && !k.isEmpty())
                .toList();

        if (!keysToDelete.isEmpty()) {
            r2StorageService.deleteFiles(keysToDelete);
        }

        workshopProjectRepository.delete(project);
    }

    @Scheduled(fixedRate = 60000) // Runs every minute
    @Transactional
    @Override
    public void processAutomaticStateTransitions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tenMinutesFromNow = now.plusMinutes(10);

        // 1. Check: 10 minutes BEFORE launch
        List<WorkshopProject> openingSoon = workshopProjectRepository.findProjectsDueForOpeningReminder(tenMinutesFromNow);
        for (WorkshopProject project : openingSoon) {
            project.setOpeningReminderSent(true);
            workshopNotificationService.notifyOpeningSoon(project);
            log.info("Dispatched 10-min opening reminder for project ID {}", project.getId());
        }

        // 2. Check: Launch moment (UPCOMING -> OPEN)
        List<WorkshopProject> toOpen = workshopProjectRepository.findProjectsReadyToOpen(now);
        for (WorkshopProject project : toOpen) {
            project.setStatus(ProjectStatus.OPEN);
            project.setLaunchNotificationSent(true);
            workshopNotificationService.notifyRegistrationOpened(project);
            log.info("Project ID {} transitioned to OPEN and launch notifications sent", project.getId());
        }

        // 3. Check: 10 minutes BEFORE closing
        List<WorkshopProject> closingSoon = workshopProjectRepository.findProjectsDueForClosingReminder(tenMinutesFromNow);
        for (WorkshopProject project : closingSoon) {
            project.setClosingReminderSent(true);
            workshopNotificationService.notifyClosingSoon(project);
            log.info("Dispatched 10-min closing reminder for project ID {}", project.getId());
        }

        // 4. Check: Closing moment (OPEN -> CLOSED)
        List<WorkshopProject> toClose = workshopProjectRepository.findProjectsReadyToClose(now);
        for (WorkshopProject project : toClose) {
            project.setStatus(ProjectStatus.CLOSED);
            project.setClosedNotificationSent(true);
            workshopNotificationService.notifyRegistrationClosed(project);
            log.info("Project ID {} transitioned to CLOSED and closed notifications sent", project.getId());
        }}

    // Inside com.example.MedRational.Services.Implementations.WorkshopProjectServiceImpl

    private WorkshopProjectResponseDTO mapToDTO(WorkshopProject project, boolean isAdmin) {
        List<WorkshopAttachmentResponseDTO> attachments = project.getAttachments() != null
                ? project.getAttachments().stream().map(this::mapAttachmentToDTO).collect(Collectors.toList())
                : List.of();

        // The form link is strictly protected until registration/application is actively OPEN
        String visibleFormLink = (isAdmin || project.getStatus() == ProjectStatus.OPEN)
                ? project.getFormLink()
                : null;

        return WorkshopProjectResponseDTO.builder()
                .id(project.getId())
                .authorId(project.getAuthor().getId())
                .authorEmail(project.getAuthor().getEmail())
                .title(project.getTitle())
                .description(project.getDescription())
                .projectType(project.getProjectType())
                .status(project.getStatus())
                .formLink(visibleFormLink)
                .registrationOpensAt(project.getRegistrationOpensAt())
                .registrationClosesAt(project.getRegistrationClosesAt())
                .createdAt(project.getCreatedAt())
                .attachments(attachments)
                .build();
    }

    private WorkshopAttachmentResponseDTO mapAttachmentToDTO(WorkshopAttachment attachment) {
        return WorkshopAttachmentResponseDTO.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .build();
    }

    private String extractStorageKey(String fileUrl) {
        if (fileUrl == null) return null;
        int index = fileUrl.indexOf("WorkshopAttachments/");
        return index != -1 ? fileUrl.substring(index) : null;
    }
}