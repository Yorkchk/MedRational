package com.example.MedRational.Services;

import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Favorite;
import com.example.MedRational.Entities.FileDownloadEvent;
import com.example.MedRational.Entities.FileRating;
import com.example.MedRational.Entities.FileViewEvent;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.UserFileDownload;
import com.example.MedRational.Repositories.CategoryRepository;
import com.example.MedRational.Repositories.FavoriteRepository;
import com.example.MedRational.Repositories.FileDownloadEventRepository;
import com.example.MedRational.Repositories.FileRatingRepository;
import com.example.MedRational.Repositories.FileViewEventRepository;
import com.example.MedRational.Repositories.ReasoningRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.UserFileDownloadRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Services.Implementations.CategoryServiceImpl;
import com.example.MedRational.Services.Implementations.R2StorageServiceImpl;
import com.example.MedRational.Services.Implementations.ReasoningServiceImpl;
import com.example.MedRational.Services.Implementations.StudyFileServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Against the real database, since only Postgres enforces the foreign keys: a file that was
// downloaded, favorited, rated and viewed must still be deletable on its own, with its reasoning
// or with its category. Before the fix, Postgres refused with
// "violates foreign key constraint ... on table file_download_events".
@SpringBootTest(properties = "preview.office-enabled=false")
class ContentDeletionIntegrationTest {

    @MockitoBean
    private R2StorageServiceImpl r2;

    @Autowired private CategoryServiceImpl categoryService;
    @Autowired private ReasoningServiceImpl reasoningService;
    @Autowired private StudyFileServiceImpl studyFileService;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ReasoningRepository reasoningRepository;
    @Autowired private StudyFileRepository studyFileRepository;
    @Autowired private FileRatingRepository fileRatingRepository;
    @Autowired private FileDownloadEventRepository fileDownloadEventRepository;
    @Autowired private FileViewEventRepository fileViewEventRepository;
    @Autowired private FavoriteRepository favoriteRepository;
    @Autowired private UserFileDownloadRepository userFileDownloadRepository;

    private User student;
    private Category category;
    private Reasoning reasoning;
    private StudyFile file;

    @BeforeEach
    void createUsedFile() {
        LocalDateTime now = LocalDateTime.now();
        String suffix = UUID.randomUUID().toString();
        student = userRepository.save(User.builder()
                .email("deletion-" + suffix + "@example.com")
                .passwordHash("not-used")
                .fullName("Deletion Test")
                .role(Role.ROLE_USER)
                .emailVerified(true)
                .build());
        category = categoryRepository.save(Category.builder().name("Deletion test " + suffix).createdAt(now).build());
        reasoning = reasoningRepository.save(Reasoning.builder().title("Reasoning").category(category).createdAt(now).build());
        file = studyFileRepository.save(StudyFile.builder()
                .fileName("notes.pdf")
                .storageKey("test/" + suffix)
                .publicUrl("https://r2.example/test/" + suffix)
                .reasoning(reasoning)
                .uploadedAt(now)
                .build());

        fileDownloadEventRepository.save(FileDownloadEvent.builder().file(file).user(student).createdAt(now).build());
        userFileDownloadRepository.save(UserFileDownload.builder().file(file).user(student).downloadedAt(now).build());
        favoriteRepository.save(Favorite.builder().file(file).user(student).createdAt(now).build());
        fileRatingRepository.save(FileRating.builder().file(file).user(student).score(4).createdAt(now).build());
        fileViewEventRepository.save(FileViewEvent.builder().file(file).user(student).createdAt(now).build());
    }

    private long rowsPointingAtTheFile(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE file_id = ?", Long.class, file.getId());
    }

    @AfterEach
    void cleanUp() {
        // Removes whatever a failed test left behind, children first
        for (String table : new String[] {"file_download_events", "user_file_downloads", "favorites", "file_ratings", "file_view_events"}) {
            jdbc.update("DELETE FROM " + table + " WHERE file_id = ?", file.getId());
        }
        jdbc.update("DELETE FROM study_files WHERE id = ?", file.getId());
        jdbc.update("DELETE FROM reasonings WHERE id = ?", reasoning.getId());
        jdbc.update("DELETE FROM categories WHERE id = ?", category.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", student.getId());
    }

    private void assertNothingPointsAtTheFile() {
        assertFalse(studyFileRepository.existsById(file.getId()));
        for (String table : new String[] {"file_download_events", "user_file_downloads", "favorites", "file_ratings", "file_view_events"}) {
            assertEquals(0, rowsPointingAtTheFile(table), table);
        }
    }

    @Test
    void deletingACategoryRemovesItsUsedFiles() {
        categoryService.deleteCategory(category.getId());

        assertFalse(categoryRepository.existsById(category.getId()));
        assertFalse(reasoningRepository.existsById(reasoning.getId()));
        assertNothingPointsAtTheFile();
    }

    @Test
    void deletingAReasoningRemovesItsUsedFiles() {
        reasoningService.deleteReasoning(reasoning.getId());

        assertFalse(reasoningRepository.existsById(reasoning.getId()));
        assertNothingPointsAtTheFile();
    }

    @Test
    void deletingAUsedFileWorks() {
        studyFileService.deleteFile(file.getId());

        assertNothingPointsAtTheFile();
    }
}
