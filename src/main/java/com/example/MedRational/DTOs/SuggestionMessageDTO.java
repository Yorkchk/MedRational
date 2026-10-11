package com.example.MedRational.DTOs;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionMessageDTO {
    private Long id;
    private boolean fromAdmin;
    private String authorName;
    private String body;
    private SuggestionAttachmentDTO attachment;
    private LocalDateTime createdAt;
}
