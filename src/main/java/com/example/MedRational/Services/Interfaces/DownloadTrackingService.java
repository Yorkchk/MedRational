package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.RecentDownloadGlimpseDTO;
import com.example.MedRational.DTOs.StudyFileResponseDTO;

import java.util.List;

public interface DownloadTrackingService {

    // Record an event when a user downloads a file (or update timestamp if re-downloaded)
    void recordUserDownload(Long userId, Long fileId);

    // Most recently downloaded files, newest first, each file once.
    // A null limit uses the configured default; other values are clamped to [1, max-limit].
    List<RecentDownloadGlimpseDTO> getRecentDownloadsGlimpse(Long userId, Integer limit);

    // Fetch full file details with breadcrumbs when the user clicks the glimpse
    StudyFileResponseDTO getFileDetailsForNavigation(Long fileId);
}