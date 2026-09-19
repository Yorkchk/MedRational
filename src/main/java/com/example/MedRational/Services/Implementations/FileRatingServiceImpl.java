package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.FileRatingSummaryDTO;
import com.example.MedRational.DTOs.RateFileRequestDTO;
import com.example.MedRational.Entities.FileRating;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.FileRatingRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Services.Interfaces.FileRatingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FileRatingServiceImpl implements FileRatingService {

    private final FileRatingRepository fileRatingRepository;
    private final StudyFileRepository studyFileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public FileRatingSummaryDTO rateFile(Long userId, Long fileId, RateFileRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        // Create or update rating
        FileRating rating = fileRatingRepository.findByUserIdAndFileId(userId, fileId)
                .orElseGet(() -> FileRating.builder()
                        .user(user)
                        .file(file)
                        .build());

        rating.setScore(request.getScore());
        fileRatingRepository.save(rating);

        // Recompute and update denormalized stats on StudyFile
        return refreshFileRatingStats(file, request.getScore());
    }

    @Override
    @Transactional(readOnly = true)
    public FileRatingSummaryDTO getFileRatingSummary(Long fileId, Long userId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        Integer userScore = null;
        if (userId != null) {
            userScore = fileRatingRepository.findByUserIdAndFileId(userId, fileId)
                    .map(FileRating::getScore)
                    .orElse(null);
        }

        return FileRatingSummaryDTO.builder()
                .fileId(file.getId())
                .avgRating(file.getAvgRating() != null ? file.getAvgRating() : 0.0)
                .totalRatings(file.getTotalRatings() != null ? file.getTotalRatings() : 0)
                .userRating(userScore)
                .build();
    }

    @Override
    @Transactional
    public FileRatingSummaryDTO removeRating(Long userId, Long fileId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        Optional<FileRating> existingRating = fileRatingRepository.findByUserIdAndFileId(userId, fileId);
        existingRating.ifPresent(fileRatingRepository::delete);

        return refreshFileRatingStats(file, null);
    }

    private FileRatingSummaryDTO refreshFileRatingStats(StudyFile file, Integer currentUserScore) {
        Object[] stats = (Object[]) fileRatingRepository.getRatingStatsByFileId(file.getId())[0];
        Double rawAvg = (Double) stats[0];
        Long totalCount = (Long) stats[1];

        // Round average rating to 1 decimal place (e.g. 4.67 -> 4.7)
        double roundedAvg = BigDecimal.valueOf(rawAvg)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();

        file.setAvgRating(roundedAvg);
        file.setTotalRatings(totalCount.intValue());
        studyFileRepository.save(file);

        return FileRatingSummaryDTO.builder()
                .fileId(file.getId())
                .avgRating(roundedAvg)
                .totalRatings(totalCount.intValue())
                .userRating(currentUserScore)
                .build();
    }
}