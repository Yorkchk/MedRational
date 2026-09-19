package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.CreateWorkshopProjectDTO;
import com.example.MedRational.DTOs.WorkshopAttachmentResponseDTO;
import com.example.MedRational.DTOs.WorkshopProjectResponseDTO;
import com.example.MedRational.Entities.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface WorkshopProjectService {

    WorkshopProjectResponseDTO createProject(CreateWorkshopProjectDTO request, Long authorId);

    WorkshopAttachmentResponseDTO attachFileToProject(Long projectId, MultipartFile file) throws IOException;

    List<WorkshopAttachmentResponseDTO> attachMultipleFiles(Long projectId, List<MultipartFile> files) throws IOException;

    void removeAttachment(Long attachmentId);

    WorkshopProjectResponseDTO getProjectById(Long projectId, boolean isAdmin);

    Page<WorkshopProjectResponseDTO> getProjectsByStatus(ProjectStatus status, Pageable pageable, boolean isAdmin);

    Page<WorkshopProjectResponseDTO> getAllProjects(Pageable pageable, boolean isAdmin);

    WorkshopProjectResponseDTO updateProjectStatus(Long projectId, ProjectStatus targetStatus);

    void deleteProject(Long projectId);

    void processAutomaticStateTransitions();
}