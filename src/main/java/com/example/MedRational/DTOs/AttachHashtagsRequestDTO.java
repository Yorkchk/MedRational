package com.example.MedRational.DTOs;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachHashtagsRequestDTO {
    @NotEmpty(message = "Hashtags list cannot be empty")
    private Set<String> tagNames;
}