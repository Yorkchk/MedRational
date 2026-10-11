package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.NoveltiesFeedResponseDTO;
import com.example.MedRational.Entities.User;
import org.springframework.data.domain.Pageable;

public interface NoveltyService {

    // Returns feed with isNew flags computed against the user's novelties baseline
    NoveltiesFeedResponseDTO getNoveltiesFeed(User user, Pageable pageable);

    // Count of uploads since the user last opened the feed (for the header badge)
    long getUnreadUploadsCount(User user);

    // Acknowledges user has viewed the feed and resets the baseline
    void markNoveltiesAsSeen(User user);
}
