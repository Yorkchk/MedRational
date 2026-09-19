package com.example.MedRational.DTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSuggestionRequestDTO {

    @NotBlank(message = "Message cannot be empty")
    private String message;

    private String attachmentUrl;
}