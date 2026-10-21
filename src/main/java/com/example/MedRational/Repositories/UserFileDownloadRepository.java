package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.UserFileDownload;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFileDownloadRepository extends JpaRepository<UserFileDownload, Long> {

    Optional<UserFileDownload> findByUserIdAndFileId(Long userId, Long fileId);

    // Newest first; the pageable's size is the limit. One row per (user, file), so no duplicates.
    // Eagerly joins file -> reasoning -> category to avoid N+1 queries when building the glimpse
    @EntityGraph(attributePaths = {"file", "file.reasoning", "file.reasoning.category"})
    List<UserFileDownload> findByUserIdOrderByDownloadedAtDesc(Long userId, Pageable pageable);

    // Account deletion
    @Modifying
    @Query("DELETE FROM UserFileDownload d WHERE d.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
