package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.FileDownloadEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface FileDownloadEventRepository extends JpaRepository<FileDownloadEvent, Long> {

    long countByFileId(Long fileId);

    // Total download events within a specific time period
    @Query("SELECT COUNT(e) FROM FileDownloadEvent e WHERE e.file.id = :fileId AND e.createdAt >= :since")
    long countRecentDownloadsByFileId(@Param("fileId") Long fileId, @Param("since") LocalDateTime since);
}