package com.example.MedRational.DTOs;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendStatsDTO {
    private List<TrendingItemDTO> files;
    private List<TrendingItemDTO> categories;
}
