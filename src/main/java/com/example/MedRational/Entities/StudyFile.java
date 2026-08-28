package com.example.MedRational.Entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", length = 50)
    private String fileType; // e.g., "application/pdf", "image/png"

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey; // Object key in Cloudflare R2

    @Column(name = "public_url", nullable = false, length = 1000)
    private String publicUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reasoning_id", nullable = false)
    @JsonBackReference
    private Reasoning reasoning;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}