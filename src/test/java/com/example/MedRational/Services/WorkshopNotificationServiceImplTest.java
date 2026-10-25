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
import com.example.MedRational.Services.Interfaces.PushMessage;
import com.example.MedRational.Services.Interfaces.PushNotificationService;
import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkshopNotificationServiceImplTest {

    private static final long PROJECT_ID = 5L;
    private static final String TOPIC = "workshops-uat";

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final PushNotificationService push = mock(PushNotificationService.class);

    private final WorkshopNotificationServiceImpl service =
            new WorkshopNotificationServiceImpl(notificationRepository, mailSender, push, TOPIC);

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

    private PushMessage assertPushed(Consumer<WorkshopProject> notify, NotifType type, String titleStart) {
        notify.accept(project);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(push).sendToTopic(
                eq(TOPIC),
                message.capture(),
                eq(Map.of("projectId", "5", "type", type.name())));
        String title = message.getValue().title();
        assertTrue(title.startsWith(titleStart), title);
        assertTrue(title.endsWith("Suturing"), title);
        assertEquals(List.of("Suturing"), message.getValue().localizationArgs());

        ArgumentCaptor<Notification> stored = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(stored.capture());
        assertEquals(title, stored.getValue().getTitle());
        return message.getValue();
    }

    @Test
    void newProjectIsPushedToEveryone() {
        PushMessage message = assertPushed(service::notifyProjectCreated, NotifType.WORKSHOP_CREATED, "New Project");
        assertEquals("push_created_fcfs", message.localizationKey());
        verifyNoInteractions(mailSender);
    }

    @Test
    void newMotivationProjectUsesItsOwnKey() {
        project.setProjectType(ProjectType.MOTIVATION);

        PushMessage message = assertPushed(service::notifyProjectCreated, NotifType.WORKSHOP_CREATED, "New Project");
        assertEquals("push_created_motivation", message.localizationKey());
    }

    @Test
    void openingReminderIsPushedToEveryone() {
        PushMessage message = assertPushed(service::notifyOpeningSoon, NotifType.WORKSHOP_REMINDER, "First-Come-First-Served Opening in 10 Min");
        assertEquals("push_opening_soon_fcfs", message.localizationKey());
    }

    @Test
    void openingIsPushedToEveryone() {
        PushMessage message = assertPushed(service::notifyRegistrationOpened, NotifType.WORKSHOP_LAUNCH, "Spots Available Now");
        assertEquals("push_opened_fcfs", message.localizationKey());
    }

    @Test
    void closingReminderIsPushedToEveryone() {
        PushMessage message = assertPushed(service::notifyClosingSoon, NotifType.WORKSHOP_REMINDER, "Registration Closing in 10 Min");
        assertEquals("push_closing_soon_fcfs", message.localizationKey());
    }

    @Test
    void closingIsPushedToEveryone() {
        PushMessage message = assertPushed(service::notifyRegistrationClosed, NotifType.WORKSHOP_CLOSED, "Registration Closed");
        assertEquals("push_closed_fcfs", message.localizationKey());
    }

    @Test
    void motivationProjectsUseTheirOwnKeys() {
        project.setProjectType(ProjectType.MOTIVATION);

        PushMessage message = assertPushed(service::notifyRegistrationOpened, NotifType.WORKSHOP_LAUNCH, "Applications Are Open");
        assertEquals("push_opened_motivation", message.localizationKey());
    }

    // The app translates pushes from these keys, so they must reach Android with the project title
    @Test
    void fcmMessageCarriesTheLocalizationKeys() throws IOException {
        PushMessage message = new PushMessage("Spots Available Now: Suturing", "Body", "push_opened_fcfs", List.of("Suturing"));

        String json = GsonFactory.getDefaultInstance()
                .toString(FcmPushNotificationService.buildMessage("workshops", message, Map.of("projectId", "5")));

        assertTrue(json.contains("\"title_loc_key\":\"push_opened_fcfs_title\""), json);
        assertTrue(json.contains("\"title_loc_args\":[\"Suturing\"]"), json);
        assertTrue(json.contains("\"body_loc_key\":\"push_opened_fcfs_body\""), json);
        assertTrue(json.contains("\"body_loc_args\":[\"Suturing\"]"), json);
        // The English text stays as a fallback
        assertTrue(json.contains("\"title\":\"Spots Available Now: Suturing\""), json);
    }

    // A push failure must not roll back the scheduler's status change
    @Test
    void fcmFailureIsLoggedNotThrown() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.send(any(Message.class))).thenThrow(new IllegalStateException("FCM unavailable"));
        FcmPushNotificationService fcm = new FcmPushNotificationService(messaging);

        PushMessage message = new PushMessage("Title", "Body", "push_opened_fcfs", List.of("Suturing"));
        assertDoesNotThrow(() -> fcm.sendToTopic("workshops", message, Map.of("projectId", "5")));
        verify(messaging).send(any(Message.class));
    }

    @Test
    void notificationsStillGoOutWhenPushIsDisabled() {
        PushNotificationService disabled = (topic, message, data) -> { };
        WorkshopNotificationServiceImpl withoutPush =
                new WorkshopNotificationServiceImpl(notificationRepository, mailSender, disabled, TOPIC);

        withoutPush.notifyRegistrationOpened(project);

        verify(notificationRepository).save(any(Notification.class));
    }
}
