package com.example.MedRational.DTOs;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentDownloadGlimpseDTO {
    private Long fileId;
    private String fileName;
    private String fileType;
    private Long fileSizeBytes;
    private LocalDateTime downloadedAt;

    // Direct routing metadata for UI navigation
    private Long categoryId;
    private String categoryName;
    private Long reasoningId;
    private String reasoningTitle;
}