package com.example.MedRational.DTOs;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageStatsDTO {
    private long totalDownloads;
    private long downloadsThisWeek;
    private long downloadsPreviousWeek;
    private long totalViews;
    private long viewsThisWeek;
    private long viewsPreviousWeek;
    private List<StatPointDTO> dailyDownloads;
    private List<StatPointDTO> dailyViews;
}
