package com.example.MedRational.DTOs;

import com.example.MedRational.Entities.enums.SuggestionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSuggestionStatusDTO {

    @NotNull(message = "Status is required")
    private SuggestionStatus status;
}