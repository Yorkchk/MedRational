package com.example.MedRational.Services;

import com.example.MedRational.DTOs.AdminStatisticsDTO;
import com.example.MedRational.DTOs.CategoryActivityDTO;
import com.example.MedRational.DTOs.FileMetricItemDTO;
import com.example.MedRational.DTOs.StatPointDTO;
import com.example.MedRational.DTOs.TrendingItemDTO;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.*;
import com.example.MedRational.Repositories.StatisticsRows.BucketCount;
import com.example.MedRational.Repositories.StatisticsRows.IdCount;
import com.example.MedRational.Repositories.StatisticsRows.PeriodCount;
import com.example.MedRational.Services.Implementations.AnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceImplTest {

    // Tuesday 13 October 2026, 15:00
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 15, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private record Bucket(Number getBucket, Number getTotal) implements BucketCount {
    }

    private record Count(Long getId, Number getTotal) implements IdCount {
    }

    private record Period(Long getId, Number getRecent, Number getEarlier) implements PeriodCount {
    }

    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final FileDownloadEventRepository downloadEventRepository = mock(FileDownloadEventRepository.class);
    private final FileViewEventRepository viewEventRepository = mock(FileViewEventRepository.class);
    private final FavoriteRepository favoriteRepository = mock(FavoriteRepository.class);
    private final FileRatingRepository fileRatingRepository = mock(FileRatingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserDailyActivityRepository activityRepository = mock(UserDailyActivityRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final HashtagRepository hashtagRepository = mock(HashtagRepository.class);
    private final SuggestionRepository suggestionRepository = mock(SuggestionRepository.class);
    private final WorkshopProjectRepository workshopProjectRepository = mock(WorkshopProjectRepository.class);

    private final AnalyticsServiceImpl service = new AnalyticsServiceImpl(
            studyFileRepository, downloadEventRepository, viewEventRepository, favoriteRepository,
            fileRatingRepository, userRepository, activityRepository, categoryRepository, hashtagRepository,
            suggestionRepository, workshopProjectRepository,
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        // Mockito answers null for a Page; the dashboard always asks for the highest rated files
        when(studyFileRepository.findHighestRatedFiles(anyInt(), any())).thenReturn(Page.empty());
    }

    @Test
    void seriesFillsEmptyPeriodsOldestFirst() {
        List<StatPointDTO> points = AnalyticsServiceImpl.series(
                List.of(new Bucket(0, 4L), new Bucket(2, 1L)), TODAY, 7, 3);

        assertEquals(List.of(1L, 0L, 4L), points.stream().map(StatPointDTO::getValue).toList());
        // Bucket 2 covers the 7 days ending two weeks before today
        assertEquals(TODAY.minusDays(20), points.get(0).getPeriodStart());
        assertEquals(TODAY.minusDays(6), points.get(2).getPeriodStart());
    }

    @Test
    void seriesIgnoresBucketsOutsideTheChart() {
        List<StatPointDTO> points = AnalyticsServiceImpl.series(
                List.of(new Bucket(-1, 9L), new Bucket(30, 9L), new Bucket(1, 2L)), TODAY, 1, 30);

        assertEquals(30, points.size());
        assertEquals(2L, points.stream().mapToLong(StatPointDTO::getValue).sum());
        assertEquals(TODAY, points.get(29).getPeriodStart());
    }

    @Test
    void risingAddsDownloadsAndViewsAndKeepsOnlyGrowth() {
        List<Map.Entry<Long, long[]>> rising = AnalyticsServiceImpl.rising(
                List.of(new Period(1L, 5L, 1L), new Period(2L, 2L, 2L), new Period(3L, 1L, 4L)),
                List.of(new Period(1L, 1L, 0L), new Period(4L, 3L, 0L)));

        assertEquals(List.of(1L, 4L), rising.stream().map(Map.Entry::getKey).toList());
        assertArrayEquals(new long[]{6, 1}, rising.get(0).getValue());
    }

    @Test
    void risingBreaksTiesOnThisWeekCount() {
        List<Map.Entry<Long, long[]>> rising = AnalyticsServiceImpl.rising(
                List.of(new Period(1L, 2L, 0L), new Period(2L, 7L, 5L)));

        assertEquals(List.of(2L, 1L), rising.stream().map(Map.Entry::getKey).toList());
    }

    @Test
    void categoryActivityListsEveryCategoryBusiestFirst() {
        List<CategoryActivityDTO> categories = AnalyticsServiceImpl.categoryActivity(
                List.of(category(1L, "Neurologie"), category(2L, "Cardiologie"), category(3L, "Anatomie")),
                List.of(new Count(2L, 5L)),
                List.of(new Count(2L, 1L), new Count(1L, 3L)),
                List.of(new Count(1L, 1L)));

        assertEquals(List.of("Cardiologie", "Neurologie", "Anatomie"),
                categories.stream().map(CategoryActivityDTO::getCategoryName).toList());
        assertEquals(3L, categories.get(1).getViews());
        assertEquals(0L, categories.get(2).getDownloads() + categories.get(2).getViews());
    }

    @Test
    void averageRatingIsRoundedAndNullWithoutRatings() {
        assertEquals(4.3, AnalyticsServiceImpl.roundToTenth(4.26));
        assertNull(AnalyticsServiceImpl.roundToTenth(null));
    }

    @Test
    void statisticsUseRollingWeeksEndingToday() {
        LocalDateTime weekStart = LocalDateTime.of(2026, 10, 7, 0, 0);
        LocalDateTime previousWeekStart = LocalDateTime.of(2026, 9, 30, 0, 0);
        LocalDateTime tomorrow = LocalDateTime.of(2026, 10, 14, 0, 0);
        when(userRepository.countByRole(Role.ROLE_USER)).thenReturn(40L);
        when(userRepository.countByRoleCreatedBetween(Role.ROLE_USER, weekStart, tomorrow)).thenReturn(6L);
        when(userRepository.countByRoleCreatedBetween(Role.ROLE_USER, previousWeekStart, weekStart)).thenReturn(3L);
        when(activityRepository.countActiveStudents(LocalDate.of(2026, 10, 7), TODAY)).thenReturn(25L);
        when(activityRepository.countActiveStudents(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 6)))
                .thenReturn(20L);
        when(activityRepository.countActiveStudents(TODAY, TODAY)).thenReturn(9L);
        when(downloadEventRepository.countDownloadsBetween(weekStart, tomorrow)).thenReturn(70L);

        AdminStatisticsDTO stats = service.getStatistics();

        assertEquals(NOW, stats.getGeneratedAt());
        assertEquals(40L, stats.getUsers().getTotalStudents());
        assertEquals(6L, stats.getUsers().getNewStudentsThisWeek());
        assertEquals(3L, stats.getUsers().getNewStudentsPreviousWeek());
        assertEquals(25L, stats.getUsers().getWeeklyActiveStudents());
        assertEquals(20L, stats.getUsers().getWeeklyActiveStudentsPreviousWeek());
        assertEquals(9L, stats.getUsers().getDailyActiveStudents());
        assertEquals(12, stats.getUsers().getWeeklySignUps().size());
        assertEquals(30, stats.getUsage().getDailyDownloads().size());
        assertEquals(70L, stats.getUsage().getDownloadsThisWeek());
    }

    @Test
    void topFilesKeepTheRankingAndPointToTheirReasoning() {
        Reasoning reasoning = Reasoning.builder().id(8L).title("ECG").category(category(5L, "Cardiologie")).build();
        StudyFile first = StudyFile.builder().id(1L).fileName("a.pdf").reasoning(reasoning).build();
        StudyFile second = StudyFile.builder().id(2L).fileName("b.pdf").build();
        when(downloadEventRepository.countByFileSince(any(), any()))
                .thenReturn(List.of(new Count(2L, 9L), new Count(1L, 4L)));
        when(studyFileRepository.findAllById(anyIterable())).thenReturn(List.of(first, second));

        List<FileMetricItemDTO> top = service.getStatistics().getLastWeek().getTopDownloaded();

        assertEquals(List.of(2L, 1L), top.stream().map(FileMetricItemDTO::getFileId).toList());
        assertEquals(9L, top.get(0).getMetricValue());
        assertNull(top.get(0).getCategoryName());
        assertEquals(8L, top.get(1).getReasoningId());
        assertEquals("Cardiologie", top.get(1).getCategoryName());
    }

    @Test
    void trendingFilesCarryBothWeeks() {
        StudyFile file = StudyFile.builder().id(3L).fileName("c.pdf").build();
        when(downloadEventRepository.compareByFile(any(), any())).thenReturn(List.of(new Period(3L, 8L, 2L)));
        when(studyFileRepository.findAllById(anyIterable())).thenReturn(List.of(file));

        List<TrendingItemDTO> files = service.getStatistics().getTrends().getFiles();

        assertEquals(1, files.size());
        assertEquals("c.pdf", files.get(0).getName());
        assertEquals(8L, files.get(0).getThisWeek());
        assertEquals(2L, files.get(0).getPreviousWeek());
    }

    private static Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
