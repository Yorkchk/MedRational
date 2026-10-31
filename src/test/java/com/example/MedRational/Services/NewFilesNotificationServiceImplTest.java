package com.example.MedRational.Services;

import com.example.MedRational.Entities.Notification;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.enums.NotifType;
import com.example.MedRational.Repositories.NotificationRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Implementations.NewFilesNotificationServiceImpl;
import com.example.MedRational.Services.Interfaces.PushMessage;
import com.example.MedRational.Services.Interfaces.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NewFilesNotificationServiceImplTest {

    private static final String TOPIC = "workshops-uat";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);

    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final PushNotificationService push = mock(PushNotificationService.class);

    private final NewFilesNotificationServiceImpl service =
            new NewFilesNotificationServiceImpl(studyFileRepository, notificationRepository, push, TOPIC);

    private final Reasoning heartFailure = Reasoning.builder().id(7L).title("Heart Failure").build();
    private final Reasoning asthma = Reasoning.builder().id(8L).title("Asthma").build();

    private static StudyFile file(long id, String name, Reasoning reasoning, int minutesAgo) {
        return StudyFile.builder()
                .id(id)
                .fileName(name)
                .reasoning(reasoning)
                .pushPending(true)
                .uploadedAt(NOW.minusMinutes(minutesAgo))
                .build();
    }

    private void pending(StudyFile... files) {
        when(studyFileRepository.findPendingPush()).thenReturn(List.of(files));
    }

    private PushMessage pushed(Map<String, String> data) {
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(push).sendToTopic(eq(TOPIC), message.capture(), eq(data));
        assertEquals(PushMessage.NEW_FILES_CHANNEL, message.getValue().channelId());
        return message.getValue();
    }

    @Test
    void nothingPendingSendsNothing() {
        pending();

        service.announcePendingUploads(NOW);

        verifyNoInteractions(push, notificationRepository);
        verify(studyFileRepository, never()).markPushSent(anyList());
    }

    @Test
    void waitsWhileTheAdminIsStillUploading() {
        pending(file(1, "a.pdf", heartFailure, 5), file(2, "b.pdf", heartFailure, 1));

        service.announcePendingUploads(NOW);

        verify(push, never()).sendToTopic(anyString(), any(), anyMap());
        verify(studyFileRepository, never()).markPushSent(anyList());
    }

    @Test
    void longUploadSessionIsAnnouncedWithoutWaitingForItsEnd() {
        pending(file(1, "a.pdf", heartFailure, 15), file(2, "b.pdf", heartFailure, 0));

        service.announcePendingUploads(NOW);

        verify(push).sendToTopic(eq(TOPIC), any(PushMessage.class), anyMap());
        verify(studyFileRepository).markPushSent(List.of(1L, 2L));
    }

    @Test
    void oneFileOpensItsPage() {
        pending(file(1, "ECG.pdf", heartFailure, 2));

        service.announcePendingUploads(NOW);

        PushMessage message = pushed(Map.of("type", "NEW_FILES", "fileId", "1", "reasoningId", "7"));
        assertEquals("push_new_file", message.localizationKey());
        assertEquals(List.of("ECG.pdf", "Heart Failure"), message.localizationArgs());
        assertEquals("New file: ECG.pdf", message.title());
        verify(studyFileRepository).markPushSent(List.of(1L));
    }

    @Test
    void severalFilesInOneReasoningAreOnePush() {
        pending(file(1, "a.pdf", heartFailure, 6), file(2, "b.pdf", heartFailure, 4), file(3, "c.pdf", heartFailure, 3));

        service.announcePendingUploads(NOW);

        PushMessage message = pushed(Map.of("type", "NEW_FILES"));
        assertEquals("push_new_files_reasoning", message.localizationKey());
        assertEquals(List.of("3", "Heart Failure"), message.localizationArgs());
        verify(studyFileRepository).markPushSent(List.of(1L, 2L, 3L));
    }

    @Test
    void filesAcrossReasoningsAreCounted() {
        pending(file(1, "a.pdf", heartFailure, 6), file(2, "b.pdf", asthma, 3));

        service.announcePendingUploads(NOW);

        PushMessage message = pushed(Map.of("type", "NEW_FILES"));
        assertEquals("push_new_files", message.localizationKey());
        assertEquals(List.of("2"), message.localizationArgs());
    }

    @Test
    void announcementIsStoredAsABroadcast() {
        pending(file(1, "ECG.pdf", heartFailure, 2));

        service.announcePendingUploads(NOW);

        ArgumentCaptor<Notification> stored = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(stored.capture());
        assertEquals(NotifType.NEW_FILES, stored.getValue().getType());
        assertEquals(1L, stored.getValue().getTargetId());
        assertEquals("New file: ECG.pdf", stored.getValue().getTitle());
        assertNull(stored.getValue().getUser());
    }
}
