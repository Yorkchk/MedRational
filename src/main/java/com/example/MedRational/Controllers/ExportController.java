package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Implementations.ExportDownloadServiceImpl;
import com.example.MedRational.Services.Interfaces.DownloadTrackingService;
import com.example.MedRational.Services.Interfaces.ExportDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/downloads")
@RequiredArgsConstructor
public class ExportController {

    private final ExportDownloadService exportDownloadService;
    private final DownloadTrackingService downloadTrackingService;
    private final CurrentUserGuard currentUserGuard;

    // Stream single file download (PDF/Image). Signed-in users get it added to their recent downloads.
    @GetMapping("/file/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        DownloadableFile file = exportDownloadService.downloadSingleFile(fileId);
        recordDownload(fileId);
        return buildFileResponse(file);
    }

    // Download multiple selected files as a single ZIP
    @PostMapping("/files/zip")
    public ResponseEntity<Resource> downloadSelectedFilesAsZip(@RequestBody List<Long> fileIds) throws IOException {
        DownloadableFile file = exportDownloadService.downloadSelectedFilesAsZip(fileIds);
        return buildFileResponse(file);
    }

    // Download an entire reasoning as a ZIP (summary markdown + attached media)
    @GetMapping("/reasoning/{reasoningId}/zip")
    public ResponseEntity<Resource> downloadReasoningAsZip(@PathVariable Long reasoningId) throws IOException {
        DownloadableFile file = exportDownloadService.downloadReasoningAsZip(reasoningId);
        return buildFileResponse(file);
    }

    // Download a category as a ZIP (organized by reasoning subfolders)
    @GetMapping("/category/{categoryId}/zip")
    public ResponseEntity<Resource> downloadCategoryAsZip(@PathVariable Long categoryId) throws IOException {
        DownloadableFile file = exportDownloadService.downloadCategoryAsZip(categoryId);
        return buildFileResponse(file);
    }

    // Full export: download all categories, reasonings, and assets
    @GetMapping("/all/zip")
    public ResponseEntity<Resource> downloadAllCategoriesAsZip() throws IOException {
        DownloadableFile file = exportDownloadService.downloadAllCategoriesAsZip();
        return buildFileResponse(file);
    }

    // Tracking is best effort: a failure here must never stop the user from getting the file
    private void recordDownload(Long fileId) {
        try {
            currentUserGuard.currentUserId()
                    .ifPresent(userId -> downloadTrackingService.recordUserDownload(userId, fileId));
        } catch (RuntimeException e) {
            log.warn("Couldn't record download of file {}", fileId, e);
        }
    }

    private ResponseEntity<Resource> buildFileResponse(DownloadableFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .body(file.getResource());
    }
}