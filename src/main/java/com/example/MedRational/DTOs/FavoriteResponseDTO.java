package com.example.MedRational.DTOs;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteResponseDTO {
    private Long favoriteId;
    private Long userId;
    private StudyFileResponseDTO file;
    private LocalDateTime createdAt;
}