package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AnalyticsService {

    // Aggregated dashboard view for the admin home screen
    AdminDashboardMetricsDTO getAdminDashboardOverview();

    // Ranked list of most downloaded files
    List<FileMetricItemDTO> getTopDownloadedFiles(int limit);

    // Ranked list of most favorited files
    List<FileMetricItemDTO> getMostFavoritedFiles(int limit);

    // Ranked list of highest rated files (with minimum rating threshold)
    List<FileMetricItemDTO> getHighestRatedFiles(int minRatings, int limit);

    // Under com.example.MedRational.Services.Interfaces

    List<CategoryDemandDTO> getCategoryDemandBreakdown();

    List<CategoryStorageMetricDTO> getStorageFootprintByCategory();

    List<TrendingHashtagMetricDTO> getTrendingHashtags(int limit);

    ContentHealthSummaryDTO getContentHealthSummary();
}