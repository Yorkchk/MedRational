package com.example.MedRational.DTOs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateFileRequestDTO {

    @NotNull(message = "Score is required")
    @Min(value = 1, message = "Rating score must be at least 1")
    @Max(value = 5, message = "Rating score cannot exceed 5")
    private Integer score;
}