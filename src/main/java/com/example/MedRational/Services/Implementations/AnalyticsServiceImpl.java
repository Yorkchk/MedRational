package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.*;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.*;
import com.example.MedRational.Services.Interfaces.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final StudyFileRepository studyFileRepository;
    private final FileDownloadEventRepository downloadEventRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final HashtagRepository hashtagRepository;


    @Override
    @Transactional(readOnly = true)
    public AdminDashboardMetricsDTO getAdminDashboardOverview() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        return AdminDashboardMetricsDTO.builder()
                .totalUsers(userRepository.count())
                .totalFiles(studyFileRepository.count())
                .totalDownloadsAllTime(downloadEventRepository.count())
                .downloadsLast7Days(downloadEventRepository.countDownloadsSince(sevenDaysAgo))
                .newFilesLast7Days(studyFileRepository.countByUploadedAtAfter(sevenDaysAgo))
                .topDownloadedFiles(getTopDownloadedFiles(5))
                .mostFavoritedFiles(getMostFavoritedFiles(5))
                .highestRatedFiles(getHighestRatedFiles(1, 5))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileMetricItemDTO> getTopDownloadedFiles(int limit) {
        return studyFileRepository.findMostDownloadedFiles(PageRequest.of(0, limit))
                .stream()
                .map(row -> {
                    StudyFile file = (StudyFile) row[0];
                    long count = (Long) row[1];
                    return toItemDTO(file, count);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileMetricItemDTO> getMostFavoritedFiles(int limit) {
        return favoriteRepository.findMostFavoritedFiles(PageRequest.of(0, limit))
                .stream()
                .map(row -> {
                    StudyFile file = (StudyFile) row[0];
                    long count = (Long) row[1];
                    return toItemDTO(file, count);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileMetricItemDTO> getHighestRatedFiles(int minRatings, int limit) {
        return studyFileRepository.findHighestRatedFiles(minRatings, PageRequest.of(0, limit))
                .stream()
                .map(file -> toItemDTO(file, file.getTotalRatings() != null ? file.getTotalRatings() : 0))
                .collect(Collectors.toList());
    }

    private FileMetricItemDTO toItemDTO(StudyFile file, long metricValue) {
        Reasoning reasoning = file.getReasoning();
        String reasoningTitle = reasoning != null ? reasoning.getTitle() : "Unassigned";
        String categoryName = (reasoning != null && reasoning.getCategory() != null)
                ? reasoning.getCategory().getName()
                : "Unassigned";

        return FileMetricItemDTO.builder()
                .fileId(file.getId())
                .fileName(file.getFileName())
                .categoryName(categoryName)
                .reasoningTitle(reasoningTitle)
                .metricValue(metricValue)
                .avgRating(file.getAvgRating())
                .totalRatings(file.getTotalRatings())
                .build();
    }

    // Inside com.example.MedRational.Services.Implementations.AnalyticsServiceImpl

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDemandDTO> getCategoryDemandBreakdown() {
        List<Object[]> rawStats = categoryRepository.findDownloadVolumeByCategory();
        long totalDownloads = downloadEventRepository.count();

        return rawStats.stream().map(row -> {
            String categoryName = (String) row[0];
            long downloads = (Long) row[1];
            double percentage = totalDownloads > 0
                    ? Math.round(((double) downloads / totalDownloads * 100.0) * 10.0) / 10.0
                    : 0.0;

            return CategoryDemandDTO.builder()
                    .categoryName(categoryName)
                    .totalDownloads(downloads)
                    .percentageOfTotal(percentage)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryStorageMetricDTO> getStorageFootprintByCategory() {
        return categoryRepository.findStorageUsageByCategory().stream().map(row -> {
            String categoryName = (String) row[0];
            long bytes = (Long) row[1];
            long filesCount = (Long) row[2];
            double megabytes = Math.round((bytes / (1024.0 * 1024.0)) * 100.0) / 100.0;

            return CategoryStorageMetricDTO.builder()
                    .categoryName(categoryName)
                    .totalSizeBytes(bytes)
                    .totalSizeMegaBytes(megabytes)
                    .totalFiles(filesCount)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrendingHashtagMetricDTO> getTrendingHashtags(int limit) {
        return hashtagRepository.findMostUsedHashtags(PageRequest.of(0, limit)).stream().map(row ->
                TrendingHashtagMetricDTO.builder()
                        .hashtag((String) row[0])
                        .attachedFilesCount((Long) row[1])
                        .build()
        ).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ContentHealthSummaryDTO getContentHealthSummary() {
        long totalFiles = studyFileRepository.count();
        long zeroDownloads = studyFileRepository.countColdFilesWithoutDownloads();

        // Active files that have at least 1 download event
        long engagedFiles = totalFiles - zeroDownloads;
        double engagementRate = totalFiles > 0
                ? Math.round(((double) engagedFiles / totalFiles * 100.0) * 10.0) / 10.0
                : 0.0;

        return ContentHealthSummaryDTO.builder()
                .totalFiles(totalFiles)
                .zeroDownloadFilesCount(zeroDownloads)
                .contentEngagementRate(engagementRate)
                .build();
    }
}