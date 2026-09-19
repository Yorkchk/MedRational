package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendingHashtagMetricDTO {
    private String hashtag;
    private long attachedFilesCount;
}