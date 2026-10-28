package com.example.MedRational.Services;

import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.UserSession;
import com.example.MedRational.Repositories.UserSessionRepository;
import com.example.MedRational.Services.Implementations.UserSessionServiceImpl;
import com.example.MedRational.Services.Interfaces.UserSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSessionServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final UserSessionRepository repository = mock(UserSessionRepository.class);
    private final UserSessionService service = new UserSessionServiceImpl(repository, CLOCK, 60);

    private final User user = User.builder().id(1L).email("sara@example.com").role(Role.ROLE_USER).build();

    private static String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private UserSession session(String token, LocalDateTime expiresAt) throws Exception {
        UserSession session = UserSession.builder().id(5L).user(user).tokenHash(sha256(token))
                .createdAt(NOW.minusDays(10)).lastUsedAt(NOW.minusDays(1)).expiresAt(expiresAt).build();
        when(repository.findByTokenHash(sha256(token))).thenReturn(Optional.of(session));
        return session;
    }

    // Only the hash is stored, so a database leak can't sign anyone in
    @Test
    void startingStoresOnlyTheHashForSixtyDays() throws Exception {
        String token = service.start(user);

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(repository).save(saved.capture());
        assertEquals(sha256(token), saved.getValue().getTokenHash());
        assertNotEquals(token, saved.getValue().getTokenHash());
        assertSame(user, saved.getValue().getUser());
        assertEquals(NOW.plusDays(60), saved.getValue().getExpiresAt());
        assertTrue(token.length() >= 43, "256 random bits");
    }

    @Test
    void eachSessionGetsItsOwnToken() {
        assertNotEquals(service.start(user), service.start(user));
    }

    // One row per device: renewing updates it instead of adding rows
    @Test
    void renewingReplacesTheTokenOnTheSameRowAndExtendsIt() throws Exception {
        UserSession session = session("old-token", NOW.plusDays(3));

        UserSessionService.Renewed renewed = service.renew("old-token");

        assertSame(user, renewed.user());
        assertNotEquals("old-token", renewed.refreshToken());
        assertEquals(sha256(renewed.refreshToken()), session.getTokenHash());
        assertEquals(NOW.plusDays(60), session.getExpiresAt());
        assertEquals(NOW, session.getLastUsedAt());
        assertEquals(5L, session.getId());
        verify(repository).save(session);
    }

    // After a renewal the old token matches nothing, so it's refused like any unknown token
    @Test
    void anUnknownOrReplacedTokenIsRefused() throws Exception {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> service.renew("old-token"));
        verify(repository, never()).save(any());
    }

    @Test
    void anExpiredSessionIsRefusedAndDeleted() throws Exception {
        UserSession session = session("old-token", NOW.minusSeconds(1));

        assertThrows(BadCredentialsException.class, () -> service.renew("old-token"));
        verify(repository).delete(session);
        verify(repository, never()).save(any());
    }

    @Test
    void signingOutDeletesThisDevicesSession() throws Exception {
        service.end("token");

        verify(repository).deleteByTokenHash(sha256("token"));
    }

    @Test
    void endingAllDeletesEveryDeviceOfTheUser() {
        service.endAll(user);

        verify(repository).deleteAllByUserId(1L);
    }

    @Test
    void cleanupDeletesSessionsPastTheirExpiry() {
        when(repository.deleteExpired(NOW)).thenReturn(3);

        assertEquals(3, service.deleteExpired());
    }
}
