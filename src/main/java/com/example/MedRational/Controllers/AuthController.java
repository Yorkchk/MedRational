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

    // Use this endpoint manually (e.g. Postman) to create initial or extra admins
    @PostMapping("/admin/create")
    public ResponseEntity<String> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        return ResponseEntity.ok(authService.createAdmin(request));
    }

    // Step 1: Admin logs in -> generates OTP & sends email
    @PostMapping("/admin/login")
    public ResponseEntity<AuthMessageResponse> login(@Valid @RequestBody LoginRequest request) {
        String msg = authService.initiateAdminLogin(request);
        return ResponseEntity.ok(new AuthMessageResponse(msg));
    }

    // Step 2: Admin enters 6-digit OTP -> gets JWT Token
    @PostMapping("/admin/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    // 1. Regular User: Step 1 (Register / Login - request OTP)
    @PostMapping("/user/request-otp")
    public ResponseEntity<Map<String, String>> requestUserOtp(
            @Valid @RequestBody UserRegisterRequestDTO request) {
        String message = authService.initiateUserAuth(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // 2. Regular User: Step 2 (Verify OTP & obtain JWT)
    @PostMapping("/user/verify-otp")
    public ResponseEntity<UserAuthResponseDTO> verifyUserOtp(
            @Valid @RequestBody UserVerifyOtpRequestDTO request) {
        return ResponseEntity.ok(authService.verifyUserOtp(request));
    }
}