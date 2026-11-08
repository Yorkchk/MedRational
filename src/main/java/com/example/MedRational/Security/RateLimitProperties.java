package com.example.MedRational.Security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Limits for the endpoints that check a password or send/check an emailed code (application.yaml,
// rate-limit.*). Each action has two counters: one per target (the email, or the signed-in user)
// against guessing and email flooding, and a looser one per client IP, since students share
// campus Wi-Fi.
@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private Action login = new Action(new Limit(10, Duration.ofMinutes(15)), new Limit(60, Duration.ofMinutes(15)));
    private Action sendCode = new Action(new Limit(5, Duration.ofHours(1)), new Limit(30, Duration.ofHours(1)));
    private Action verifyCode = new Action(new Limit(10, Duration.ofMinutes(15)), new Limit(60, Duration.ofMinutes(15)));

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        private Limit target = new Limit();
        private Limit ip = new Limit();
    }

    // At most `limit` requests per `period`, counted from the first request of the window
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Limit {
        private int limit = 10;
        private Duration period = Duration.ofMinutes(15);
    }
}
