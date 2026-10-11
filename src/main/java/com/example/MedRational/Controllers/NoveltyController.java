package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.NoveltiesFeedResponseDTO;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Interfaces.NoveltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Novelties are per user; the caller always comes from the JWT, never from a request parameter.
@RestController
@RequestMapping("/api/v1/novelties")
@RequiredArgsConstructor
public class NoveltyController {

    private final NoveltyService noveltyService;
    private final CurrentUserGuard currentUserGuard;

    // Get the novelties feed with preview cards and `isNew` flags
    @GetMapping
    public ResponseEntity<NoveltiesFeedResponseDTO> getNovelties(
            @PageableDefault(size = 15, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(noveltyService.getNoveltiesFeed(currentUserGuard.requireCurrentUser(), pageable));
    }

    // Badge count endpoint for app header/sidebar
    @GetMapping("/badge-count")
    public ResponseEntity<Map<String, Long>> getBadgeCount() {
        long count = noveltyService.getUnreadUploadsCount(currentUserGuard.requireCurrentUser());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    // Mark as seen when user opens the novelties tab
    @PostMapping("/mark-seen")
    public ResponseEntity<Void> markAsSeen() {
        noveltyService.markNoveltiesAsSeen(currentUserGuard.requireCurrentUser());
        return ResponseEntity.ok().build();
    }
}
