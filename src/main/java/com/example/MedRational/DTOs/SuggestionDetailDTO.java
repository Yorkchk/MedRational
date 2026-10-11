package com.example.MedRational.DTOs;

import com.example.MedRational.Entities.enums.SuggestionStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionDetailDTO {
    private Long id;
    private String title;
    private String description;
    private SuggestionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime closedAt;
    private SuggestionAttachmentDTO attachment;
    private SuggestionSenderDTO sender;
    // Oldest first
    private List<SuggestionMessageDTO> messages;
}
