package com.example.MedRational.DTOs;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngagementStatsDTO {
    private long totalFiles;
    private long newFilesThisWeek;
    private long filesWithoutDownloads;
    private long totalFavorites;
    private long favoritesThisWeek;
    private long totalRatings;
    private long ratingsThisWeek;
    // Null when nobody rated anything yet
    private Double averageRating;
    private List<FileMetricItemDTO> highestRated;
    private long openSuggestions;
    private long unreadSuggestions;
    private long upcomingWorkshops;
    private long openWorkshops;
    private long closedWorkshops;
}
