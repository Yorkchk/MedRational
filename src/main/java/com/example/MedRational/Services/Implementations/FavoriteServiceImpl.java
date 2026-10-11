package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Preview.PreviewKind;
import com.example.MedRational.DTOs.FavoriteResponseDTO;
import com.example.MedRational.DTOs.StudyFileResponseDTO;
import com.example.MedRational.Entities.Favorite;
import com.example.MedRational.Entities.Hashtag;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.FavoriteRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Services.Interfaces.FavoriteService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final StudyFileRepository studyFileRepository;

    @Override
    @Transactional
    public FavoriteResponseDTO addFavorite(Long userId, Long fileId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        Favorite favorite = favoriteRepository.findByUserIdAndFileId(userId, fileId)
                .orElseGet(() -> favoriteRepository.save(
                        Favorite.builder()
                                .user(user)
                                .file(file)
                                .build()
                ));

        return mapToDTO(favorite);
    }

    @Override
    @Transactional
    public void removeFavorite(Long userId, Long fileId) {
        if (!favoriteRepository.existsByUserIdAndFileId(userId, fileId)) {
            throw new EntityNotFoundException("Favorite not found for userId: " + userId + " and fileId: " + fileId);
        }
        favoriteRepository.deleteByUserIdAndFileId(userId, fileId);
    }

    @Override
    @Transactional
    public boolean toggleFavorite(Long userId, Long fileId) {
        if (favoriteRepository.existsByUserIdAndFileId(userId, fileId)) {
            favoriteRepository.deleteByUserIdAndFileId(userId, fileId);
            return false; // Removed
        } else {
            addFavorite(userId, fileId);
            return true; // Added
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FavoriteResponseDTO> getFavoritesByUserId(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public long getFavoriteCountForFile(Long fileId) {
        if (!studyFileRepository.existsById(fileId)) {
            throw new EntityNotFoundException("File not found with id: " + fileId);
        }
        return favoriteRepository.countByFileId(fileId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFileFavoritedByUser(Long userId, Long fileId) {
        return favoriteRepository.existsByUserIdAndFileId(userId, fileId);
    }

    private FavoriteResponseDTO mapToDTO(Favorite favorite) {
        StudyFile file = favorite.getFile();

        StudyFileResponseDTO fileDTO = StudyFileResponseDTO.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .publicUrl(file.getPublicUrl())
                .previewable(PreviewKind.detect(file.getFileName(), file.getFileType()).isPreviewable())
                .fileSizeBytes(file.getFileSizeBytes())
                .avgRating(file.getAvgRating())
                .totalRatings(file.getTotalRatings())
                .reasoningId(file.getReasoning() != null ? file.getReasoning().getId() : null)
                .reasoningTitle(file.getReasoning() != null ? file.getReasoning().getTitle() : null)
                .categoryId(file.getReasoning() != null && file.getReasoning().getCategory() != null
                        ? file.getReasoning().getCategory().getId() : null)
                .categoryName(file.getReasoning() != null && file.getReasoning().getCategory() != null
                        ? file.getReasoning().getCategory().getName() : null)
                .hashtags(file.getHashtags() != null
                        ? file.getHashtags().stream().map(Hashtag::getName).collect(Collectors.toSet())
                        : Set.of())
                .uploadedAt(file.getUploadedAt())
                .build();

        return FavoriteResponseDTO.builder()
                .favoriteId(favorite.getId())
                .userId(favorite.getUser().getId())
                .file(fileDTO)
                .createdAt(favorite.getCreatedAt())
                .build();
    }
}