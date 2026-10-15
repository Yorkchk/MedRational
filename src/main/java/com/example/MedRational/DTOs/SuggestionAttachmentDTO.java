package com.example.MedRational.DTOs;

import lombok.*;

// Metadata only: the file itself is streamed from /suggestions/{id}/attachment or
// /suggestions/{id}/messages/{messageId}/attachment, and its preview from /suggestions/{id}/attachment/preview
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionAttachmentDTO {
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    // PDF, DOCX, PPTX, XLSX or an image
    private boolean previewable;
}
