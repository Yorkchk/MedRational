package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.NoveltiesFeedResponseDTO;
import com.example.MedRational.DTOs.NoveltyFileItemDTO;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Hashtag;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Services.Interfaces.NoveltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoveltyServiceImpl implements NoveltyService {

    private final StudyFileRepository studyFileRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public NoveltiesFeedResponseDTO getNoveltiesFeed(User user, Pageable pageable) {
        LocalDateTime baseline = baselineOf(user);

        Page<StudyFile> filePage = studyFileRepository.findAllFilesForNovelties(pageable);
        Page<NoveltyFileItemDTO> mappedPage = filePage.map(file -> toNoveltyDTO(file, baseline));

        long unreadCount = baseline != null ? studyFileRepository.countByUploadedAtAfter(baseline) : 0L;

        return NoveltiesFeedResponseDTO.builder()
                .newFilesCount(unreadCount)
                .feed(mappedPage)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadUploadsCount(User user) {
        LocalDateTime baseline = baselineOf(user);
        return baseline != null ? studyFileRepository.countByUploadedAtAfter(baseline) : 0L;
    }

    @Override
    @Transactional
    public void markNoveltiesAsSeen(User user) {
        user.setNoveltiesSeenAt(LocalDateTime.now(clock));
        userRepository.save(user);
    }

    // Files uploaded after this are "new": the last time the user opened the feed, or account
    // creation for users who never did, so a fresh account isn't flooded with the whole library.
    // Deliberately not lastLoginAt, which every sign-in overwrites.
    private LocalDateTime baselineOf(User user) {
        return user.getNoveltiesSeenAt() != null ? user.getNoveltiesSeenAt() : user.getCreatedAt();
    }

    private NoveltyFileItemDTO toNoveltyDTO(StudyFile file, LocalDateTime baseline) {
        Reasoning reasoning = file.getReasoning();
        Category category = reasoning != null ? reasoning.getCategory() : null;

        boolean isNew = (baseline != null && file.getUploadedAt() != null)
                && file.getUploadedAt().isAfter(baseline);

        Set<String> tagNames = file.getHashtags() != null
                ? file.getHashtags().stream().map(Hashtag::getName).collect(Collectors.toSet())
                : Set.of();

        return NoveltyFileItemDTO.builder()
                .fileId(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .fileSizeBytes(file.getFileSizeBytes())
                .avgRating(file.getAvgRating())
                .totalRatings(file.getTotalRatings())
                .uploadedAt(file.getUploadedAt())
                .reasoningId(reasoning != null ? reasoning.getId() : null)
                .reasoningTitle(reasoning != null ? reasoning.getTitle() : null)
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .hashtags(tagNames)
                .isNew(isNew)
                .build();
    }
}