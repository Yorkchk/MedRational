package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.NoveltiesFeedResponseDTO;
import org.springframework.data.domain.Pageable;

public interface NoveltyService {

    // Returns feed with isNew flags computed against the user's last login
    NoveltiesFeedResponseDTO getNoveltiesFeed(Long userId, Pageable pageable);

    // Get count of new uploads since user's last login (for header badge)
    long getUnreadUploadsCount(Long userId);

    // Acknowledges user has viewed the feed and resets the baseline
    void markNoveltiesAsSeen(Long userId);
}