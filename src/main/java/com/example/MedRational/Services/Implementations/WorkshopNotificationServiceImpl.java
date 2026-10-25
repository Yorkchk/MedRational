package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Entities.Notification;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.WorkshopProject;
import com.example.MedRational.Entities.enums.NotifType;
import com.example.MedRational.Entities.enums.ProjectType;
import com.example.MedRational.Repositories.NotificationRepository;
import com.example.MedRational.Services.Interfaces.PushMessage;
import com.example.MedRational.Services.Interfaces.PushNotificationService;
import com.example.MedRational.Services.Interfaces.WorkshopNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WorkshopNotificationServiceImpl implements WorkshopNotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final PushNotificationService pushNotificationService;
    // "workshops" in prod, which the app subscribes to; dev and uat use their own topic
    private final String workshopsTopic;

    public WorkshopNotificationServiceImpl(NotificationRepository notificationRepository,
                                           JavaMailSender mailSender,
                                           PushNotificationService pushNotificationService,
                                           @Value("${push.workshops-topic}") String workshopsTopic) {
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
        this.pushNotificationService = pushNotificationService;
        this.workshopsTopic = workshopsTopic;
    }

    // Announces a new project while it's still upcoming. The author just created it, so no email
    @Override
    @Transactional
    public void notifyProjectCreated(WorkshopProject project) {
        boolean isFcfs = project.getProjectType() == ProjectType.FIRST_COME_FIRST_SERVED;

        String title = "New Project: " + project.getTitle();

        String body = isFcfs
                ? "'" + project.getTitle() + "' is coming up. Registration opens soon, check the dates in the app."
                : "Applications for '" + project.getTitle() + "' open soon. Check the dates and prepare your motivation letter.";

        broadcast(project, title, body, "created", NotifType.WORKSHOP_CREATED);
    }

    @Override
    @Transactional
    public void notifyOpeningSoon(WorkshopProject project) {
        boolean isFcfs = project.getProjectType() == ProjectType.FIRST_COME_FIRST_SERVED;

        String title = isFcfs
                ? "First-Come-First-Served Opening in 10 Min: " + project.getTitle()
                : "Application Submissions Open in 10 Min: " + project.getTitle();

        String body = isFcfs
                ? "Spots are limited! Registration for '" + project.getTitle() + "' opens in 10 minutes."
                : "Applications for '" + project.getTitle() + "' open in 10 minutes. Prepare your submission.";

        broadcast(project, title, body, "opening_soon", NotifType.WORKSHOP_REMINDER);
        sendAuthorEmail(project, title, body);
    }

    @Override
    @Transactional
    public void notifyRegistrationOpened(WorkshopProject project) {
        boolean isFcfs = project.getProjectType() == ProjectType.FIRST_COME_FIRST_SERVED;

        String title = isFcfs
                ? "Spots Available Now: " + project.getTitle()
                : "Applications Are Open: " + project.getTitle();

        String body = isFcfs
                ? "First-come, first-served registration is officially OPEN for '" + project.getTitle() + "'! Secure your spot."
                : "Submissions for '" + project.getTitle() + "' are now open. Complete your motivation submission before the deadline.";

        broadcast(project, title, body, "opened", NotifType.WORKSHOP_LAUNCH);
        sendAuthorEmail(project, title, body);
    }

    @Override
    @Transactional
    public void notifyClosingSoon(WorkshopProject project) {
        boolean isFcfs = project.getProjectType() == ProjectType.FIRST_COME_FIRST_SERVED;

        String title = isFcfs
                ? "Registration Closing in 10 Min: " + project.getTitle()
                : "Deadline Alert: 10 Minutes Left for " + project.getTitle();

        String body = isFcfs
                ? "Registration for '" + project.getTitle() + "' is wrapping up and closes in 10 minutes."
                : "The application deadline for '" + project.getTitle() + "' is in 10 minutes. Submit your motivation form now.";

        broadcast(project, title, body, "closing_soon", NotifType.WORKSHOP_REMINDER);
        sendAuthorEmail(project, title, body);
    }

    @Override
    @Transactional
    public void notifyRegistrationClosed(WorkshopProject project) {
        boolean isFcfs = project.getProjectType() == ProjectType.FIRST_COME_FIRST_SERVED;

        String title = isFcfs
                ? "Registration Closed: " + project.getTitle()
                : "Applications Closed: " + project.getTitle();

        String body = isFcfs
                ? "Registration for '" + project.getTitle() + "' has ended."
                : "Application period for '" + project.getTitle() + "' is now closed. Submissions are under review.";

        broadcast(project, title, body, "closed", NotifType.WORKSHOP_CLOSED);
        sendAuthorEmail(project, title, body);
    }

    // Stored for everyone and pushed to every signed-in phone (the app subscribes to the topic)
    private void broadcast(WorkshopProject project, String title, String body, String event, NotifType type) {
        saveBroadcastNotification(title, body, project.getId(), type);
        pushNotificationService.sendToTopic(
                workshopsTopic,
                new PushMessage(title, body, pushLocalizationKey(event, project.getProjectType()), List.of(project.getTitle())),
                Map.of("projectId", String.valueOf(project.getId()), "type", type.name()));
    }

    /**
     * "push_opened_fcfs", ... The app has a "_title" and a "_body" string for each, in French and
     * English, so these keys must match its res/values/push_strings.xml.
     */
    private static String pushLocalizationKey(String event, ProjectType projectType) {
        String type = projectType == ProjectType.FIRST_COME_FIRST_SERVED ? "fcfs" : "motivation";
        return "push_" + event + "_" + type;
    }

    private void saveBroadcastNotification(String title, String body, Long projectId, NotifType type) {
        Notification notification = Notification.builder()
                .user(null) // null = global broadcast
                .title(title)
                .body(body)
                .type(type)
                .targetId(projectId)
                .isRead(false)
                .build();

        notificationRepository.save(notification);
    }

    private void sendAuthorEmail(WorkshopProject project, String subject, String body) {
        User author = project.getAuthor();
        if (author != null && author.getEmail() != null) {
            try {
                SimpleMailMessage email = new SimpleMailMessage();
                email.setTo(author.getEmail());
                email.setSubject("[MedRational] " + subject);
                email.setText(body);
                mailSender.send(email);
            } catch (Exception e) {
                log.error("Failed to send workshop email alert to {}: {}", author.getEmail(), e.getMessage());
            }
        }
    }
}