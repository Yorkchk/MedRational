package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Services.Interfaces.PushMessage;
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

    private final FirebaseMessaging messaging;

    public FcmPushNotificationService(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public void sendToTopic(String topic, PushMessage push, Map<String, String> data) {
        Message message = buildMessage(topic, push, data);
        try {
            String id = messaging.send(message);
            log.info("Sent push '{}' to topic '{}' ({})", push.title(), topic, id);
        } catch (FirebaseMessagingException | RuntimeException e) {
            log.error("Failed to send push '{}' to topic '{}': {}", push.title(), topic, e.getMessage());
        }
    }

    public static Message buildMessage(String topic, PushMessage push, Map<String, String> data) {
        return Message.builder()
                .setTopic(topic)
                // The notification part is shown by Android itself while the app is closed
                .setNotification(Notification.builder().setTitle(push.title()).setBody(push.body()).build())
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        // The app translates the text from these keys, in the language the user picked
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(push.channelId())
                                .setTitleLocalizationKey(push.titleLocalizationKey())
                                .addAllTitleLocalizationArgs(push.localizationArgs())
                                .setBodyLocalizationKey(push.bodyLocalizationKey())
                                .addAllBodyLocalizationArgs(push.localizationArgs())
                                .build())
                        .build())
                .build();
    }
}
