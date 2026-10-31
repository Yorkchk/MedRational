package com.example.MedRational.Services;

import com.example.MedRational.Entities.Notification;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.enums.NotifType;
import com.example.MedRational.Repositories.NotificationRepository;
import com.example.MedRational.Services.Implementations.NewFilesNotificationServiceImpl;
import com.example.MedRational.Services.Interfaces.PushMessage;
import com.example.MedRational.Services.Interfaces.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NewFilesNotificationServiceImplTest {

    private static final String TOPIC = "workshops-uat";

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final PushNotificationService push = mock(PushNotificationService.class);

    private final NewFilesNotificationServiceImpl service =
            new NewFilesNotificationServiceImpl(notificationRepository, push, TOPIC);

    private final Reasoning heartFailure = Reasoning.builder().id(7L).title("Heart Failure").build();
    private final Reasoning asthma = Reasoning.builder().id(8L).title("Asthma").build();

    private static StudyFile file(long id, String name, Reasoning reasoning) {
        return StudyFile.builder().id(id).fileName(name).reasoning(reasoning).build();
    }

    private PushMessage pushed(Map<String, String> data) {
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(push).sendToTopic(eq(TOPIC), message.capture(), eq(data));
        assertEquals(PushMessage.NEW_FILES_CHANNEL, message.getValue().channelId());
        return message.getValue();
    }

    @Test
    void noFilesSendsNothing() {
        service.announce(List.of());

        verifyNoInteractions(push, notificationRepository);
    }

    @Test
    void oneFileOpensItsPage() {
        service.announce(List.of(file(1, "ECG.pdf", heartFailure)));

        PushMessage message = pushed(Map.of("type", "NEW_FILES", "fileId", "1", "reasoningId", "7"));
        assertEquals("push_new_file", message.localizationKey());
        assertEquals(List.of("ECG.pdf", "Heart Failure"), message.localizationArgs());
        assertEquals("New file: ECG.pdf", message.title());
    }

    @Test
    void severalFilesInOneReasoningAreOnePush() {
        service.announce(List.of(file(1, "a.pdf", heartFailure), file(2, "b.pdf", heartFailure), file(3, "c.pdf", heartFailure)));

        PushMessage message = pushed(Map.of("type", "NEW_FILES"));
        assertEquals("push_new_files_reasoning", message.localizationKey());
        assertEquals(List.of("3", "Heart Failure"), message.localizationArgs());
    }

    @Test
    void filesAcrossReasoningsAreCounted() {
        service.announce(List.of(file(1, "a.pdf", heartFailure), file(2, "b.pdf", asthma)));

        PushMessage message = pushed(Map.of("type", "NEW_FILES"));
        assertEquals("push_new_files", message.localizationKey());
        assertEquals(List.of("2"), message.localizationArgs());
    }

    @Test
    void announcementIsStoredAsABroadcast() {
        service.announce(List.of(file(1, "ECG.pdf", heartFailure)));

        ArgumentCaptor<Notification> stored = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(stored.capture());
        assertEquals(NotifType.NEW_FILES, stored.getValue().getType());
        assertEquals(1L, stored.getValue().getTargetId());
        assertEquals("New file: ECG.pdf", stored.getValue().getTitle());
        assertNull(stored.getValue().getUser());
    }
}
