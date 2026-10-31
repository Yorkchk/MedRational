package com.example.MedRational.Services.Interfaces;

import java.util.List;

/**
 * The text of a push, shown on the app's Android notification channel {@code channelId}. The app translates it: Android looks up {@code <localizationKey>_title} and
 * {@code <localizationKey>_body} in the app's strings and fills them with {@code localizationArgs},
 * in the language the user picked. {@code title} and {@code body} are the English fallback, shown
 * when the app doesn't know the key.
 */
public record PushMessage(String title, String body, String localizationKey, List<String> localizationArgs,
                          String channelId) {

    // Channels the Android app creates (WorkshopPush.ensureChannel); users can mute each one
    public static final String WORKSHOP_CHANNEL = "workshop_reminders";
    public static final String NEW_FILES_CHANNEL = "new_files";

    /** A workshop push. */
    public PushMessage(String title, String body, String localizationKey, List<String> localizationArgs) {
        this(title, body, localizationKey, localizationArgs, WORKSHOP_CHANNEL);
    }

    public String titleLocalizationKey() {
        return localizationKey + "_title";
    }

    public String bodyLocalizationKey() {
        return localizationKey + "_body";
    }
}
