package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileRatingSummaryDTO {
    private Long fileId;
    private Double avgRating;
    private Integer totalRatings;
    private Integer userRating; // Current user's individual score (null if not rated yet)
}