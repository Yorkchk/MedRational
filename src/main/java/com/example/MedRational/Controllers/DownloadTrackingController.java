package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.RecentDownloadGlimpseDTO;
import com.example.MedRational.DTOs.StudyFileResponseDTO;
import com.example.MedRational.Services.Interfaces.DownloadTrackingService;
import com.example.MedRational.Security.CurrentUserGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DownloadTrackingController {

    private final DownloadTrackingService downloadTrackingService;
    private final CurrentUserGuard currentUserGuard;

    // The user's most recently downloaded files, newest first, each file once.
    // limit defaults to downloads.recent.default-limit (10) and is capped at downloads.recent.max-limit
    @GetMapping("/users/{userId}/recent-downloads")
    public ResponseEntity<List<RecentDownloadGlimpseDTO>> getRecentDownloads(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer limit) {
        currentUserGuard.requireSelf(userId);
        return ResponseEntity.ok(downloadTrackingService.getRecentDownloadsGlimpse(userId, limit));
    }

    // Direct resolution endpoint when the user clicks on a glimpse item
    @GetMapping("/files/{fileId}/details")
    public ResponseEntity<StudyFileResponseDTO> getFileDetails(@PathVariable Long fileId) {
        return ResponseEntity.ok(downloadTrackingService.getFileDetailsForNavigation(fileId));
    }

    // Hook to record download (can be triggered by frontend or integrated into ExportDownloadService)
    @PostMapping("/users/{userId}/downloads/{fileId}")
    public ResponseEntity<Void> recordDownload(@PathVariable Long userId, @PathVariable Long fileId) {
        currentUserGuard.requireSelf(userId);
        downloadTrackingService.recordUserDownload(userId, fileId);
        return ResponseEntity.ok().build();
    }
}