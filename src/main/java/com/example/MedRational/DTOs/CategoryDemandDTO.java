package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDemandDTO {
    private String categoryName;
    private long totalDownloads;
    private double percentageOfTotal;
}