package com.example.MedRational.DTOs;

import com.example.MedRational.Entities.enums.SuggestionStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionResponseDTO {
    private Long id;
    private Long userId;
    private String userEmail;
    private String message;
    private String attachmentUrl;
    private SuggestionStatus status;
    private LocalDateTime createdAt;
}