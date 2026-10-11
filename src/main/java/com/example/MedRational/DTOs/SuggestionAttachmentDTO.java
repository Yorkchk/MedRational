package com.example.MedRational.DTOs;

import lombok.*;

// Metadata only: the file itself is streamed from /suggestions/{id}/attachment or
// /suggestions/{id}/messages/{messageId}/attachment
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionAttachmentDTO {
    private String fileName;
    private String contentType;
    private Long sizeBytes;
}
