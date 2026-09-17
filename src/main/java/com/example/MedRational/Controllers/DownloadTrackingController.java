package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.RecentDownloadGlimpseDTO;
import com.example.MedRational.DTOs.StudyFileResponseDTO;
import com.example.MedRational.Services.Interfaces.DownloadTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DownloadTrackingController {

    private final DownloadTrackingService downloadTrackingService;

    // Fetch the 10 most recent downloads for the user's glimpse feed
    @GetMapping("/users/{userId}/recent-downloads")
    public ResponseEntity<List<RecentDownloadGlimpseDTO>> getRecentDownloads(@PathVariable Long userId) {
        return ResponseEntity.ok(downloadTrackingService.getRecentDownloadsGlimpse(userId));
    }

    // Direct resolution endpoint when the user clicks on a glimpse item
    @GetMapping("/files/{fileId}/details")
    public ResponseEntity<StudyFileResponseDTO> getFileDetails(@PathVariable Long fileId) {
        return ResponseEntity.ok(downloadTrackingService.getFileDetailsForNavigation(fileId));
    }

    // Hook to record download (can be triggered by frontend or integrated into ExportDownloadService)
    @PostMapping("/users/{userId}/downloads/{fileId}")
    public ResponseEntity<Void> recordDownload(@PathVariable Long userId, @PathVariable Long fileId) {
        downloadTrackingService.recordUserDownload(userId, fileId);
        return ResponseEntity.ok().build();
    }
}