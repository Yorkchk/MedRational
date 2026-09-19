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
    private String fileUrl;
}