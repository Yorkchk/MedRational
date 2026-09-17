package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.UserFileDownload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFileDownloadRepository extends JpaRepository<UserFileDownload, Long> {

    Optional<UserFileDownload> findByUserIdAndFileId(Long userId, Long fileId);

    // Eagerly joins file -> reasoning -> category to avoid N+1 queries when building the glimpse
    @EntityGraph(attributePaths = {"file", "file.reasoning", "file.reasoning.category"})
    List<UserFileDownload> findTop10ByUserIdOrderByDownloadedAtDesc(Long userId);

    Page<UserFileDownload> findByUserIdOrderByDownloadedAtDesc(Long userId, Pageable pageable);
}