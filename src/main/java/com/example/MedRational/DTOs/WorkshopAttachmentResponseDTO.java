package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopAttachmentResponseDTO {
    private Long id;
    private String fileName;
    // Not reachable from outside: download through /workshops/attachments/{id}/download instead
    private String fileUrl;
    // True when /workshops/attachments/{id}/preview can show it (PDF, DOCX, PPTX, XLSX or image)
    private boolean previewable;
}