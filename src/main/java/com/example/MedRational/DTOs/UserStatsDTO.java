package com.example.MedRational.DTOs;

import lombok.*;

import java.util.List;

// Students only (ROLE_USER); "week" is the last 7 days, today included
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsDTO {
    private long totalStudents;
    private long newStudentsThisWeek;
    private long newStudentsPreviousWeek;
    private long weeklyActiveStudents;
    private long weeklyActiveStudentsPreviousWeek;
    private long dailyActiveStudents;
    // Students who downloaded or opened at least one file
    private long activatedStudents;
    private List<StatPointDTO> weeklySignUps;
    private List<StatPointDTO> weeklyActiveSeries;
}
