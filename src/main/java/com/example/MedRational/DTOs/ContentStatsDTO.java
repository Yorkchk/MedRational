package com.example.MedRational.DTOs;

import lombok.*;

import java.util.List;

// What students use over one range (last 7 days or all time)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentStatsDTO {
    private List<FileMetricItemDTO> topDownloaded;
    private List<FileMetricItemDTO> topViewed;
    private List<FileMetricItemDTO> mostFavorited;
    private List<CategoryActivityDTO> categories;
    private List<HashtagActivityDTO> topHashtags;
}
