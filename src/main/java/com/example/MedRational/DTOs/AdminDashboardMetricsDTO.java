package com.example.MedRational.DTOs;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardMetricsDTO {
    private long totalUsers;
    private long totalFiles;
    private long totalDownloadsAllTime;
    private long downloadsLast7Days;
    private long newFilesLast7Days;
    private List<FileMetricItemDTO> topDownloadedFiles;
    private List<FileMetricItemDTO> mostFavoritedFiles;
    private List<FileMetricItemDTO> highestRatedFiles;
}