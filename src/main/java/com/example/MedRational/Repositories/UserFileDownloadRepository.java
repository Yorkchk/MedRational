package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.UserFileDownload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserFileDownloadRepository extends JpaRepository<UserFileDownload, Long> {

    Optional<UserFileDownload> findByUserIdAndFileId(Long userId, Long fileId);

    // User's personal download history tab
    Page<UserFileDownload> findByUserIdOrderByDownloadedAtDesc(Long userId, Pageable pageable);
}