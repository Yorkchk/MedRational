package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.FavoriteResponseDTO;
import com.example.MedRational.Services.Interfaces.FavoriteService;
import com.example.MedRational.Security.CurrentUserGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentUserGuard currentUserGuard;

    // Toggle favorite state (convenient single button on UI)
    @PostMapping("/users/{userId}/favorites/{fileId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleFavorite(
            @PathVariable Long userId,
            @PathVariable Long fileId) {
        currentUserGuard.requireSelf(userId);
        boolean isFavorited = favoriteService.toggleFavorite(userId, fileId);
        return ResponseEntity.ok(Map.of(
                "fileId", fileId,
                "isFavorited", isFavorited,
                "message", isFavorited ? "File added to favorites" : "File removed from favorites"
        ));
    }

    // Add explicit favorite
    @PostMapping("/users/{userId}/favorites/{fileId}")
    public ResponseEntity<FavoriteResponseDTO> addFavorite(
            @PathVariable Long userId,
            @PathVariable Long fileId) {
        currentUserGuard.requireSelf(userId);
        return ResponseEntity.ok(favoriteService.addFavorite(userId, fileId));
    }

    // Remove explicit favorite
    @DeleteMapping("/users/{userId}/favorites/{fileId}")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable Long userId,
            @PathVariable Long fileId) {
        currentUserGuard.requireSelf(userId);
        favoriteService.removeFavorite(userId, fileId);
        return ResponseEntity.noContent().build();
    }

    // Fetch user's favorite files (paginated)
    @GetMapping("/users/{userId}/favorites")
    public ResponseEntity<Page<FavoriteResponseDTO>> getUserFavorites(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        currentUserGuard.requireSelf(userId);
        return ResponseEntity.ok(favoriteService.getFavoritesByUserId(userId, pageable));
    }

    // Check if a file is favorited by a user
    @GetMapping("/users/{userId}/favorites/{fileId}/status")
    public ResponseEntity<Map<String, Boolean>> isFileFavorited(
            @PathVariable Long userId,
            @PathVariable Long fileId) {
        currentUserGuard.requireSelf(userId);
        return ResponseEntity.ok(Map.of("isFavorited", favoriteService.isFileFavoritedByUser(userId, fileId)));
    }

    // Get total favorite count for a file
    @GetMapping("/files/{fileId}/favorites/count")
    public ResponseEntity<Map<String, Object>> getFileFavoriteCount(@PathVariable Long fileId) {
        long count = favoriteService.getFavoriteCountForFile(fileId);
        return ResponseEntity.ok(Map.of(
                "fileId", fileId,
                "favoriteCount", count
        ));
    }
}