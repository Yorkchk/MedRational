package com.example.MedRational.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// The title is the file's name without its extension; the backend keeps the extension
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudyFileRequestDTO {
    @NotBlank(message = "File title is required")
    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;
}
