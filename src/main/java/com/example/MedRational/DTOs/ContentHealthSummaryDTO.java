package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentHealthSummaryDTO {
    private long totalFiles;
    private long unreviewedFilesCount;    // files with totalRatings == 0
    private long zeroDownloadFilesCount;   // cold files
    private double contentEngagementRate;  // % of files with >= 1 download
}