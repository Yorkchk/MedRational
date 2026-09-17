package com.example.MedRational.DTOs;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyFileResponseDTO {
    private Long id;
    private String fileName;
    private String fileType;
    private String publicUrl;
    private Long fileSizeBytes;
    private Double avgRating;
    private Integer totalRatings;
    private Long reasoningId;
    private String reasoningTitle;
    private Long categoryId;
    private String categoryName;
    private Set<String> hashtags;
    private LocalDateTime uploadedAt;
}