package com.example.MedRational.DTOs;

import com.example.MedRational.Entities.enums.SuggestionStatus;
import lombok.*;

import java.time.LocalDateTime;

// One row of the admin inbox or of a user's "My suggestions" list
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionSummaryDTO {
    private Long id;
    private String title;
    private SuggestionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private int messageCount;
    // The other side posted since the caller last opened it
    private boolean unread;
    private String senderName;
    private String senderEmail;
}
