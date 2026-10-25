package com.example.MedRational.Config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentFilesTest {

    private static final String[] NO_ARGS = {};

    private static Properties props(String profile) {
        Properties properties = new Properties();
        properties.setProperty("spring.profiles.active", profile);
        return properties;
    }

    @Test
    void defaultsToDev() {
        assertEquals("dev", EnvironmentFiles.resolveProfile(NO_ARGS, Map.of(), new Properties()));
    }

    @Test
    void readsTheEnvironmentVariable() {
        assertEquals("uat", EnvironmentFiles.resolveProfile(
                NO_ARGS, Map.of("SPRING_PROFILES_ACTIVE", "uat"), new Properties()));
    }

    @Test
    void systemPropertyWinsOverEnvironmentVariable() {
        assertEquals("prod", EnvironmentFiles.resolveProfile(
                NO_ARGS, Map.of("SPRING_PROFILES_ACTIVE", "uat"), props("prod")));
    }

    @Test
    void argumentWinsOverEverything() {
        assertEquals("uat", EnvironmentFiles.resolveProfile(
                new String[]{"--server.port=9090", "--spring.profiles.active=uat"},
                Map.of("SPRING_PROFILES_ACTIVE", "prod"), props("prod")));
    }

    @Test
    void blankValuesFallThrough() {
        assertEquals("uat", EnvironmentFiles.resolveProfile(
                NO_ARGS, Map.of("SPRING_PROFILES_ACTIVE", "uat"), props("  ")));
    }

    @Test
    void normalizesCaseAndSpaces() {
        assertEquals("prod", EnvironmentFiles.resolveProfile(
                NO_ARGS, Map.of("SPRING_PROFILES_ACTIVE", " PROD "), new Properties()));
    }

    @Test
    void rejectsUnknownProfile() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                EnvironmentFiles.resolveProfile(NO_ARGS, Map.of("SPRING_PROFILES_ACTIVE", "pord"), new Properties()));
        assertEquals("Unknown profile 'pord': use one of dev, uat or prod", error.getMessage());
    }

    @Test
    void fileNamePerProfile() {
        assertEquals(".env.dev", EnvironmentFiles.envFileName("dev"));
        assertEquals(".env.uat", EnvironmentFiles.envFileName("uat"));
        assertEquals(".env.prod", EnvironmentFiles.envFileName("prod"));
    }

    @Test
    void realEnvironmentVariablesWinOverTheFile() {
        Map<String, String> applied = EnvironmentFiles.valuesToApply(
                Map.of("R2_BUCKET", "from-file", "JWT_SECRET", "file-secret"),
                Map.of("R2_BUCKET", "from-server"));

        assertEquals(Map.of("JWT_SECRET", "file-secret"), applied);
    }

    // An empty line from the template must not replace the profile's default with ""
    @Test
    void emptyValuesKeepTheProfileDefaults() {
        Map<String, String> applied = EnvironmentFiles.valuesToApply(
                Map.of("R2_BUCKET", "", "SUPABASE_DB_HOST", "  ", "MAIL_PORT", "587"),
                Map.of());

        assertEquals(Map.of("MAIL_PORT", "587"), applied);
    }
}
