package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndFileId(Long userId, Long fileId);

    boolean existsByUserIdAndFileId(Long userId, Long fileId);

    void deleteByUserIdAndFileId(Long userId, Long fileId);

    Page<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Endpoint support: count total times a file has been favorited
    @Query("SELECT COUNT(f) FROM Favorite f WHERE f.file.id = :fileId")
    long countByFileId(@Param("fileId") Long fileId);
}