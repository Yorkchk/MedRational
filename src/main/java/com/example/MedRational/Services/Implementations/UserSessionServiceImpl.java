package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.UserSession;
import com.example.MedRational.Repositories.UserSessionRepository;
import com.example.MedRational.Services.Interfaces.UserSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
public class UserSessionServiceImpl implements UserSessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserSessionRepository sessionRepository;
    private final Clock clock;
    private final long lifetimeDays;

    public UserSessionServiceImpl(UserSessionRepository sessionRepository,
                                  Clock clock,
                                  @Value("${jwt.refresh-expiration-days:60}") long lifetimeDays) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.lifetimeDays = lifetimeDays;
    }

    @Override
    @Transactional
    public String start(User user) {
        String token = newToken();
        LocalDateTime now = LocalDateTime.now(clock);
        sessionRepository.save(UserSession.builder()
                .user(user)
                .tokenHash(hash(token))
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(now.plusDays(lifetimeDays))
                .build());
        return token;
    }

    @Override
    @Transactional
    public Renewed renew(String refreshToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        UserSession session = sessionRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new BadCredentialsException("Session expired. Please sign in again."));
        if (session.getExpiresAt().isBefore(now)) {
            sessionRepository.delete(session);
            throw new BadCredentialsException("Session expired. Please sign in again.");
        }

        // Same row, new token: the old one stops working right away
        String token = newToken();
        session.setTokenHash(hash(token));
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plusDays(lifetimeDays));
        sessionRepository.save(session);
        return new Renewed(session.getUser(), token);
    }

    @Override
    @Transactional
    public void end(String refreshToken) {
        sessionRepository.deleteByTokenHash(hash(refreshToken));
    }

    @Override
    @Transactional
    public void endAll(User user) {
        sessionRepository.deleteAllByUserId(user.getId());
    }

    // Every night at 3:00, so devices unused for the whole lifetime don't stay in the table
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanUpExpiredSessions() {
        deleteExpired();
    }

    @Override
    @Transactional
    public int deleteExpired() {
        int deleted = sessionRepository.deleteExpired(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("Deleted {} expired sessions", deleted);
        }
        return deleted;
    }

    // 256 random bits, URL-safe so the app can send it as is
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
