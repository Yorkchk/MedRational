package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Services.Implementations.ExportDownloadServiceImpl;
import com.example.MedRational.Services.Interfaces.ExportDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/downloads")
@RequiredArgsConstructor
public class ExportController {

    private final ExportDownloadService exportDownloadService;

    // Stream single file download (PDF/Image)
    @GetMapping("/file/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        DownloadableFile file = exportDownloadService.downloadSingleFile(fileId);
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

    private ResponseEntity<Resource> buildFileResponse(DownloadableFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .body(file.getResource());
    }
}