package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetricItemDTO {
    private Long fileId;
    private String fileName;
    // Null when the file has no reasoning or category
    private Long reasoningId;
    private String categoryName;
    private String reasoningTitle;
    private long metricValue;      // Holds count (downloads, favorites)
    private Double avgRating;
    private Integer totalRatings;
}