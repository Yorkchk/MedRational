package com.example.MedRational.DTOs;

import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Entities.enums.ProjectType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopProjectResponseDTO {
    private Long id;
    private Long authorId;
    private String authorEmail;
    private String title;
    private String description;
    private ProjectType projectType;
    private ProjectStatus status;
    private String formLink; // Populated only when status == OPEN (or when requested by admin)
    private LocalDateTime registrationOpensAt;
    private LocalDateTime registrationClosesAt;
    private LocalDateTime createdAt;
    private List<WorkshopAttachmentResponseDTO> attachments;
}