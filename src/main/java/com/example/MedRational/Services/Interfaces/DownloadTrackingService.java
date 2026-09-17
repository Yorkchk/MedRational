package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.RecentDownloadGlimpseDTO;
import com.example.MedRational.DTOs.StudyFileResponseDTO;

import java.util.List;

public interface DownloadTrackingService {

    // Record an event when a user downloads a file (or update timestamp if re-downloaded)
    void recordUserDownload(Long userId, Long fileId);

    // Get the latest 10 downloads preview for the user
    List<RecentDownloadGlimpseDTO> getRecentDownloadsGlimpse(Long userId);

    // Fetch full file details with breadcrumbs when the user clicks the glimpse
    StudyFileResponseDTO getFileDetailsForNavigation(Long fileId);
}