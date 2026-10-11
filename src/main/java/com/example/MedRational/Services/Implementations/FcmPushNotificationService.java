package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Services.Interfaces.PushNotificationService;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/** Sends through FCM; created by FirebaseConfig when credentials are configured. */
@Slf4j
public class FcmPushNotificationService implements PushNotificationService {

    // Must match the channel the Android app creates
    static final String ANDROID_CHANNEL_ID = "workshop_reminders";

    private final FirebaseMessaging messaging;

    public FcmPushNotificationService(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public void sendToTopic(String topic, String title, String body, Map<String, String> data) {
        Message message = buildMessage(topic, title, body, data);
        try {
            String id = messaging.send(message);
            log.info("Sent push '{}' to topic '{}' ({})", title, topic, id);
        } catch (FirebaseMessagingException | RuntimeException e) {
            log.error("Failed to send push '{}' to topic '{}': {}", title, topic, e.getMessage());
        }
    }

    static Message buildMessage(String topic, String title, String body, Map<String, String> data) {
        return Message.builder()
                .setTopic(topic)
                // The notification part is shown by Android itself while the app is closed
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder().setChannelId(ANDROID_CHANNEL_ID).build())
                        .build())
                .build();
    }
}
