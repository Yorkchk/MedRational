package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.CreateWorkshopProjectDTO;
import com.example.MedRational.DTOs.WorkshopAttachmentResponseDTO;
import com.example.MedRational.DTOs.WorkshopProjectResponseDTO;
import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Services.Interfaces.WorkshopProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/workshops")
@RequiredArgsConstructor
public class WorkshopProjectController {

    private final WorkshopProjectService workshopService;

    // Public / Student: List projects (formLink hidden when status != OPEN)
    @GetMapping
    public ResponseEntity<Page<WorkshopProjectResponseDTO>> listProjects(
            @RequestParam(required = false) ProjectStatus status,
            @PageableDefault(size = 10, sort = "registrationOpensAt", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (status != null) {
            return ResponseEntity.ok(workshopService.getProjectsByStatus(status, pageable, isAdmin));
        }
        return ResponseEntity.ok(workshopService.getAllProjects(pageable, isAdmin));
    }

    // Public / Student: View single workshop details
    @GetMapping("/{id}")
    public ResponseEntity<WorkshopProjectResponseDTO> getProject(
            @PathVariable Long id,
            Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(workshopService.getProjectById(id, isAdmin));
    }

    // Admin: Create workshop
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<WorkshopProjectResponseDTO> createProject(
            @RequestParam Long authorId,
            @Valid @RequestBody CreateWorkshopProjectDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workshopService.createProject(request, authorId));
    }

    // Admin: Upload attachment to workshop (stored in R2 under WorkshopAttachments/)
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<WorkshopAttachmentResponseDTO> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workshopService.attachFileToProject(id, file));
    }

    // Admin: Delete attachment
    @DeleteMapping("/attachments/{attachmentId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) {
        workshopService.removeAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }

    // Admin: Manually force state change
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<WorkshopProjectResponseDTO> changeStatus(
            @PathVariable Long id,
            @RequestParam ProjectStatus status) {
        return ResponseEntity.ok(workshopService.updateProjectStatus(id, status));
    }

    // Admin: Delete workshop
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        workshopService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}