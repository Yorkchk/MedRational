package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.FileRatingSummaryDTO;
import com.example.MedRational.DTOs.RateFileRequestDTO;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Interfaces.FileRatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Ratings are per user; the rater always comes from the JWT, never from a request parameter.
@RestController
@RequestMapping("/api/v1/files/{fileId}/ratings")
@RequiredArgsConstructor
public class FileRatingController {

    private final FileRatingService fileRatingService;
    private final CurrentUserGuard currentUserGuard;

    // Submit or change the caller's rating (1-5)
    @PostMapping
    public ResponseEntity<FileRatingSummaryDTO> rateFile(
            @PathVariable Long fileId,
            @Valid @RequestBody RateFileRequestDTO request) {
        Long userId = currentUserGuard.requireCurrentUser().getId();
        return ResponseEntity.ok(fileRatingService.rateFile(userId, fileId, request));
    }

    // Average rating out of 5 and total count; userRating is the caller's score (null when anonymous or unrated)
    @GetMapping
    public ResponseEntity<FileRatingSummaryDTO> getRatingSummary(@PathVariable Long fileId) {
        Long userId = currentUserGuard.currentUserId().orElse(null);
        return ResponseEntity.ok(fileRatingService.getFileRatingSummary(fileId, userId));
    }

    // Remove the caller's rating; a no-op when they hadn't rated the file
    @DeleteMapping
    public ResponseEntity<FileRatingSummaryDTO> removeRating(@PathVariable Long fileId) {
        Long userId = currentUserGuard.requireCurrentUser().getId();
        return ResponseEntity.ok(fileRatingService.removeRating(userId, fileId));
    }
}
