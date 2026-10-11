package com.example.MedRational.Services;

import com.example.MedRational.DTOs.NoveltiesFeedResponseDTO;
import com.example.MedRational.DTOs.NoveltyFileItemDTO;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Services.Implementations.NoveltyServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoveltyServiceImplTest {

    private static final LocalDateTime SEEN_AT = LocalDateTime.of(2026, 10, 1, 12, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 9, 30);

    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private final NoveltyServiceImpl service = new NoveltyServiceImpl(studyFileRepository, userRepository, clock);

    private final Pageable page = PageRequest.of(0, 15);

    private StudyFile file(long id, LocalDateTime uploadedAt) {
        return StudyFile.builder().id(id).fileName("file" + id + ".pdf").uploadedAt(uploadedAt).build();
    }

    private List<NoveltyFileItemDTO> feedFor(User user, StudyFile... files) {
        when(studyFileRepository.findAllFilesForNovelties(page)).thenReturn(new PageImpl<>(List.of(files), page, files.length));
        return service.getNoveltiesFeed(user, page).getFeed().getContent();
    }

    @Test
    void onlyFilesUploadedAfterTheBaselineAreNew() {
        User user = User.builder().id(1L).noveltiesSeenAt(SEEN_AT).createdAt(SEEN_AT.minusYears(1)).build();

        List<NoveltyFileItemDTO> feed = feedFor(user, file(2, SEEN_AT.plusHours(1)), file(1, SEEN_AT.minusHours(1)));

        assertTrue(feed.get(0).isNew());
        assertFalse(feed.get(1).isNew());
    }

    // Regression: the baseline used to be lastLoginAt, which signing in overwrites, so nothing
    // was ever new right after a login
    @Test
    void signingInDoesNotResetWhatIsNew() {
        User user = User.builder().id(1L).noveltiesSeenAt(SEEN_AT).lastLoginAt(NOW).createdAt(SEEN_AT.minusYears(1)).build();

        List<NoveltyFileItemDTO> feed = feedFor(user, file(2, SEEN_AT.plusDays(2)));

        assertTrue(feed.get(0).isNew());
    }

    @Test
    void usersWhoNeverOpenedTheFeedCompareAgainstAccountCreation() {
        LocalDateTime createdAt = SEEN_AT;
        User user = User.builder().id(1L).createdAt(createdAt).build();

        List<NoveltyFileItemDTO> feed = feedFor(user, file(2, createdAt.plusMinutes(1)), file(1, createdAt.minusDays(30)));

        assertTrue(feed.get(0).isNew());
        assertFalse(feed.get(1).isNew());
    }

    @Test
    void newFilesCountAndBadgeUseTheBaseline() {
        User user = User.builder().id(1L).noveltiesSeenAt(SEEN_AT).createdAt(SEEN_AT.minusYears(1)).build();
        when(studyFileRepository.countByUploadedAtAfter(SEEN_AT)).thenReturn(3L);
        when(studyFileRepository.findAllFilesForNovelties(any())).thenReturn(new PageImpl<>(List.of()));

        NoveltiesFeedResponseDTO feed = service.getNoveltiesFeed(user, page);

        assertEquals(3L, feed.getNewFilesCount());
        assertEquals(3L, service.getUnreadUploadsCount(user));
    }

    @Test
    void markingAsSeenMovesTheBaselineToNow() {
        User user = User.builder().id(1L).noveltiesSeenAt(SEEN_AT).lastLoginAt(SEEN_AT).build();

        service.markNoveltiesAsSeen(user);

        assertEquals(NOW, user.getNoveltiesSeenAt());
        assertEquals(SEEN_AT, user.getLastLoginAt());
        verify(userRepository).save(user);
    }

    @Test
    void isNewIsSerializedAsIsNew() {
        NoveltyFileItemDTO item = NoveltyFileItemDTO.builder().fileId(1L).isNew(true).build();

        String json = JsonMapper.builder().build().writeValueAsString(item);

        assertTrue(json.contains("\"isNew\":true"), json);
        assertFalse(json.contains("\"new\""), json);
    }
}
