package com.example.MedRational.DTOs;

import com.example.MedRational.Entities.enums.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWorkshopProjectDTO {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Project type is required")
    private ProjectType projectType;

    private String formLink;

    // Optional; null means no limit
    @Positive(message = "Capacity must be positive")
    private Integer capacity;

    @NotNull(message = "Registration opening timestamp is required")
    private LocalDateTime registrationOpensAt;

    @NotNull(message = "Registration closing timestamp is required")
    private LocalDateTime registrationClosesAt;
}