package com.example.MedRational.Config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Picks the environment (dev, uat or prod) before Spring starts, so main can load its
 * .env.{profile} file. The profile comes from --spring.profiles.active=..., then the
 * spring.profiles.active system property, then SPRING_PROFILES_ACTIVE, and defaults to dev.
 */
public final class EnvironmentFiles {

    public static final String DEFAULT_PROFILE = "dev";
    public static final Set<String> PROFILES = Set.of("dev", "uat", "prod");

    private static final String PROFILE_ARG = "--spring.profiles.active=";
    private static final String PROFILE_PROPERTY = "spring.profiles.active";
    private static final String PROFILE_ENV = "SPRING_PROFILES_ACTIVE";

    private EnvironmentFiles() {
    }

    public static String resolveProfile(String[] args, Map<String, String> env, Properties systemProperties) {
        String profile = null;
        for (String arg : args) {
            if (arg.startsWith(PROFILE_ARG)) {
                profile = arg.substring(PROFILE_ARG.length());
            }
        }
        if (isBlank(profile)) {
            profile = systemProperties.getProperty(PROFILE_PROPERTY);
        }
        if (isBlank(profile)) {
            profile = env.get(PROFILE_ENV);
        }
        if (isBlank(profile)) {
            return DEFAULT_PROFILE;
        }

        String normalized = profile.trim().toLowerCase(Locale.ROOT);
        if (!PROFILES.contains(normalized)) {
            throw new IllegalStateException(
                    "Unknown profile '" + profile + "': use one of dev, uat or prod");
        }
        return normalized;
    }

    public static String envFileName(String profile) {
        return ".env." + profile;
    }

    /**
     * Values from the file, minus the keys already set as real environment variables, so a
     * server's own variables always win over a leftover file. Empty values are skipped, so
     * "R2_BUCKET=" keeps the profile's default instead of an empty bucket name.
     */
    public static Map<String, String> valuesToApply(Map<String, String> fileValues, Map<String, String> env) {
        Map<String, String> result = new LinkedHashMap<>();
        fileValues.forEach((key, value) -> {
            if (!env.containsKey(key) && !isBlank(value)) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
