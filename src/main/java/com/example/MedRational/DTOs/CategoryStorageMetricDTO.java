package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryStorageMetricDTO {
    private String categoryName;
    private long totalSizeBytes;
    private double totalSizeMegaBytes;
    private long totalFiles;
}