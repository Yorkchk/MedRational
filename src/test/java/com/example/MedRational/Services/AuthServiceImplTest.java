package com.example.MedRational.Services;

import com.example.MedRational.DTOs.AuthResponse;
import com.example.MedRational.DTOs.LoginRequest;
import com.example.MedRational.DTOs.RefreshTokenRequest;
import com.example.MedRational.DTOs.ResetPasswordRequest;
import com.example.MedRational.DTOs.UserRegisterRequestDTO;
import com.example.MedRational.DTOs.UserVerifyOtpRequestDTO;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Exceptions.EmailAlreadyRegisteredException;
import com.example.MedRational.Exceptions.EmailNotVerifiedException;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Security.JwtUtil;
import com.example.MedRational.Services.Implementations.AuthServiceImpl;
import com.example.MedRational.Services.Interfaces.EmailService;
import com.example.MedRational.Services.Interfaces.UserSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final EmailService emailService = mock(EmailService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserSessionService sessionService = mock(UserSessionService.class);

    private final AuthServiceImpl service =
            new AuthServiceImpl(userRepository, passwordEncoder, emailService, jwtUtil, sessionService);

    private User user(Boolean emailVerified) {
        User user = User.builder()
                .id(1L)
                .email("sara@example.com")
                .fullName("Sara Alaoui")
                .firstName("Sara")
                .lastName("Alaoui")
                .passwordHash("hash")
                .role(Role.ROLE_USER)
                .emailVerified(emailVerified)
                .build();
        when(userRepository.findByEmail("sara@example.com")).thenReturn(Optional.of(user));
        return user;
    }

    private LoginRequest login(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(" Sara@Example.com ");
        request.setPassword(password);
        return request;
    }

    private UserRegisterRequestDTO register() {
        return new UserRegisterRequestDTO("Eve Intruder", "Eve", "Intruder", "sara@example.com", "evil123", "0612345678");
    }

    @Test
    void theRightPasswordSignsInWithoutAnyCode() {
        User user = user(true);
        when(passwordEncoder.matches("secret1", "hash")).thenReturn(true);
        when(jwtUtil.generateToken("sara@example.com", "ROLE_USER")).thenReturn("jwt");
        when(sessionService.start(user)).thenReturn("refresh-1");

        AuthResponse auth = service.login(login("secret1"));

        assertEquals("jwt", auth.getToken());
        assertEquals("ROLE_USER", auth.getRole());
        assertEquals(1L, auth.getUserId());
        assertEquals("sara@example.com", auth.getEmail());
        // Keeps this device signed in once the JWT expires
        assertEquals("refresh-1", auth.getRefreshToken());
        assertNotNull(user.getLastLoginAt());
        verifyNoInteractions(emailService);
    }

    @Test
    void aWrongPasswordIsRejected() {
        user(true);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service.login(login("wrong")));
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    @Test
    void anUnknownEmailIsRejectedLikeAWrongPassword() {
        when(userRepository.findByEmail("sara@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> service.login(login("secret1")));
    }

    // Sign-in has no code any more, so an email nobody confirmed must not open a session
    @Test
    void anUnverifiedSignUpCannotSignIn() {
        user(null);
        when(passwordEncoder.matches("secret1", "hash")).thenReturn(true);

        assertThrows(EmailNotVerifiedException.class, () -> service.login(login("secret1")));
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
        verify(sessionService, never()).start(any());
    }

    // Regression: sign-up used to replace the password of an existing account before any code
    @Test
    void signUpCannotTakeOverAVerifiedAccount() {
        User owner = user(true);

        assertThrows(EmailAlreadyRegisteredException.class, () -> service.initiateUserAuth(register()));

        assertEquals("hash", owner.getPasswordHash());
        assertEquals("Sara Alaoui", owner.getFullName());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void anAbandonedSignUpCanBeRedone() {
        User pending = user(false);
        when(passwordEncoder.encode("evil123")).thenReturn("newHash");

        service.initiateUserAuth(register());

        assertEquals("newHash", pending.getPasswordHash());
        assertNotNull(pending.getOtpCode());
        verify(emailService).sendOtpEmail(eq("sara@example.com"), anyString());
    }

    @Test
    void confirmingTheSignUpCodeVerifiesTheEmail() {
        User pending = user(null);
        pending.setOtpCode("123456");
        pending.setOtpExpiry(LocalDateTime.now().plusMinutes(5));

        service.verifyUserOtp(new UserVerifyOtpRequestDTO("sara@example.com", "123456"));

        assertTrue(pending.getEmailVerified());
        // The app signs in after signing up, so no session is left unused here
        verify(sessionService, never()).start(any());
    }

    // The reset code proves the email too, so the account can sign in afterwards
    @Test
    void resettingThePasswordVerifiesTheEmail() {
        User pending = user(null);
        pending.setOtpCode("123456");
        pending.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        when(passwordEncoder.encode("secret2")).thenReturn("hash2");

        service.resetPassword(new ResetPasswordRequest("sara@example.com", "123456", "secret2"));

        assertTrue(pending.getEmailVerified());
        assertEquals("hash2", pending.getPasswordHash());
    }

    // Whoever knew the old password is signed out everywhere
    @Test
    void resettingThePasswordSignsEveryDeviceOut() {
        User user = user(true);
        user.setOtpCode("123456");
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        when(passwordEncoder.encode("secret2")).thenReturn("hash2");

        service.resetPassword(new ResetPasswordRequest("sara@example.com", "123456", "secret2"));

        verify(sessionService).endAll(user);
    }

    @Test
    void aValidRefreshTokenGivesANewPair() {
        User user = user(true);
        when(sessionService.renew("refresh-1")).thenReturn(new UserSessionService.Renewed(user, "refresh-2"));
        when(jwtUtil.generateToken("sara@example.com", "ROLE_USER")).thenReturn("jwt-2");

        AuthResponse auth = service.refresh(new RefreshTokenRequest("refresh-1"));

        assertEquals("jwt-2", auth.getToken());
        assertEquals("refresh-2", auth.getRefreshToken());
        assertEquals(1L, auth.getUserId());
        // A renewal isn't a sign-in
        assertNull(user.getLastLoginAt());
    }

    // 401, so the app knows the session is over and shows the login screen
    @Test
    void anEndedSessionCannotBeRenewed() {
        when(sessionService.renew("old")).thenThrow(new BadCredentialsException("Session expired"));

        assertThrows(BadCredentialsException.class, () -> service.refresh(new RefreshTokenRequest("old")));
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    @Test
    void signingOutEndsThisDevicesSession() {
        service.logout(new RefreshTokenRequest("refresh-1"));

        verify(sessionService).end("refresh-1");
    }
}
