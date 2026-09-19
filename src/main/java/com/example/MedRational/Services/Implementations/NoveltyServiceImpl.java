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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoveltyServiceImpl implements NoveltyService {

    private final StudyFileRepository studyFileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public NoveltiesFeedResponseDTO getNoveltiesFeed(Long userId, Pageable pageable) {
        LocalDateTime lastLogin = null;
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
            lastLogin = user.getLastLoginAt();
        }

        final LocalDateTime comparisonTime = lastLogin;

        Page<StudyFile> filePage = studyFileRepository.findAllFilesForNovelties(pageable);

        Page<NoveltyFileItemDTO> mappedPage = filePage.map(file -> toNoveltyDTO(file, comparisonTime));

        long unreadCount = (comparisonTime != null)
                ? studyFileRepository.countByUploadedAtAfter(comparisonTime)
                : 0L;

        return NoveltiesFeedResponseDTO.builder()
                .newFilesCount(unreadCount)
                .feed(mappedPage)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadUploadsCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        if (user.getLastLoginAt() == null) {
            return studyFileRepository.count();
        }

        return studyFileRepository.countByUploadedAtAfter(user.getLastLoginAt());
    }

    @Override
    @Transactional
    public void markNoveltiesAsSeen(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private NoveltyFileItemDTO toNoveltyDTO(StudyFile file, LocalDateTime lastLoginAt) {
        Reasoning reasoning = file.getReasoning();
        Category category = reasoning != null ? reasoning.getCategory() : null;

        // File is marked new if user has a login timestamp and upload occurred after it
        boolean isNew = (lastLoginAt != null && file.getUploadedAt() != null)
                && file.getUploadedAt().isAfter(lastLoginAt);

        Set<String> tagNames = file.getHashtags() != null
                ? file.getHashtags().stream().map(Hashtag::getName).collect(Collectors.toSet())
                : Set.of();

        return NoveltyFileItemDTO.builder()
                .fileId(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .fileSizeBytes(file.getFileSizeBytes())
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