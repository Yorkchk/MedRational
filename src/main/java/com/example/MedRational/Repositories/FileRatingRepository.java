package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.FileRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRatingRepository extends JpaRepository<FileRating, Long> {

    Optional<FileRating> findByUserIdAndFileId(Long userId, Long fileId);

    boolean existsByUserIdAndFileId(Long userId, Long fileId);

    // Compute average score and total count for a given file
    @Query("""
        SELECT COALESCE(AVG(r.score), 0.0), COUNT(r)
        FROM FileRating r
        WHERE r.file.id = :fileId
    """)
    Object[] getRatingStatsByFileId(@Param("fileId") Long fileId);
}