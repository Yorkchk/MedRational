package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.FileDownloadEvent;
import com.example.MedRational.Repositories.StatisticsRows.BucketCount;
import com.example.MedRational.Repositories.StatisticsRows.IdCount;
import com.example.MedRational.Repositories.StatisticsRows.NameCount;
import com.example.MedRational.Repositories.StatisticsRows.PeriodCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FileDownloadEventRepository extends JpaRepository<FileDownloadEvent, Long> {

    long countByFileId(Long fileId);

    // Total download events within a specific time period
    @Query("SELECT COUNT(e) FROM FileDownloadEvent e WHERE e.file.id = :fileId AND e.createdAt >= :since")
    long countRecentDownloadsByFileId(@Param("fileId") Long fileId, @Param("since") LocalDateTime since);

    // Under com.example.MedRational.Repositories

    @Query("SELECT COUNT(e) FROM FileDownloadEvent e WHERE e.createdAt >= :since")
    long countDownloadsSince(@Param("since") LocalDateTime since);

    // --- Admin statistics ---

    @Query("SELECT COUNT(e) FROM FileDownloadEvent e WHERE e.createdAt >= :from AND e.createdAt < :to")
    long countDownloadsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT e.file.id AS id, COUNT(e) AS total FROM FileDownloadEvent e
            WHERE e.createdAt >= :since
            GROUP BY e.file.id
            ORDER BY COUNT(e) DESC
            """)
    List<IdCount> countByFileSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("""
            SELECT f.reasoning.category.id AS id, COUNT(e) AS total
            FROM FileDownloadEvent e JOIN e.file f
            WHERE e.createdAt >= :since
            GROUP BY f.reasoning.category.id
            """)
    List<IdCount> countByCategorySince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT h.name AS name, COUNT(e) AS total
            FROM FileDownloadEvent e JOIN e.file f JOIN f.hashtags h
            WHERE e.createdAt >= :since
            GROUP BY h.name
            ORDER BY COUNT(e) DESC
            """)
    List<NameCount> countByHashtagSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("""
            SELECT e.file.id AS id,
                   SUM(CASE WHEN e.createdAt >= :middle THEN 1 ELSE 0 END) AS recent,
                   SUM(CASE WHEN e.createdAt < :middle THEN 1 ELSE 0 END) AS earlier
            FROM FileDownloadEvent e
            WHERE e.createdAt >= :from
            GROUP BY e.file.id
            """)
    List<PeriodCount> compareByFile(@Param("from") LocalDateTime from, @Param("middle") LocalDateTime middle);

    @Query("""
            SELECT f.reasoning.category.id AS id,
                   SUM(CASE WHEN e.createdAt >= :middle THEN 1 ELSE 0 END) AS recent,
                   SUM(CASE WHEN e.createdAt < :middle THEN 1 ELSE 0 END) AS earlier
            FROM FileDownloadEvent e JOIN e.file f
            WHERE e.createdAt >= :from
            GROUP BY f.reasoning.category.id
            """)
    List<PeriodCount> compareByCategory(@Param("from") LocalDateTime from, @Param("middle") LocalDateTime middle);

    // Downloads per bucket of bucketDays days counted back from today
    @Query(value = """
            SELECT (CAST(:today AS date) - CAST(e.created_at AS date)) / :bucketDays AS bucket,
                   COUNT(*) AS total
            FROM file_download_events e
            WHERE e.created_at >= :since
            GROUP BY 1
            """, nativeQuery = true)
    List<BucketCount> countPerBucket(@Param("today") LocalDate today,
                                     @Param("since") LocalDateTime since,
                                     @Param("bucketDays") int bucketDays);

}