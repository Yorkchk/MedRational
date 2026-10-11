package com.example.MedRational.DTOs;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class StudyFileResponse {
    private Long id;
    private Long reasoningId;
    private String fileName;
    private String fileType;
    private String publicUrl;
    private boolean previewable;
    private Long fileSizeBytes;
    private LocalDateTime uploadedAt;
}