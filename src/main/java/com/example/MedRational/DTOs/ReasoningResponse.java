package com.example.MedRational.DTOs;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReasoningResponse {
    private Long id;
    private Long categoryId;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private List<StudyFileResponse> files;
}