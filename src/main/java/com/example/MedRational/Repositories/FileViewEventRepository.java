package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.FileViewEvent;
import com.example.MedRational.Repositories.StatisticsRows.BucketCount;
import com.example.MedRational.Repositories.StatisticsRows.IdCount;
import com.example.MedRational.Repositories.StatisticsRows.PeriodCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FileViewEventRepository extends JpaRepository<FileViewEvent, Long> {

    boolean existsByUserIdAndFileIdAndCreatedAtAfter(Long userId, Long fileId, LocalDateTime after);

    @Query("SELECT COUNT(v) FROM FileViewEvent v WHERE v.createdAt >= :from AND v.createdAt < :to")
    long countViewsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT v.file.id AS id, COUNT(v) AS total FROM FileViewEvent v
            WHERE v.createdAt >= :since
            GROUP BY v.file.id
            ORDER BY COUNT(v) DESC
            """)
    List<IdCount> countByFileSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("""
            SELECT f.reasoning.category.id AS id, COUNT(v) AS total
            FROM FileViewEvent v JOIN v.file f
            WHERE v.createdAt >= :since
            GROUP BY f.reasoning.category.id
            """)
    List<IdCount> countByCategorySince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT v.file.id AS id,
                   SUM(CASE WHEN v.createdAt >= :middle THEN 1 ELSE 0 END) AS recent,
                   SUM(CASE WHEN v.createdAt < :middle THEN 1 ELSE 0 END) AS earlier
            FROM FileViewEvent v
            WHERE v.createdAt >= :from
            GROUP BY v.file.id
            """)
    List<PeriodCount> compareByFile(@Param("from") LocalDateTime from, @Param("middle") LocalDateTime middle);

    @Query("""
            SELECT f.reasoning.category.id AS id,
                   SUM(CASE WHEN v.createdAt >= :middle THEN 1 ELSE 0 END) AS recent,
                   SUM(CASE WHEN v.createdAt < :middle THEN 1 ELSE 0 END) AS earlier
            FROM FileViewEvent v JOIN v.file f
            WHERE v.createdAt >= :from
            GROUP BY f.reasoning.category.id
            """)
    List<PeriodCount> compareByCategory(@Param("from") LocalDateTime from, @Param("middle") LocalDateTime middle);

    // Views per bucket of bucketDays days counted back from today
    @Query(value = """
            SELECT (CAST(:today AS date) - CAST(v.created_at AS date)) / :bucketDays AS bucket,
                   COUNT(*) AS total
            FROM file_view_events v
            WHERE v.created_at >= :since
            GROUP BY 1
            """, nativeQuery = true)
    List<BucketCount> countPerBucket(@Param("today") LocalDate today,
                                     @Param("since") LocalDateTime since,
                                     @Param("bucketDays") int bucketDays);

    // Account deletion: the view still counts, but no longer points to anyone
    @Modifying
    @Query("UPDATE FileViewEvent v SET v.user = null WHERE v.user.id = :userId")
    void anonymizeByUserId(@Param("userId") Long userId);
}
