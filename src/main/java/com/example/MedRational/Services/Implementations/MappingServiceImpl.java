package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.CategoryResponse;
import com.example.MedRational.DTOs.ReasoningResponse;
import com.example.MedRational.DTOs.StudyFileResponse;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Collectors;

@Component
public class MappingServiceImpl {

    public CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .createdAt(category.getCreatedAt())
                .reasonings(category.getReasonings() != null
                        ? category.getReasonings().stream().map(this::toReasoningResponse).collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    public ReasoningResponse toReasoningResponse(Reasoning reasoning) {
        return ReasoningResponse.builder()
                .id(reasoning.getId())
                .categoryId(reasoning.getCategory() != null ? reasoning.getCategory().getId() : null)
                .title(reasoning.getTitle())
                .content(reasoning.getContent())
                .createdAt(reasoning.getCreatedAt())
                .files(reasoning.getFiles() != null
                        ? reasoning.getFiles().stream().map(this::toStudyFileResponse).collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    public StudyFileResponse toStudyFileResponse(StudyFile file) {
        return StudyFileResponse.builder()
                .id(file.getId())
                .reasoningId(file.getReasoning() != null ? file.getReasoning().getId() : null)
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .publicUrl(file.getPublicUrl())
                .fileSizeBytes(file.getFileSizeBytes())
                .uploadedAt(file.getUploadedAt())
                .build();
    }
}