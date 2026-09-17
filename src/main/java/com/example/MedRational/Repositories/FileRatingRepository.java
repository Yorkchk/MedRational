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

    @Query("SELECT AVG(r.score) FROM FileRating r WHERE r.file.id = :fileId")
    Double calculateAverageRating(@Param("fileId") Long fileId);

    @Query("SELECT COUNT(r) FROM FileRating r WHERE r.file.id = :fileId")
    Integer countRatingsByFileId(@Param("fileId") Long fileId);
}