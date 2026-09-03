package com.example.MedRational.Services;

import com.example.MedRational.DTOs.*;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Security.JwtUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    // Endpoint you use directly to provision Admins (never exposed on public UI)
    @Transactional
    public String createAdmin(CreateAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Admin with this email already exists.");
        }

        User admin = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_ADMIN)
                .build();

        userRepository.save(admin);
        return "Admin account created successfully.";
    }

    // Step 1: Validate Email/Password and send OTP
    @Transactional
    public String initiateAdminLogin(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Invalid credentials"));

        if (user.getRole() != Role.ROLE_ADMIN || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        // Generate 6-digit random code
        String otp = String.format("%06d", new SecureRandom().nextInt(999999));
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        // Send email
        emailService.sendOtpEmail(user.getEmail(), otp);
        return "OTP verification code sent to " + user.getEmail();
    }

    // Step 2: Verify OTP and return JWT
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getOtpCode() == null || user.getOtpExpiry() == null) {
            throw new IllegalArgumentException("No pending verification request found.");
        }

        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new IllegalArgumentException("Verification code has expired. Please login again.");
        }

        if (!user.getOtpCode().equals(request.getCode().trim())) {
            throw new IllegalArgumentException("Invalid verification code.");
        }

        // Clear OTP once used
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        // Issue JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, "Authentication successful");
    }
}