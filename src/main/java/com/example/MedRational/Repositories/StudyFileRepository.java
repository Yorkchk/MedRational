package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.StudyFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudyFileRepository extends JpaRepository<StudyFile, Long>, JpaSpecificationExecutor<StudyFile> {

    List<StudyFile> findByReasoningId(Long reasoningId);
    Optional<StudyFile> findByStorageKey(String storageKey);
    List<StudyFile> findByFileType(String fileType);

    List<StudyFile> findTop10ByOrderByUploadedAtDesc();
    long countByUploadedAtAfter(LocalDateTime lastLoginAt);

    @Query("SELECT f FROM StudyFile f JOIN f.hashtags h WHERE LOWER(h.name) = LOWER(:name)")
    Page<StudyFile> findByHashtagName(@Param("name") String name, Pageable pageable);

    // Under com.example.MedRational.Repositories

    @Query("""
    SELECT f, COUNT(e) as downloadCount
    FROM StudyFile f
    LEFT JOIN FileDownloadEvent e ON e.file.id = f.id
    GROUP BY f.id
    ORDER BY downloadCount DESC
""")
    Page<Object[]> findMostDownloadedFiles(Pageable pageable);

    @Query("""
    SELECT f
    FROM StudyFile f
    WHERE f.totalRatings >= :minRatings
    ORDER BY f.avgRating DESC, f.totalRatings DESC
""")
    Page<StudyFile> findHighestRatedFiles(@Param("minRatings") int minRatings, Pageable pageable);

// Under com.example.MedRational.Repositories

    // 4. Cold Content: Files with 0 downloads
    @Query("""
    SELECT f FROM StudyFile f
    WHERE NOT EXISTS (
        SELECT 1 FROM FileDownloadEvent e WHERE e.file.id = f.id
    )
    ORDER BY f.uploadedAt ASC
""")
    List<StudyFile> findColdFilesWithoutDownloads(org.springframework.data.domain.Pageable pageable);

    @Query("""
    SELECT COUNT(f) FROM StudyFile f
    WHERE NOT EXISTS (
        SELECT 1 FROM FileDownloadEvent e WHERE e.file.id = f.id
    )
""")
    long countColdFilesWithoutDownloads();
}