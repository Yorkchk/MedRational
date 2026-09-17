package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.StudyFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudyFileRepository extends JpaRepository<StudyFile, Long> {

    // Existing queries
    List<StudyFile> findByReasoningId(Long reasoningId);
    Optional<StudyFile> findByStorageKey(String storageKey);
    List<StudyFile> findByFileType(String fileType);

    // --- Additions for Novelties ---
    // Fetch the N most recently uploaded files for the Novelties section
    List<StudyFile> findTop10ByOrderByUploadedAtDesc();

    // Check count of uploads that arrived since user's last login
    long countByUploadedAtAfter(LocalDateTime lastLoginAt);

    // --- Addition for Hashtags ---
    @Query("SELECT f FROM StudyFile f JOIN f.hashtags h WHERE LOWER(h.name) = LOWER(:name)")
    Page<StudyFile> findByHashtagName(@Param("name") String name, Pageable pageable);
}