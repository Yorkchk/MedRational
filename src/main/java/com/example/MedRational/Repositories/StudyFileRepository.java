package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.StudyFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudyFileRepository extends JpaRepository<StudyFile, Long> {

    // Find all files attached to a specific reasoning
    List<StudyFile> findByReasoningId(Long reasoningId);

    // Find a file by its unique Cloudflare R2 storage key (useful for deletion)
    Optional<StudyFile> findByStorageKey(String storageKey);

    // Find files by MIME type (e.g., "application/pdf", "image/png")
    List<StudyFile> findByFileType(String fileType);
}