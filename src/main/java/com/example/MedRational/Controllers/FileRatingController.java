package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.FileRatingSummaryDTO;
import com.example.MedRational.DTOs.RateFileRequestDTO;
import com.example.MedRational.Services.Interfaces.FileRatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/files/{fileId}/ratings")
@RequiredArgsConstructor
public class FileRatingController {

    private final FileRatingService fileRatingService;

    // Submit or update rating for a file
    @PostMapping
    public ResponseEntity<FileRatingSummaryDTO> rateFile(
            @PathVariable Long fileId,
            @RequestParam Long userId,
            @Valid @RequestBody RateFileRequestDTO request) {
        return ResponseEntity.ok(fileRatingService.rateFile(userId, fileId, request));
    }

    // Get current rating stats (avg rating out of 5, total ratings count, and user's score)
    @GetMapping
    public ResponseEntity<FileRatingSummaryDTO> getRatingSummary(
            @PathVariable Long fileId,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(fileRatingService.getFileRatingSummary(fileId, userId));
    }

    // Delete a user's rating
    @DeleteMapping
    public ResponseEntity<FileRatingSummaryDTO> removeRating(
            @PathVariable Long fileId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(fileRatingService.removeRating(userId, fileId));
    }
}