package com.example.MedRational.DTOs;

import lombok.*;

// A file or category whose downloads and views rose this week. reasoningId and categoryName are
// only set for files
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendingItemDTO {
    private Long id;
    private String name;
    private Long reasoningId;
    private String categoryName;
    private long thisWeek;
    private long previousWeek;
}
