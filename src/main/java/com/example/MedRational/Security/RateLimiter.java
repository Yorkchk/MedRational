package com.example.MedRational.Security;

import com.example.MedRational.Exceptions.TooManyRequestsException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counters for the auth and code endpoints, against password guessing, code guessing
 * and email flooding. Each call counts once for its target (an email, or "user:{id}") and once for
 * the client IP; over either limit, it throws {@link TooManyRequestsException} (429).
 *
 * The counters live in memory: there is one API instance per environment, and a restart only
 * resets them. Behind Caddy, the client IP comes from X-Forwarded-For
 * (server.forward-headers-strategy=native), which clients can't fake because port 8080 isn't public.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    // Counts this request for the target and the current client IP
    public void check(RateLimitedAction action, String target) {
        RateLimitProperties.Action limits = action.limitsIn(properties);
        String normalized = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);

        long targetWait = hit(action + ":target:" + normalized, limits.getTarget());
        String ip = currentClientIp();
        long ipWait = ip == null ? 0 : hit(action + ":ip:" + ip, limits.getIp());

        long wait = Math.max(targetWait, ipWait);
        if (wait > 0) {
            throw new TooManyRequestsException(wait);
        }
    }

    // Seconds to wait when over the limit, 0 when allowed
    private long hit(String key, RateLimitProperties.Limit limit) {
        long now = clock.millis();
        long periodMillis = limit.getPeriod().toMillis();
        Window window = windows.compute(key, (k, current) ->
                current == null || current.endsAt() <= now
                        ? new Window(now + periodMillis, 1)
                        : new Window(current.endsAt(), current.count() + 1));
        if (window.count() <= limit.getLimit()) {
            return 0;
        }
        return Math.max(1, (window.endsAt() - now + 999) / 1000);
    }

    // Forgets finished windows, so the map doesn't grow with every email ever tried
    @Scheduled(fixedDelayString = "PT10M")
    public void evictExpired() {
        long now = clock.millis();
        windows.values().removeIf(window -> window.endsAt() <= now);
    }

    int trackedKeys() {
        return windows.size();
    }

    private static String currentClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getRemoteAddr();
        }
        return null;
    }

    private record Window(long endsAt, int count) {
    }
}
