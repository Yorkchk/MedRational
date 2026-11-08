package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.*;
import com.example.MedRational.Security.RateLimitedAction;
import com.example.MedRational.Security.RateLimiter;
import com.example.MedRational.Services.Interfaces.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    // There is no endpoint to create admins: everything under /api/v1/auth is public. Promote an
    // existing account in the database instead: update users set role = 'ROLE_ADMIN' where email = '…';

    // -------------------------------------------------------------
    // Unified Login (Admins & Regular Users: Email + Password -> JWT, no code)
    // -------------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        rateLimiter.check(RateLimitedAction.LOGIN, request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }

    // -------------------------------------------------------------
    // Staying signed in: the app trades its refresh token when the JWT expires
    // -------------------------------------------------------------
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------
    // User Registration (New Users: Name, Email, Phone, Pass -> OTP -> Back to Welcome)
    // -------------------------------------------------------------
    @PostMapping("/user/register")
    public ResponseEntity<Map<String, String>> registerUser(
            @Valid @RequestBody UserRegisterRequestDTO request) {
        rateLimiter.check(RateLimitedAction.SEND_CODE, request.getEmail());
        String message = authService.initiateUserAuth(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/user/verify-registration")
    public ResponseEntity<UserAuthResponseDTO> verifyRegistrationOtp(
            @Valid @RequestBody UserVerifyOtpRequestDTO request) {
        rateLimiter.check(RateLimitedAction.VERIFY_CODE, request.getEmail());
        return ResponseEntity.ok(authService.verifyUserOtp(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthMessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        rateLimiter.check(RateLimitedAction.SEND_CODE, request.getEmail());
        String msg = authService.initiatePasswordReset(request);
        return ResponseEntity.ok(new AuthMessageResponse(msg));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthMessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        rateLimiter.check(RateLimitedAction.VERIFY_CODE, request.getEmail());
        String msg = authService.resetPassword(request);
        return ResponseEntity.ok(new AuthMessageResponse(msg));
    }
}