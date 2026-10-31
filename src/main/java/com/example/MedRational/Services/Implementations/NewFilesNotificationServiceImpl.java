package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Entities.Notification;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.enums.NotifType;
import com.example.MedRational.Repositories.NotificationRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Interfaces.NewFilesNotificationService;
import com.example.MedRational.Services.Interfaces.PushMessage;
import com.example.MedRational.Services.Interfaces.PushNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The app uploads files one at a time, so pushing each upload would send one notification per file.
 * Uploads are marked pending instead, and announced together once the admin has stopped uploading.
 */
@Service
public class NewFilesNotificationServiceImpl implements NewFilesNotificationService {

    // Uploads this close together are announced in one push
    static final Duration QUIET_PERIOD = Duration.ofMinutes(2);
    // A long upload session is still announced, without waiting for its end
    static final Duration MAX_WAIT = Duration.ofMinutes(15);

    private final StudyFileRepository studyFileRepository;
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;
    // The topic every phone of this environment subscribes to, workshop pushes included
    private final String topic;

    public NewFilesNotificationServiceImpl(StudyFileRepository studyFileRepository,
                                           NotificationRepository notificationRepository,
                                           PushNotificationService pushNotificationService,
                                           @Value("${push.workshops-topic}") String topic) {
        this.studyFileRepository = studyFileRepository;
        this.notificationRepository = notificationRepository;
        this.pushNotificationService = pushNotificationService;
        this.topic = topic;
    }

    // Second 30, so it doesn't run together with the workshop scheduler at second 0
    @Scheduled(cron = "30 * * * * *")
    @Transactional
    public void announcePendingUploads() {
        announcePendingUploads(LocalDateTime.now());
    }

    @Override
    @Transactional
    public void announcePendingUploads(LocalDateTime now) {
        // Oldest first
        List<StudyFile> pending = studyFileRepository.findPendingPush();
        if (pending.isEmpty()) {
            return;
        }
        LocalDateTime oldest = pending.get(0).getUploadedAt();
        LocalDateTime newest = pending.get(pending.size() - 1).getUploadedAt();
        boolean stillUploading = newest.isAfter(now.minus(QUIET_PERIOD));
        boolean waitedLongEnough = !oldest.isAfter(now.minus(MAX_WAIT));
        if (stillUploading && !waitedLongEnough) {
            return;
        }

        Announcement announcement = announcementFor(pending);
        notificationRepository.save(Notification.builder()
                .user(null) // null = global broadcast
                .title(announcement.push().title())
                .body(announcement.push().body())
                .type(NotifType.NEW_FILES)
                .targetId(announcement.fileId())
                .isRead(false)
                .build());
        pushNotificationService.sendToTopic(topic, announcement.push(), announcement.data());
        studyFileRepository.markPushSent(pending.stream().map(StudyFile::getId).toList());
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
