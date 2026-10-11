package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Preview.PreviewKind;
import com.example.MedRational.DTOs.RecentDownloadGlimpseDTO;
import com.example.MedRational.DTOs.StudyFileResponseDTO;
import com.example.MedRational.Entities.*;
import com.example.MedRational.Repositories.FileDownloadEventRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.UserFileDownloadRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Services.Interfaces.DownloadTrackingService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DownloadTrackingServiceImpl implements DownloadTrackingService {

    private final UserFileDownloadRepository userFileDownloadRepository;
    private final FileDownloadEventRepository fileDownloadEventRepository;
    private final StudyFileRepository studyFileRepository;
    private final UserRepository userRepository;
    private final int defaultRecentLimit;
    private final int maxRecentLimit;

    public DownloadTrackingServiceImpl(UserFileDownloadRepository userFileDownloadRepository,
                                       FileDownloadEventRepository fileDownloadEventRepository,
                                       StudyFileRepository studyFileRepository,
                                       UserRepository userRepository,
                                       @Value("${downloads.recent.default-limit:10}") int defaultRecentLimit,
                                       @Value("${downloads.recent.max-limit:50}") int maxRecentLimit) {
        this.userFileDownloadRepository = userFileDownloadRepository;
        this.fileDownloadEventRepository = fileDownloadEventRepository;
        this.studyFileRepository = studyFileRepository;
        this.userRepository = userRepository;
        this.defaultRecentLimit = defaultRecentLimit;
        this.maxRecentLimit = maxRecentLimit;
    }

    @Override
    @Transactional
    public void recordUserDownload(Long userId, Long fileId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));
        User user = userId == null ? null : userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // 1. Log platform metrics event
        fileDownloadEventRepository.save(FileDownloadEvent.builder()
                .file(file)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build());

        // 2. Track or update personal user download history: one row per (user, file),
        //    so a re-download only moves the file back to the top
        if (user != null) {
            UserFileDownload userDownload = userFileDownloadRepository
                    .findByUserIdAndFileId(userId, fileId)
                    .orElseGet(() -> UserFileDownload.builder()
                            .user(user)
                            .file(file)
                            .build());

            userDownload.setDownloadedAt(LocalDateTime.now());
            userFileDownloadRepository.save(userDownload);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentDownloadGlimpseDTO> getRecentDownloadsGlimpse(Long userId, Integer limit) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }

        return userFileDownloadRepository
                .findByUserIdOrderByDownloadedAtDesc(userId, PageRequest.of(0, effectiveRecentLimit(limit)))
                .stream()
                .map(this::toGlimpseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StudyFileResponseDTO getFileDetailsForNavigation(Long fileId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        Reasoning reasoning = file.getReasoning();
        Category category = reasoning != null ? reasoning.getCategory() : null;

        return StudyFileResponseDTO.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .publicUrl(file.getPublicUrl())
                .previewable(PreviewKind.detect(file.getFileName(), file.getFileType()).isPreviewable())
                .fileSizeBytes(file.getFileSizeBytes())
                .avgRating(file.getAvgRating())
                .totalRatings(file.getTotalRatings())
                .reasoningId(reasoning != null ? reasoning.getId() : null)
                .reasoningTitle(reasoning != null ? reasoning.getTitle() : null)
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .hashtags(file.getHashtags() != null
                        ? file.getHashtags().stream().map(Hashtag::getName).collect(Collectors.toSet())
                        : Set.of())
                .uploadedAt(file.getUploadedAt())
                .build();
    }

    int effectiveRecentLimit(Integer requested) {
        if (requested == null) {
            return defaultRecentLimit;
        }
        return Math.max(1, Math.min(requested, maxRecentLimit));
    }

    private RecentDownloadGlimpseDTO toGlimpseDTO(UserFileDownload download) {
        StudyFile file = download.getFile();
        Reasoning reasoning = file != null ? file.getReasoning() : null;
        Category category = reasoning != null ? reasoning.getCategory() : null;

        return RecentDownloadGlimpseDTO.builder()
                .fileId(file != null ? file.getId() : null)
                .fileName(file != null ? file.getFileName() : null)
                .fileType(file != null ? file.getFileType() : null)
                .fileSizeBytes(file != null ? file.getFileSizeBytes() : null)
                .downloadedAt(download.getDownloadedAt())
                .reasoningId(reasoning != null ? reasoning.getId() : null)
                .reasoningTitle(reasoning != null ? reasoning.getTitle() : null)
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .build();
    }
}