package com.example.MedRational.Services;

import com.example.MedRational.Entities.FileDownloadEvent;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.UserFileDownload;
import com.example.MedRational.Repositories.FileDownloadEventRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.UserFileDownloadRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Services.Implementations.DownloadTrackingServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadTrackingServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long FILE_ID = 42L;

    private final UserFileDownloadRepository userFileDownloadRepository = mock(UserFileDownloadRepository.class);
    private final FileDownloadEventRepository fileDownloadEventRepository = mock(FileDownloadEventRepository.class);
    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final DownloadTrackingServiceImpl service = new DownloadTrackingServiceImpl(
            userFileDownloadRepository, fileDownloadEventRepository, studyFileRepository, userRepository, 10, 50);

    private final User user = User.builder().id(USER_ID).email("student@example.com").build();
    private final StudyFile file = StudyFile.builder().id(FILE_ID).fileName("ecg.pdf").build();

    @BeforeEach
    void setUp() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(studyFileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
        when(userFileDownloadRepository.findByUserIdOrderByDownloadedAtDesc(any(), any())).thenReturn(List.of());
    }

    private void assertQueriedWithLimit(Integer requested, int expected) {
        service.getRecentDownloadsGlimpse(USER_ID, requested);
        verify(userFileDownloadRepository).findByUserIdOrderByDownloadedAtDesc(USER_ID, PageRequest.of(0, expected));
    }

    @Test
    void missingLimitUsesTheConfiguredDefault() {
        assertQueriedWithLimit(null, 10);
    }

    @Test
    void requestedLimitIsUsed() {
        assertQueriedWithLimit(3, 3);
    }

    @Test
    void zeroLimitIsRaisedToOne() {
        assertQueriedWithLimit(0, 1);
    }

    @Test
    void negativeLimitIsRaisedToOne() {
        assertQueriedWithLimit(-5, 1);
    }

    @Test
    void hugeLimitIsCappedAtTheMax() {
        assertQueriedWithLimit(1000, 50);
    }

    @Test
    void defaultLimitComesFromConfiguration() {
        DownloadTrackingServiceImpl configured = new DownloadTrackingServiceImpl(
                userFileDownloadRepository, fileDownloadEventRepository, studyFileRepository, userRepository, 25, 50);

        configured.getRecentDownloadsGlimpse(USER_ID, null);

        verify(userFileDownloadRepository).findByUserIdOrderByDownloadedAtDesc(USER_ID, PageRequest.of(0, 25));
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.getRecentDownloadsGlimpse(99L, null));
    }

    @Test
    void recentDownloadsKeepTheRepositoryOrder() {
        StudyFile older = StudyFile.builder().id(7L).fileName("old.pdf").build();
        when(userFileDownloadRepository.findByUserIdOrderByDownloadedAtDesc(any(), any())).thenReturn(List.of(
                UserFileDownload.builder().user(user).file(file).downloadedAt(LocalDateTime.now()).build(),
                UserFileDownload.builder().user(user).file(older).downloadedAt(LocalDateTime.now().minusDays(1)).build()
        ));

        List<Long> ids = service.getRecentDownloadsGlimpse(USER_ID, null).stream().map(d -> d.getFileId()).toList();

        assertEquals(List.of(FILE_ID, 7L), ids);
    }

    @Test
    void firstDownloadCreatesOneHistoryRow() {
        when(userFileDownloadRepository.findByUserIdAndFileId(USER_ID, FILE_ID)).thenReturn(Optional.empty());

        service.recordUserDownload(USER_ID, FILE_ID);

        ArgumentCaptor<UserFileDownload> saved = ArgumentCaptor.forClass(UserFileDownload.class);
        verify(userFileDownloadRepository).save(saved.capture());
        assertSame(user, saved.getValue().getUser());
        assertSame(file, saved.getValue().getFile());
    }

    @Test
    void redownloadUpdatesTheExistingRowInsteadOfAddingADuplicate() {
        LocalDateTime earlier = LocalDateTime.now().minusDays(3);
        UserFileDownload existing = UserFileDownload.builder().id(5L).user(user).file(file).downloadedAt(earlier).build();
        when(userFileDownloadRepository.findByUserIdAndFileId(USER_ID, FILE_ID)).thenReturn(Optional.of(existing));

        service.recordUserDownload(USER_ID, FILE_ID);

        ArgumentCaptor<UserFileDownload> saved = ArgumentCaptor.forClass(UserFileDownload.class);
        verify(userFileDownloadRepository).save(saved.capture());
        assertSame(existing, saved.getValue());
        assertTrue(existing.getDownloadedAt().isAfter(earlier));
    }

    @Test
    void downloadEventIsAttributedToTheUser() {
        when(userFileDownloadRepository.findByUserIdAndFileId(USER_ID, FILE_ID)).thenReturn(Optional.empty());

        service.recordUserDownload(USER_ID, FILE_ID);

        ArgumentCaptor<FileDownloadEvent> event = ArgumentCaptor.forClass(FileDownloadEvent.class);
        verify(fileDownloadEventRepository).save(event.capture());
        assertSame(user, event.getValue().getUser());
        assertSame(file, event.getValue().getFile());
    }

    @Test
    void anonymousDownloadOnlyLogsTheEvent() {
        service.recordUserDownload(null, FILE_ID);

        verify(fileDownloadEventRepository).save(any());
        verify(userFileDownloadRepository, never()).save(any());
    }
}
