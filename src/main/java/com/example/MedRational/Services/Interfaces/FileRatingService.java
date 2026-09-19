package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.FileRatingSummaryDTO;
import com.example.MedRational.DTOs.RateFileRequestDTO;

public interface FileRatingService {

    // Submits or updates a rating (1-5) by a user and updates StudyFile cached statistics
    FileRatingSummaryDTO rateFile(Long userId, Long fileId, RateFileRequestDTO request);

    // Retrieves file rating summary and user's score if authenticated
    FileRatingSummaryDTO getFileRatingSummary(Long fileId, Long userId);

    // Removes a user's rating
    FileRatingSummaryDTO removeRating(Long userId, Long fileId);
}