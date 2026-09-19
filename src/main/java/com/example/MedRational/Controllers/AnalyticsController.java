package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.*;
import com.example.MedRational.Services.Interfaces.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<AdminDashboardMetricsDTO> getOverview() {
        return ResponseEntity.ok(analyticsService.getAdminDashboardOverview());
    }

    @GetMapping("/top-downloads")
    public ResponseEntity<List<FileMetricItemDTO>> getTopDownloads(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getTopDownloadedFiles(limit));
    }

    @GetMapping("/most-favorited")
    public ResponseEntity<List<FileMetricItemDTO>> getMostFavorited(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getMostFavoritedFiles(limit));
    }

    @GetMapping("/highest-rated")
    public ResponseEntity<List<FileMetricItemDTO>> getHighestRated(
            @RequestParam(defaultValue = "1") int minRatings,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getHighestRatedFiles(minRatings, limit));
    }
    // Inside com.example.MedRational.Controllers.AnalyticsController

    @GetMapping("/category-demand")
    public ResponseEntity<List<CategoryDemandDTO>> getCategoryDemand() {
        return ResponseEntity.ok(analyticsService.getCategoryDemandBreakdown());
    }

    @GetMapping("/storage-footprint")
    public ResponseEntity<List<CategoryStorageMetricDTO>> getStorageFootprint() {
        return ResponseEntity.ok(analyticsService.getStorageFootprintByCategory());
    }

    @GetMapping("/trending-hashtags")
    public ResponseEntity<List<TrendingHashtagMetricDTO>> getTrendingHashtags(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getTrendingHashtags(limit));
    }

    @GetMapping("/content-health")
    public ResponseEntity<ContentHealthSummaryDTO> getContentHealth() {
        return ResponseEntity.ok(analyticsService.getContentHealthSummary());
    }

}