package com.example.MedRational.Security;

import com.example.MedRational.Exceptions.TooManyRequestsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-07T10:00:00Z"));
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setLogin(new RateLimitProperties.Action(
                new RateLimitProperties.Limit(3, Duration.ofMinutes(15)),
                new RateLimitProperties.Limit(5, Duration.ofMinutes(15))));
        properties.setSendCode(new RateLimitProperties.Action(
                new RateLimitProperties.Limit(2, Duration.ofHours(1)),
                new RateLimitProperties.Limit(100, Duration.ofHours(1))));
        limiter = new RateLimiter(properties, clock);
        fromIp("10.0.0.1");
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void allowsUpToTheLimitThenAsksToWaitUntilTheWindowEnds() {
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> limiter.check(RateLimitedAction.LOGIN, "sara@example.com"));
        }
        clock.advance(Duration.ofMinutes(5));

        TooManyRequestsException refused = assertThrows(TooManyRequestsException.class,
                () -> limiter.check(RateLimitedAction.LOGIN, "sara@example.com"));
        assertEquals(Duration.ofMinutes(10).toSeconds(), refused.getRetryAfterSeconds());
    }

    @Test
    void aNewWindowStartsAfterThePeriod() {
        for (int i = 0; i < 3; i++) {
            limiter.check(RateLimitedAction.LOGIN, "sara@example.com");
        }
        clock.advance(Duration.ofMinutes(15));

        assertDoesNotThrow(() -> limiter.check(RateLimitedAction.LOGIN, "sara@example.com"));
    }

    @Test
    void emailsIgnoreCaseAndSpaces() {
        limiter.check(RateLimitedAction.SEND_CODE, "Sara@Example.com");
        limiter.check(RateLimitedAction.SEND_CODE, " sara@example.com ");

        assertThrows(TooManyRequestsException.class,
                () -> limiter.check(RateLimitedAction.SEND_CODE, "SARA@EXAMPLE.COM"));
    }

    @Test
    void targetsAndActionsHaveTheirOwnCounters() {
        limiter.check(RateLimitedAction.SEND_CODE, "sara@example.com");
        limiter.check(RateLimitedAction.SEND_CODE, "sara@example.com");

        assertDoesNotThrow(() -> limiter.check(RateLimitedAction.SEND_CODE, "omar@example.com"));
        assertDoesNotThrow(() -> limiter.check(RateLimitedAction.LOGIN, "sara@example.com"));
    }

    // Trying many emails from one place still hits the per-IP limit
    @Test
    void oneIpCannotTryManyEmails() {
        for (int i = 0; i < 5; i++) {
            limiter.check(RateLimitedAction.LOGIN, "user" + i + "@example.com");
        }

        assertThrows(TooManyRequestsException.class,
                () -> limiter.check(RateLimitedAction.LOGIN, "user5@example.com"));

        fromIp("10.0.0.2");
        assertDoesNotThrow(() -> limiter.check(RateLimitedAction.LOGIN, "user6@example.com"));
    }

    // Outside an HTTP request (no client IP) only the target is counted
    @Test
    void withoutARequestOnlyTheTargetCounts() {
        RequestContextHolder.resetRequestAttributes();
        for (int i = 0; i < 10; i++) {
            limiter.check(RateLimitedAction.LOGIN, "user" + i + "@example.com");
        }

        assertThrows(TooManyRequestsException.class, () -> {
            for (int i = 0; i < 4; i++) {
                limiter.check(RateLimitedAction.LOGIN, "same@example.com");
            }
        });
    }

    @Test
    void finishedWindowsAreForgotten() {
        limiter.check(RateLimitedAction.LOGIN, "sara@example.com");
        assertEquals(2, limiter.trackedKeys()); // the email and the IP

        clock.advance(Duration.ofMinutes(15));
        limiter.evictExpired();

        assertEquals(0, limiter.trackedKeys());
    }

    private static void fromIp(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
