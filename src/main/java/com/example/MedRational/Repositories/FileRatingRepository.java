package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.FileRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRatingRepository extends JpaRepository<FileRating, Long> {

    Optional<FileRating> findByUserIdAndFileId(Long userId, Long fileId);

    boolean existsByUserIdAndFileId(Long userId, Long fileId);

    // Average score of a file, 0.0 when nobody rated it
    @Query("SELECT COALESCE(AVG(r.score), 0.0) FROM FileRating r WHERE r.file.id = :fileId")
    Double averageScoreByFileId(@Param("fileId") Long fileId);

    @Query("SELECT COUNT(r) FROM FileRating r WHERE r.file.id = :fileId")
    long countByFileId(@Param("fileId") Long fileId);

    // file_ratings has no ON DELETE CASCADE, so these run before deleting files
    @Modifying
    @Query("DELETE FROM FileRating r WHERE r.file.id = :fileId")
    void deleteAllByFileId(@Param("fileId") Long fileId);

    @Modifying
    @Query("DELETE FROM FileRating r WHERE r.file.id IN (SELECT f.id FROM StudyFile f WHERE f.reasoning.id = :reasoningId)")
    void deleteAllByReasoningId(@Param("reasoningId") Long reasoningId);

    @Modifying
    @Query("DELETE FROM FileRating r WHERE r.file.id IN (SELECT f.id FROM StudyFile f WHERE f.reasoning.category.id = :categoryId)")
    void deleteAllByCategoryId(@Param("categoryId") Long categoryId);
}
