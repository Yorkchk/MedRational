package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Entities.Notification;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.WorkshopProject;
import com.example.MedRational.Entities.enums.NotifType;
import com.example.MedRational.Entities.enums.ProjectType;
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
        boolean isFcfs = project.getProjectType() == ProjectType.FIRST_COME_FIRST_SERVED;

        String title = isFcfs
                ? "First-Come-First-Served Opening in 10 Min: " + project.getTitle()
                : "Application Submissions Open in 10 Min: " + project.getTitle();

        String body = isFcfs
                ? "Spots are limited! Registration for '" + project.getTitle() + "' opens in 10 minutes."
                : "Applications for '" + project.getTitle() + "' open in 10 minutes. Prepare your submission.";

        saveBroadcastNotification(title, body, project.getId(), NotifType.WORKSHOP_REMINDER);
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

        saveBroadcastNotification(title, body, project.getId(), NotifType.WORKSHOP_LAUNCH);
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

        saveBroadcastNotification(title, body, project.getId(), NotifType.WORKSHOP_REMINDER);
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

        saveBroadcastNotification(title, body, project.getId(), NotifType.WORKSHOP_CLOSED);
        sendAuthorEmail(project, title, body);
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