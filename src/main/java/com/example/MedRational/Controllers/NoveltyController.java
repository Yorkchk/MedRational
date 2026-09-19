package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.NoveltiesFeedResponseDTO;
import com.example.MedRational.Services.Interfaces.NoveltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/novelties")
@RequiredArgsConstructor
public class NoveltyController {

    private final NoveltyService noveltyService;

    // Get the novelties feed with preview cards and `isNew` flags
    @GetMapping
    public ResponseEntity<NoveltiesFeedResponseDTO> getNovelties(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 15, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(noveltyService.getNoveltiesFeed(userId, pageable));
    }

    // Badge count endpoint for app header/sidebar
    @GetMapping("/badge-count")
    public ResponseEntity<Map<String, Long>> getBadgeCount(@RequestParam Long userId) {
        long count = noveltyService.getUnreadUploadsCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    // Mark as seen when user opens the novelties tab
    @PostMapping("/mark-seen")
    public ResponseEntity<Void> markAsSeen(@RequestParam Long userId) {
        noveltyService.markNoveltiesAsSeen(userId);
        return ResponseEntity.ok().build();
    }
}