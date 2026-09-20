package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.*;
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

    // Manual / Bootstrap endpoint for creating admins
    @PostMapping("/admin/create")
    public ResponseEntity<String> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        return ResponseEntity.ok(authService.createAdmin(request));
    }

    // -------------------------------------------------------------
    // Unified Login (Admins & Regular Users: Email + Password -> OTP)
    // -------------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<AuthMessageResponse> login(@Valid @RequestBody LoginRequest request) {
        String msg = authService.initiateLogin(request);
        return ResponseEntity.ok(new AuthMessageResponse(msg));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    // -------------------------------------------------------------
    // User Registration (New Users: Name, Email, Phone, Pass -> OTP -> Back to Welcome)
    // -------------------------------------------------------------
    @PostMapping("/user/register")
    public ResponseEntity<Map<String, String>> registerUser(
            @Valid @RequestBody UserRegisterRequestDTO request) {
        String message = authService.initiateUserAuth(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/user/verify-registration")
    public ResponseEntity<UserAuthResponseDTO> verifyRegistrationOtp(
            @Valid @RequestBody UserVerifyOtpRequestDTO request) {
        return ResponseEntity.ok(authService.verifyUserOtp(request));
    }
}