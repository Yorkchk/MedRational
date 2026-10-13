package com.example.MedRational.DTOs;

import lombok.*;

import java.time.LocalDate;

// One point of a statistics chart: the first day of the period and its value
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatPointDTO {
    private LocalDate periodStart;
    private long value;
}
