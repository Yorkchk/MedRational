package com.example.MedRational.Services;

import com.example.MedRational.Entities.Notification;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.WorkshopProject;
import com.example.MedRational.Entities.enums.NotifType;
import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Entities.enums.ProjectType;
import com.example.MedRational.Repositories.NotificationRepository;
import com.example.MedRational.Services.Implementations.FcmPushNotificationService;
import com.example.MedRational.Services.Implementations.WorkshopNotificationServiceImpl;
import com.example.MedRational.Services.Interfaces.PushNotificationService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkshopNotificationServiceImplTest {

    private static final long PROJECT_ID = 5L;

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final PushNotificationService push = mock(PushNotificationService.class);

    private final WorkshopNotificationServiceImpl service =
            new WorkshopNotificationServiceImpl(notificationRepository, mailSender, push);

    private final WorkshopProject project = WorkshopProject.builder()
            .id(PROJECT_ID)
            .author(User.builder().id(1L).email("admin@example.com").build())
            .title("Suturing")
            .description("Hands-on")
            .projectType(ProjectType.FIRST_COME_FIRST_SERVED)
            .status(ProjectStatus.UPCOMING)
            .registrationOpensAt(LocalDateTime.now().plusMinutes(10))
            .registrationClosesAt(LocalDateTime.now().plusDays(1))
            .build();

    private void assertPushed(Consumer<WorkshopProject> notify, NotifType type, String titleStart) {
        notify.accept(project);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(push).sendToTopic(
                eq(PushNotificationService.WORKSHOPS_TOPIC),
                title.capture(),
                anyString(),
                eq(Map.of("projectId", "5", "type", type.name())));
        assertTrue(title.getValue().startsWith(titleStart), title.getValue());
        assertTrue(title.getValue().endsWith("Suturing"), title.getValue());

        ArgumentCaptor<Notification> stored = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(stored.capture());
        assertEquals(title.getValue(), stored.getValue().getTitle());
    }

    @Test
    void openingReminderIsPushedToEveryone() {
        assertPushed(service::notifyOpeningSoon, NotifType.WORKSHOP_REMINDER, "First-Come-First-Served Opening in 10 Min");
    }

    @Test
    void openingIsPushedToEveryone() {
        assertPushed(service::notifyRegistrationOpened, NotifType.WORKSHOP_LAUNCH, "Spots Available Now");
    }

    @Test
    void closingReminderIsPushedToEveryone() {
        assertPushed(service::notifyClosingSoon, NotifType.WORKSHOP_REMINDER, "Registration Closing in 10 Min");
    }

    @Test
    void closingIsPushedToEveryone() {
        assertPushed(service::notifyRegistrationClosed, NotifType.WORKSHOP_CLOSED, "Registration Closed");
    }

    // A push failure must not roll back the scheduler's status change
    @Test
    void fcmFailureIsLoggedNotThrown() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.send(any(Message.class))).thenThrow(new IllegalStateException("FCM unavailable"));
        FcmPushNotificationService fcm = new FcmPushNotificationService(messaging);

        assertDoesNotThrow(() -> fcm.sendToTopic("workshops", "Title", "Body", Map.of("projectId", "5")));
        verify(messaging).send(any(Message.class));
    }

    @Test
    void notificationsStillGoOutWhenPushIsDisabled() {
        PushNotificationService disabled = (topic, title, body, data) -> { };
        WorkshopNotificationServiceImpl withoutPush =
                new WorkshopNotificationServiceImpl(notificationRepository, mailSender, disabled);

        withoutPush.notifyRegistrationOpened(project);

        verify(notificationRepository).save(any(Notification.class));
    }
}
