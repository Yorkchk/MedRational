package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileSearchFilterDTO {
    private String fileName;       // e.g. "ecg_guide.pdf"
    private String categoryName;   // e.g. "Cardiology"
    private String reasoningTitle; // e.g. "Chest Pain Differential"
    private String hashtag;        // e.g. "emergency" or "#emergency"
}