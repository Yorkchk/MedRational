package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Favorite;
import com.example.MedRational.Repositories.StatisticsRows.IdCount;
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
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndFileId(Long userId, Long fileId);

    boolean existsByUserIdAndFileId(Long userId, Long fileId);

    void deleteByUserIdAndFileId(Long userId, Long fileId);

    Page<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Endpoint support: count total times a file has been favorited
    @Query("SELECT COUNT(f) FROM Favorite f WHERE f.file.id = :fileId")
    long countByFileId(@Param("fileId") Long fileId);

    // Under com.example.MedRational.Repositories

    @Query("""
    SELECT f.file, COUNT(f) as favCount
    FROM Favorite f
    GROUP BY f.file.id
    ORDER BY favCount DESC
""")
    Page<Object[]> findMostFavoritedFiles(Pageable pageable);

    // --- Admin statistics ---

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    @Query("""
            SELECT f.file.id AS id, COUNT(f) AS total FROM Favorite f
            WHERE f.createdAt >= :since
            GROUP BY f.file.id
            ORDER BY COUNT(f) DESC
            """)
    List<IdCount> countByFileSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("""
            SELECT file.reasoning.category.id AS id, COUNT(f) AS total
            FROM Favorite f JOIN f.file file
            WHERE f.createdAt >= :since
            GROUP BY file.reasoning.category.id
            """)
    List<IdCount> countByCategorySince(@Param("since") LocalDateTime since);

}