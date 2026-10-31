package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Entities.Notification;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.enums.NotifType;
import com.example.MedRational.Repositories.NotificationRepository;
import com.example.MedRational.Services.Interfaces.NewFilesNotificationService;
import com.example.MedRational.Services.Interfaces.PushMessage;
import com.example.MedRational.Services.Interfaces.PushNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Pushes as soon as files are uploaded: one push per upload request, so a batch upload
 * (/upload-batch) is announced once for all its files.
 */
@Service
public class NewFilesNotificationServiceImpl implements NewFilesNotificationService {

    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;
    // The topic every phone of this environment subscribes to, workshop pushes included
    private final String topic;

    public NewFilesNotificationServiceImpl(NotificationRepository notificationRepository,
                                           PushNotificationService pushNotificationService,
                                           @Value("${push.workshops-topic}") String topic) {
        this.notificationRepository = notificationRepository;
        this.pushNotificationService = pushNotificationService;
        this.topic = topic;
    }

    @Override
    @Transactional
    public void announce(List<StudyFile> files) {
        if (files.isEmpty()) {
            return;
        }

        Announcement announcement = announcementFor(files);
        notificationRepository.save(Notification.builder()
                .user(null) // null = global broadcast
                .title(announcement.push().title())
                .body(announcement.push().body())
                .type(NotifType.NEW_FILES)
                .targetId(announcement.fileId())
                .isRead(false)
                .build());
        // Never throws (PushNotificationService), so a failed push can't fail the upload
        pushNotificationService.sendToTopic(topic, announcement.push(), announcement.data());
    }

    /**
     * "push_new_file", "push_new_files_reasoning" and "push_new_files" have a "_title" and a "_body"
     * string in the app (res/values/push_strings.xml), in French and English, filled with these args.
     * One file opens its page; several open the app's What's new screen.
     */
    static Announcement announcementFor(List<StudyFile> files) {
        String type = NotifType.NEW_FILES.name();
        if (files.size() == 1) {
            StudyFile file = files.get(0);
            Reasoning reasoning = file.getReasoning();
            PushMessage push = new PushMessage(
                    "New file: " + file.getFileName(),
                    "In " + reasoning.getTitle(),
                    "push_new_file",
                    List.of(file.getFileName(), reasoning.getTitle()),
                    PushMessage.NEW_FILES_CHANNEL);
            Map<String, String> data = Map.of(
                    "type", type,
                    "fileId", String.valueOf(file.getId()),
                    "reasoningId", String.valueOf(reasoning.getId()));
            return new Announcement(push, data, file.getId());
        }

        String count = String.valueOf(files.size());
        List<Long> reasoningIds = files.stream().map(f -> f.getReasoning().getId()).distinct().toList();
        PushMessage push;
        if (reasoningIds.size() == 1) {
            String reasoning = files.get(0).getReasoning().getTitle();
            push = new PushMessage(
                    count + " new files in " + reasoning,
                    "Open the app to see what's new.",
                    "push_new_files_reasoning",
                    List.of(count, reasoning),
                    PushMessage.NEW_FILES_CHANNEL);
        } else {
            push = new PushMessage(
                    count + " new files",
                    "Open the app to see what's new.",
                    "push_new_files",
                    List.of(count),
                    PushMessage.NEW_FILES_CHANNEL);
        }
        return new Announcement(push, Map.of("type", type), null);
    }

    record Announcement(PushMessage push, Map<String, String> data, Long fileId) {
    }
}
