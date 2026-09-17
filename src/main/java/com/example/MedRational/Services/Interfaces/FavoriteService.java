package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.FavoriteResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FavoriteService {

    // Adds file to user's favorites (idempotent)
    FavoriteResponseDTO addFavorite(Long userId, Long fileId);

    // Removes file from user's favorites
    void removeFavorite(Long userId, Long fileId);

    // Toggles favorite state (adds if absent, removes if present)
    boolean toggleFavorite(Long userId, Long fileId);

    // Returns paginated files favorited by a user
    Page<FavoriteResponseDTO> getFavoritesByUserId(Long userId, Pageable pageable);

    // Total count of users who favorited this file
    long getFavoriteCountForFile(Long fileId);

    // Check whether a specific user already favorited this file
    boolean isFileFavoritedByUser(Long userId, Long fileId);
}