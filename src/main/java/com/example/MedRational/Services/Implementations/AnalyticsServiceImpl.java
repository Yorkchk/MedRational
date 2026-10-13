package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.*;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import com.example.MedRational.Repositories.*;
import com.example.MedRational.Repositories.StatisticsRows.BucketCount;
import com.example.MedRational.Repositories.StatisticsRows.IdCount;
import com.example.MedRational.Repositories.StatisticsRows.PeriodCount;
import com.example.MedRational.Services.Interfaces.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    static final int TOP_LIMIT = 10;
    static final int WEEKS_SHOWN = 12;
    static final int DAYS_SHOWN = 30;
    static final int HIGHEST_RATED_MIN_RATINGS = 3;
    // Older than any event, so "since" queries also serve the all-time numbers
    static final LocalDateTime ALL_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final StudyFileRepository studyFileRepository;
    private final FileDownloadEventRepository downloadEventRepository;
    private final FileViewEventRepository viewEventRepository;
    private final FavoriteRepository favoriteRepository;
    private final FileRatingRepository fileRatingRepository;
    private final UserRepository userRepository;
    private final UserDailyActivityRepository activityRepository;
    private final CategoryRepository categoryRepository;
    private final HashtagRepository hashtagRepository;
    private final SuggestionRepository suggestionRepository;
    private final WorkshopProjectRepository workshopProjectRepository;
    private final Clock clock;


    @Override
    @Transactional(readOnly = true)
    public AdminDashboardMetricsDTO getAdminDashboardOverview() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now(clock).minusDays(7);

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

    // "This week" is the last 7 days with today; "previous week" the 7 days before
    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsDTO getStatistics() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        LocalDateTime previousWeekStart = today.minusDays(13).atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();

        return AdminStatisticsDTO.builder()
                .generatedAt(now)
                .users(userStats(today, weekStart, previousWeekStart, tomorrow))
                .usage(usageStats(today, weekStart, previousWeekStart, tomorrow))
                .lastWeek(contentStats(weekStart))
                .allTime(contentStats(ALL_TIME))
                .trends(trendStats(previousWeekStart, weekStart))
                .engagement(engagementStats(weekStart))
                .build();
    }

    private UserStatsDTO userStats(LocalDate today, LocalDateTime weekStart,
                                   LocalDateTime previousWeekStart, LocalDateTime tomorrow) {
        LocalDateTime signUpsSince = today.minusDays(WEEKS_SHOWN * 7L - 1).atStartOfDay();
        return UserStatsDTO.builder()
                .totalStudents(userRepository.countByRole(Role.ROLE_USER))
                .newStudentsThisWeek(userRepository.countByRoleCreatedBetween(Role.ROLE_USER, weekStart, tomorrow))
                .newStudentsPreviousWeek(
                        userRepository.countByRoleCreatedBetween(Role.ROLE_USER, previousWeekStart, weekStart))
                .weeklyActiveStudents(activityRepository.countActiveStudents(today.minusDays(6), today))
                .weeklyActiveStudentsPreviousWeek(
                        activityRepository.countActiveStudents(today.minusDays(13), today.minusDays(7)))
                .dailyActiveStudents(activityRepository.countActiveStudents(today, today))
                .activatedStudents(userRepository.countActivatedStudents())
                .weeklySignUps(series(
                        userRepository.countStudentSignUpsPerBucket(today, signUpsSince, 7), today, 7, WEEKS_SHOWN))
                .weeklyActiveSeries(series(
                        activityRepository.countActiveStudentsPerBucket(today, signUpsSince.toLocalDate(), 7),
                        today, 7, WEEKS_SHOWN))
                .build();
    }

    private UsageStatsDTO usageStats(LocalDate today, LocalDateTime weekStart,
                                     LocalDateTime previousWeekStart, LocalDateTime tomorrow) {
        LocalDateTime dailySince = today.minusDays(DAYS_SHOWN - 1).atStartOfDay();
        return UsageStatsDTO.builder()
                .totalDownloads(downloadEventRepository.count())
                .downloadsThisWeek(downloadEventRepository.countDownloadsBetween(weekStart, tomorrow))
                .downloadsPreviousWeek(downloadEventRepository.countDownloadsBetween(previousWeekStart, weekStart))
                .totalViews(viewEventRepository.count())
                .viewsThisWeek(viewEventRepository.countViewsBetween(weekStart, tomorrow))
                .viewsPreviousWeek(viewEventRepository.countViewsBetween(previousWeekStart, weekStart))
                .dailyDownloads(series(
                        downloadEventRepository.countPerBucket(today, dailySince, 1), today, 1, DAYS_SHOWN))
                .dailyViews(series(
                        viewEventRepository.countPerBucket(today, dailySince, 1), today, 1, DAYS_SHOWN))
                .build();
    }

    private ContentStatsDTO contentStats(LocalDateTime since) {
        PageRequest top = PageRequest.of(0, TOP_LIMIT);
        List<IdCount> downloads = downloadEventRepository.countByFileSince(since, top);
        List<IdCount> views = viewEventRepository.countByFileSince(since, top);
        List<IdCount> favorites = favoriteRepository.countByFileSince(since, top);

        Set<Long> fileIds = new HashSet<>();
        for (List<IdCount> rows : List.of(downloads, views, favorites)) {
            rows.forEach(row -> fileIds.add(row.getId()));
        }
        Map<Long, StudyFile> files = filesById(fileIds);

        return ContentStatsDTO.builder()
                .topDownloaded(toItems(downloads, files))
                .topViewed(toItems(views, files))
                .mostFavorited(toItems(favorites, files))
                .categories(categoryActivity(
                        categoryRepository.findAll(),
                        downloadEventRepository.countByCategorySince(since),
                        viewEventRepository.countByCategorySince(since),
                        favoriteRepository.countByCategorySince(since)))
                .topHashtags(downloadEventRepository.countByHashtagSince(since, top).stream()
                        .map(row -> new HashtagActivityDTO(row.getName(), row.getTotal().longValue()))
                        .toList())
                .build();
    }

    private TrendStatsDTO trendStats(LocalDateTime previousWeekStart, LocalDateTime weekStart) {
        List<Map.Entry<Long, long[]>> risingFiles = rising(
                downloadEventRepository.compareByFile(previousWeekStart, weekStart),
                viewEventRepository.compareByFile(previousWeekStart, weekStart));
        Map<Long, StudyFile> files = filesById(risingFiles.stream().map(Map.Entry::getKey).toList());
        List<TrendingItemDTO> trendingFiles = new ArrayList<>();
        for (Map.Entry<Long, long[]> entry : risingFiles) {
            StudyFile file = files.get(entry.getKey());
            if (file == null) continue;
            Reasoning reasoning = file.getReasoning();
            trendingFiles.add(TrendingItemDTO.builder()
                    .id(file.getId())
                    .name(file.getFileName())
                    .reasoningId(reasoning != null ? reasoning.getId() : null)
                    .categoryName(categoryName(file))
                    .thisWeek(entry.getValue()[0])
                    .previousWeek(entry.getValue()[1])
                    .build());
        }

        List<Map.Entry<Long, long[]>> risingCategories = rising(
                downloadEventRepository.compareByCategory(previousWeekStart, weekStart),
                viewEventRepository.compareByCategory(previousWeekStart, weekStart));
        Map<Long, Category> categories = categoryRepository
                .findAllById(risingCategories.stream().map(Map.Entry::getKey).toList()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        List<TrendingItemDTO> trendingCategories = new ArrayList<>();
        for (Map.Entry<Long, long[]> entry : risingCategories) {
            Category category = categories.get(entry.getKey());
            if (category == null) continue;
            trendingCategories.add(TrendingItemDTO.builder()
                    .id(category.getId())
                    .name(category.getName())
                    .thisWeek(entry.getValue()[0])
                    .previousWeek(entry.getValue()[1])
                    .build());
        }

        return TrendStatsDTO.builder().files(trendingFiles).categories(trendingCategories).build();
    }

    private EngagementStatsDTO engagementStats(LocalDateTime weekStart) {
        return EngagementStatsDTO.builder()
                .totalFiles(studyFileRepository.count())
                .newFilesThisWeek(studyFileRepository.countByUploadedAtAfter(weekStart))
                .filesWithoutDownloads(studyFileRepository.countColdFilesWithoutDownloads())
                .totalFavorites(favoriteRepository.count())
                .favoritesThisWeek(favoriteRepository.countByCreatedAtGreaterThanEqual(weekStart))
                .totalRatings(fileRatingRepository.count())
                .ratingsThisWeek(fileRatingRepository.countByCreatedAtGreaterThanEqual(weekStart))
                .averageRating(roundToTenth(fileRatingRepository.averageScore()))
                .highestRated(getHighestRatedFiles(HIGHEST_RATED_MIN_RATINGS, 5))
                .openSuggestions(suggestionRepository.countByStatus(SuggestionStatus.PENDING))
                .unreadSuggestions(suggestionRepository.countUnreadByAdmin())
                .upcomingWorkshops(workshopProjectRepository.countByStatus(ProjectStatus.UPCOMING))
                .openWorkshops(workshopProjectRepository.countByStatus(ProjectStatus.OPEN))
                .closedWorkshops(workshopProjectRepository.countByStatus(ProjectStatus.CLOSED))
                .build();
    }

    // Turns bucket rows (0 = the period ending today) into a chart, oldest first, with empty periods at 0
    public static List<StatPointDTO> series(List<BucketCount> rows, LocalDate today, int bucketDays, int buckets) {
        long[] values = new long[buckets];
        for (BucketCount row : rows) {
            int bucket = row.getBucket().intValue();
            if (bucket >= 0 && bucket < buckets) {
                values[bucket] += row.getTotal().longValue();
            }
        }
        List<StatPointDTO> points = new ArrayList<>(buckets);
        for (int bucket = buckets - 1; bucket >= 0; bucket--) {
            LocalDate start = today.minusDays((long) bucket * bucketDays + bucketDays - 1);
            points.add(new StatPointDTO(start, values[bucket]));
        }
        return points;
    }

    // Adds downloads and views per id, keeps what rose against the previous week, biggest rise first.
    // The value holds {this week, previous week}
    @SafeVarargs
    public static List<Map.Entry<Long, long[]>> rising(List<PeriodCount>... sources) {
        Map<Long, long[]> totals = new LinkedHashMap<>();
        for (List<PeriodCount> rows : sources) {
            for (PeriodCount row : rows) {
                if (row.getId() == null) continue;
                long[] total = totals.computeIfAbsent(row.getId(), id -> new long[2]);
                total[0] += row.getRecent().longValue();
                total[1] += row.getEarlier().longValue();
            }
        }
        return totals.entrySet().stream()
                .filter(entry -> entry.getValue()[0] > entry.getValue()[1])
                .sorted(Comparator.<Map.Entry<Long, long[]>>comparingLong(
                                entry -> entry.getValue()[0] - entry.getValue()[1]).reversed()
                        .thenComparing(Comparator.<Map.Entry<Long, long[]>>comparingLong(
                                entry -> entry.getValue()[0]).reversed()))
                .limit(TOP_LIMIT)
                .toList();
    }

    // Every category, even without traffic, busiest first
    public static List<CategoryActivityDTO> categoryActivity(List<Category> categories, List<IdCount> downloads,
                                                      List<IdCount> views, List<IdCount> favorites) {
        Map<Long, Long> downloadsById = countsById(downloads);
        Map<Long, Long> viewsById = countsById(views);
        Map<Long, Long> favoritesById = countsById(favorites);
        return categories.stream()
                .map(category -> CategoryActivityDTO.builder()
                        .categoryId(category.getId())
                        .categoryName(category.getName())
                        .downloads(downloadsById.getOrDefault(category.getId(), 0L))
                        .views(viewsById.getOrDefault(category.getId(), 0L))
                        .favorites(favoritesById.getOrDefault(category.getId(), 0L))
                        .build())
                .sorted(Comparator.comparingLong(
                                (CategoryActivityDTO c) -> c.getDownloads() + c.getViews() + c.getFavorites())
                        .reversed()
                        .thenComparing(CategoryActivityDTO::getCategoryName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    public static Double roundToTenth(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }

    private static Map<Long, Long> countsById(List<IdCount> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (IdCount row : rows) {
            if (row.getId() != null) {
                counts.merge(row.getId(), row.getTotal().longValue(), Long::sum);
            }
        }
        return counts;
    }

    private Map<Long, StudyFile> filesById(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return studyFileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StudyFile::getId, Function.identity()));
    }

    // Keeps the ranking order; skips a file deleted between the two queries
    private List<FileMetricItemDTO> toItems(List<IdCount> rows, Map<Long, StudyFile> files) {
        List<FileMetricItemDTO> items = new ArrayList<>();
        for (IdCount row : rows) {
            StudyFile file = files.get(row.getId());
            if (file != null) {
                items.add(toItemDTO(file, row.getTotal().longValue()));
            }
        }
        return items;
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

    // Reasoning and category stay null when missing; clients show their own translated label
    private FileMetricItemDTO toItemDTO(StudyFile file, long metricValue) {
        Reasoning reasoning = file.getReasoning();

        return FileMetricItemDTO.builder()
                .fileId(file.getId())
                .fileName(file.getFileName())
                .reasoningId(reasoning != null ? reasoning.getId() : null)
                .categoryName(categoryName(file))
                .reasoningTitle(reasoning != null ? reasoning.getTitle() : null)
                .metricValue(metricValue)
                .avgRating(file.getAvgRating())
                .totalRatings(file.getTotalRatings())
                .build();
    }

    private static String categoryName(StudyFile file) {
        Reasoning reasoning = file.getReasoning();
        return reasoning != null && reasoning.getCategory() != null ? reasoning.getCategory().getName() : null;
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
