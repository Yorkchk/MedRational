package com.example.MedRational.DTOs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoveltyFileItemDTO {
    private Long fileId;
    private String fileName;
    private String fileType;
    private Long fileSizeBytes;
    private LocalDateTime uploadedAt;

    // Direct breadcrumbs for UI routing
    private Long categoryId;
    private String categoryName;
    private Long reasoningId;
    private String reasoningTitle;

    private Set<String> hashtags;

    // Visual differentiator flag (bolding / badge in the UI). Lombok's getter is isNew(), which
    // Jackson would serialize as "new"
    @Getter(onMethod_ = @JsonProperty("isNew"))
    private boolean isNew;
}