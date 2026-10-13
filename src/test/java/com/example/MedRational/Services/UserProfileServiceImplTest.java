package com.example.MedRational.Services;

import com.example.MedRational.DTOs.AuthResponse;
import com.example.MedRational.DTOs.ChangePasswordRequestDTO;
import com.example.MedRational.DTOs.ConfirmCodeRequestDTO;
import com.example.MedRational.DTOs.RequestEmailChangeDTO;
import com.example.MedRational.DTOs.UpdateProfileRequestDTO;
import com.example.MedRational.DTOs.UserProfileResponseDTO;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Security.JwtUtil;
import com.example.MedRational.Services.Implementations.UserProfileServiceImpl;
import com.example.MedRational.Services.Interfaces.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final EmailService emailService = mock(EmailService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);

    private final UserProfileServiceImpl service =
            new UserProfileServiceImpl(userRepository, passwordEncoder, emailService, jwtUtil, CLOCK);

    private User user() {
        return User.builder()
                .id(1L)
                .email("old@example.com")
                .fullName("Amina Ben Ali")
                .firstName("Amina")
                .lastName("Ben Ali")
                .phoneNumber("0612345678")
                .role(Role.ROLE_USER)
                .build();
    }

    private User withCode(User user, String code, LocalDateTime expiry) {
        user.setOtpCode(code);
        user.setOtpExpiry(expiry);
        return user;
    }

    @Test
    void profileShowsTheStoredNames() {
        UserProfileResponseDTO profile = service.getProfile(user());

        assertEquals("Amina", profile.getFirstName());
        assertEquals("Ben Ali", profile.getLastName());
        assertEquals("old@example.com", profile.getEmail());
        assertEquals("0612345678", profile.getPhoneNumber());
        assertEquals("ROLE_USER", profile.getRole());
    }

    @Test
    void accountsWithOnlyAFullNameAreSplitAtTheFirstSpace() {
        User legacy = User.builder().id(2L).email("a@b.c").fullName("Youssef El Amrani").role(Role.ROLE_USER).build();

        UserProfileResponseDTO profile = service.getProfile(legacy);

        assertEquals("Youssef", profile.getFirstName());
        assertEquals("El Amrani", profile.getLastName());
    }

    @Test
    void updatingTheProfileRebuildsTheFullNameAndClearsABlankPhone() {
        User user = user();

        UserProfileResponseDTO profile =
                service.updateProfile(user, new UpdateProfileRequestDTO(" Sara ", " Idrissi ", ""));

        assertEquals("Sara", user.getFirstName());
        assertEquals("Idrissi", user.getLastName());
        assertEquals("Sara Idrissi", user.getFullName());
        assertNull(user.getPhoneNumber());
        assertEquals("Sara", profile.getFirstName());
        verify(userRepository).save(user);
    }

    @Test
    void passwordCodeIsEmailedToTheCurrentAddress() {
        User user = user();

        service.requestPasswordChangeCode(user);

        assertEquals(6, user.getOtpCode().length());
        assertEquals(NOW.plusMinutes(5), user.getOtpExpiry());
        verify(emailService).sendOtpEmail(eq("old@example.com"), eq(user.getOtpCode()), anyString());
    }

    @Test
    void rightCodeChangesThePasswordAndUsesTheCodeUp() {
        User user = withCode(user(), "123456", NOW.plusMinutes(2));
        when(passwordEncoder.encode("newSecret")).thenReturn("hashed");

        service.changePassword(user, new ChangePasswordRequestDTO("123456", "newSecret"));

        assertEquals("hashed", user.getPasswordHash());
        assertNull(user.getOtpCode());
        assertNull(user.getOtpExpiry());
    }

    @Test
    void wrongCodeIsABadRequestAndKeepsThePassword() {
        User user = withCode(user(), "123456", NOW.plusMinutes(2));
        user.setPasswordHash("old-hash");

        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(user, new ChangePasswordRequestDTO("654321", "newSecret")));

        assertEquals("old-hash", user.getPasswordHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    void expiredCodeIsABadRequest() {
        User user = withCode(user(), "123456", NOW.minusSeconds(1));

        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(user, new ChangePasswordRequestDTO("123456", "newSecret")));
    }

    @Test
    void emailChangeCodeGoesToTheNewAddress() {
        User user = user();
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        service.requestEmailChange(user, new RequestEmailChangeDTO(" New@Example.com "));

        assertEquals("new@example.com", user.getPendingEmail());
        assertEquals("old@example.com", user.getEmail());
        verify(emailService).sendOtpEmail(eq("new@example.com"), eq(user.getOtpCode()), anyString());
    }

    @Test
    void emailAlreadyInUseIsRejected() {
        User user = user();
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.requestEmailChange(user, new RequestEmailChangeDTO("taken@example.com")));

        assertNull(user.getPendingEmail());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString());
    }

    @Test
    void sameEmailIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.requestEmailChange(user(), new RequestEmailChangeDTO("OLD@example.com")));
    }

    @Test
    void confirmingTheEmailSwapsItAndReturnsATokenForTheNewAddress() {
        User user = withCode(user(), "123456", NOW.plusMinutes(2));
        user.setPendingEmail("new@example.com");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(jwtUtil.generateToken("new@example.com", "ROLE_USER")).thenReturn("new-token");

        AuthResponse response = service.confirmEmailChange(user, new ConfirmCodeRequestDTO("123456"));

        assertEquals("new@example.com", user.getEmail());
        assertNull(user.getPendingEmail());
        assertNull(user.getOtpCode());
        assertEquals("new-token", response.getToken());
        assertEquals("new@example.com", response.getEmail());
        assertEquals(1L, response.getUserId());
    }

    @Test
    void wrongEmailCodeKeepsTheOldEmail() {
        User user = withCode(user(), "123456", NOW.plusMinutes(2));
        user.setPendingEmail("new@example.com");

        assertThrows(IllegalArgumentException.class,
                () -> service.confirmEmailChange(user, new ConfirmCodeRequestDTO("000000")));

        assertEquals("old@example.com", user.getEmail());
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    @Test
    void emailTakenSinceTheCodeWasSentIsRejected() {
        User user = withCode(user(), "123456", NOW.plusMinutes(2));
        user.setPendingEmail("new@example.com");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.confirmEmailChange(user, new ConfirmCodeRequestDTO("123456")));

        assertEquals("old@example.com", user.getEmail());
        assertNull(user.getPendingEmail());
    }

    @Test
    void confirmingWithoutARequestIsRejected() {
        User user = withCode(user(), "123456", NOW.plusMinutes(2));

        assertThrows(IllegalArgumentException.class,
                () -> service.confirmEmailChange(user, new ConfirmCodeRequestDTO("123456")));

        assertEquals("123456", user.getOtpCode());
        verify(userRepository, never()).save(any());
    }
}
