package com.example.MedRational.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSearchResultDTO {
    private Long id;
    private Long reasoningId;
    private String reasoningTitle;
    private Long categoryId;
    private String categoryName;
    private String fileName;
    private String fileType;
    private String publicUrl;
    private boolean previewable;
    private Long fileSizeBytes;
    private List<String> hashtags;
}