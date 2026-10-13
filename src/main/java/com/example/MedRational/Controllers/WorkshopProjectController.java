package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.CreateWorkshopProjectDTO;
import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.DTOs.WorkshopAttachmentResponseDTO;
import com.example.MedRational.DTOs.WorkshopProjectResponseDTO;
import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Services.Interfaces.FilePreviewService;
import com.example.MedRational.Services.Interfaces.WorkshopProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/workshops")
@RequiredArgsConstructor
public class WorkshopProjectController {

    private final WorkshopProjectService workshopService;
    private final FilePreviewService filePreviewService;

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
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<WorkshopProjectResponseDTO> createProject(
            @RequestParam Long authorId,
            @Valid @RequestBody CreateWorkshopProjectDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workshopService.createProject(request, authorId));
    }

    // Admin: Edit a workshop's details and dates. Changing the dates recomputes the status
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<WorkshopProjectResponseDTO> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody CreateWorkshopProjectDTO request) {
        return ResponseEntity.ok(workshopService.updateProject(id, request));
    }

    // Admin: Upload attachment to workshop (stored in R2 under WorkshopAttachments/)
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<WorkshopAttachmentResponseDTO> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workshopService.attachFileToProject(id, file));
    }

    // Signed-in users: the attachment as uploaded. The R2 bucket is private, so it's streamed from here
    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId) {
        DownloadableFile file = workshopService.openAttachment(attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.getResource());
    }

    // Signed-in users: inline rendition (PDF and images as-is; DOCX/PPTX/XLSX converted to PDF and cached).
    // 415 when the format can't be previewed
    @GetMapping("/attachments/{attachmentId}/preview")
    public ResponseEntity<Resource> previewAttachment(@PathVariable Long attachmentId) {
        DownloadableFile preview = filePreviewService.getAttachmentPreview(attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(preview.getFileName(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(preview.getResource());
    }

    // Admin: Delete attachment
    @DeleteMapping("/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) {
        workshopService.removeAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }

    // Admin: Manually force state change
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<WorkshopProjectResponseDTO> changeStatus(
            @PathVariable Long id,
            @RequestParam ProjectStatus status) {
        return ResponseEntity.ok(workshopService.updateProjectStatus(id, status));
    }

    // Admin: Delete workshop
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        workshopService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}