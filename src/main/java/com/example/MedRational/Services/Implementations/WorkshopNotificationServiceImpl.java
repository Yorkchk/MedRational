package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Entities.Notification;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.WorkshopProject;
import com.example.MedRational.Entities.enums.NotifType;
import com.example.MedRational.Repositories.NotificationRepository;
import com.example.MedRational.Services.Interfaces.WorkshopNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkshopNotificationServiceImpl implements WorkshopNotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Override
    @Transactional
    public void notifyOpeningSoon(WorkshopProject project) {
        String title = "Workshop Opening in 10 Minutes: " + project.getTitle();
        String body = "Registration for '" + project.getTitle() + "' opens in 10 minutes. Get ready!";

        // 1. Broadcast notification to all users (user = null)
        saveBroadcastNotification(title, body, project.getId(), NotifType.WORKSHOP_REMINDER);

        // 2. Alert admin/author via email
        sendAuthorEmail(project, title, body);
    }

    @Override
    @Transactional
    public void notifyRegistrationOpened(WorkshopProject project) {
        String title = "Registration is NOW OPEN: " + project.getTitle();
        String body = "Registration is officially open for '" + project.getTitle() + "'. Secure your spot now!";

        saveBroadcastNotification(title, body, project.getId(), NotifType.WORKSHOP_LAUNCH);
        sendAuthorEmail(project, title, body);
    }

    @Override
    @Transactional
    public void notifyClosingSoon(WorkshopProject project) {
        String title = "10 Minutes Left to Register: " + project.getTitle();
        String body = "Last chance! Registration for '" + project.getTitle() + "' closes in 10 minutes.";

        saveBroadcastNotification(title, body, project.getId(), NotifType.WORKSHOP_REMINDER);
        sendAuthorEmail(project, title, body);
    }

    @Override
    @Transactional
    public void notifyRegistrationClosed(WorkshopProject project) {
        String title = "Registration Closed: " + project.getTitle();
        String body = "Registration for '" + project.getTitle() + "' is now closed.";

        saveBroadcastNotification(title, body, project.getId(), NotifType.WORKSHOP_CLOSED);
        sendAuthorEmail(project, title, body);
    }

    private void saveBroadcastNotification(String title, String body, Long projectId, NotifType type) {
        Notification notification = Notification.builder()
                .user(null) // null represents broadcast across all users
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