package com.example.MedRational.Services.Interfaces;

import java.util.Map;

/**
 * Push notifications to phones through Firebase Cloud Messaging. Phones subscribe to topics
 * themselves; senders only name the topic. Implementations never throw: a failed push is logged.
 */
public interface PushNotificationService {

    // Every signed-in app is subscribed to it
    String WORKSHOPS_TOPIC = "workshops";

    void sendToTopic(String topic, PushMessage message, Map<String, String> data);
}
