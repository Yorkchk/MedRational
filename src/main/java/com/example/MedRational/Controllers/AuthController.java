package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.*;
import com.example.MedRational.Services.Implementations.AuthServiceImpl;
import com.example.MedRational.Services.Interfaces.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    @PostMapping("/login")
    public ResponseEntity<AuthMessageResponse> login(@Valid @RequestBody LoginRequest request) {
        String msg = authService.initiateAdminLogin(request);
        return ResponseEntity.ok(new AuthMessageResponse(msg));
    }

    // Step 2: Admin enters 6-digit OTP -> gets JWT Token
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }
}