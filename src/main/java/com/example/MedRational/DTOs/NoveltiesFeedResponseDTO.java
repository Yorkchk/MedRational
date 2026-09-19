package com.example.MedRational.DTOs;

import lombok.*;
import org.springframework.data.domain.Page;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoveltiesFeedResponseDTO {
    private long newFilesCount; // Badge counter (e.g. "+3 new")
    private Page<NoveltyFileItemDTO> feed;
}