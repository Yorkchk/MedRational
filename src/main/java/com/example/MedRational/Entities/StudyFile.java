package com.example.MedRational.Entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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

    @Column(name = "file_type", length = 255)
    private String fileType; // e.g., "application/pdf", "image/png"

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey; // Object key in Cloudflare R2

    @Column(name = "public_url", nullable = false, length = 1000)
    private String publicUrl;

    // Cached PDF rendition in R2 for Office files (null until first preview)
    @Column(name = "preview_storage_key", length = 500)
    private String previewStorageKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    // Precomputed rating metrics for high-speed reads
    @Column(name = "avg_rating", nullable = false)
    @Builder.Default
    private Double avgRating = 0.0;

    @Column(name = "total_ratings", nullable = false)
    @Builder.Default
    private Integer totalRatings = 0;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "file_hashtags",
            joinColumns = @JoinColumn(name = "file_id"),
            inverseJoinColumns = @JoinColumn(name = "hashtag_id")
    )
    @Builder.Default
    private Set<Hashtag> hashtags = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reasoning_id", nullable = false)
    @JsonBackReference
    private Reasoning reasoning;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}