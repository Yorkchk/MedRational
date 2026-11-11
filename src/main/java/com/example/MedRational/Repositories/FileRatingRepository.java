package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.FileRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    // Average of every score on the platform, null when nobody rated anything
    @Query("SELECT AVG(r.score) FROM FileRating r")
    Double averageScore();

    // Before deleting files: file_id has no ON DELETE CASCADE (see StudyFileReferences)
    @Modifying
    @Query("DELETE FROM FileRating r WHERE r.file.id IN :fileIds")
    void deleteAllByFileIdIn(@Param("fileIds") Collection<Long> fileIds);

    // Account deletion: the score stays in the file's average, but no longer points to anyone
    @Modifying
    @Query("UPDATE FileRating r SET r.user = null WHERE r.user.id = :userId")
    void anonymizeByUserId(@Param("userId") Long userId);
}
