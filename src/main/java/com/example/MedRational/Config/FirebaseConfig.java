package com.example.MedRational.Config;

import com.example.MedRational.Services.Implementations.FcmPushNotificationService;
import com.example.MedRational.Services.Interfaces.PushNotificationService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Push notifications through Firebase Cloud Messaging. FIREBASE_CREDENTIALS is the path to the
 * Firebase service-account key (secret, never committed). Without it, pushes are skipped with a
 * warning so the app still runs, e.g. in CI.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean
    public PushNotificationService pushNotificationService(
            @Value("${FIREBASE_CREDENTIALS:}") String credentialsPath) {
        if (credentialsPath.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS is not set: push notifications are disabled");
            return (topic, message, data) -> log.info("Push disabled, skipped '{}' to topic '{}'", message.title(), topic);
        }
        try (InputStream credentials = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            log.info("Firebase push notifications enabled for project {}", app.getOptions().getProjectId());
            return new FcmPushNotificationService(FirebaseMessaging.getInstance(app));
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't read the Firebase key at FIREBASE_CREDENTIALS=" + credentialsPath, e);
        }
    }
}
