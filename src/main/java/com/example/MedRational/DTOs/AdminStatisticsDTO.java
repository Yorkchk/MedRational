package com.example.MedRational.DTOs;

import lombok.*;

import java.time.LocalDateTime;

// Everything the admin statistics screen shows, in one response
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStatisticsDTO {
    private LocalDateTime generatedAt;
    private UserStatsDTO users;
    private UsageStatsDTO usage;
    private ContentStatsDTO lastWeek;
    private ContentStatsDTO allTime;
    private TrendStatsDTO trends;
    private EngagementStatsDTO engagement;
}
