package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.*;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Security.JwtUtil;
import com.example.MedRational.Services.Interfaces.AuthService;
import com.example.MedRational.Services.Interfaces.EmailService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    // --- Admin Endpoints ---
    @Override
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

    @Override
    @Transactional
    public String initiateAdminLogin(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Invalid credentials"));

        if (user.getRole() != Role.ROLE_ADMIN || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        String otp = String.format("%06d", new SecureRandom().nextInt(999999));
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), otp);
        return "OTP verification code sent to " + user.getEmail();
    }

    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        validateOtp(user, request.getCode());

        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, "Authentication successful");
    }

    // --- Regular User Endpoints ---

    @Override
    @Transactional
    public String initiateUserAuth(UserRegisterRequestDTO request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        String cleanFullName = request.getFullName().trim();

        // Retrieve existing user or create a new non-admin record
        User user = userRepository.findByEmail(cleanEmail)
                .orElseGet(() -> User.builder()
                        .email(cleanEmail)
                        .fullName(cleanFullName)
                        .role(Role.ROLE_USER) // Non-admin role
                        .build());

        // Update full name if changed
        user.setFullName(cleanFullName);

        // Generate 6-digit OTP valid for 5 minutes
        String otp = String.format("%06d", new SecureRandom().nextInt(999999));
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        // Send OTP via email
        emailService.sendOtpEmail(user.getEmail(), otp);
        return "Verification code sent to " + user.getEmail();
    }

    @Override
    @Transactional
    public UserAuthResponseDTO verifyUserOtp(UserVerifyOtpRequestDTO request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new EntityNotFoundException("User with email " + cleanEmail + " not found."));

        validateOtp(user, request.getCode());

        // Clear used OTP
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        // Issue JWT token with the user's role
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return UserAuthResponseDTO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .token(token)
                .message("Authentication successful")
                .build();
    }

    private void validateOtp(User user, String providedCode) {
        if (user.getOtpCode() == null || user.getOtpExpiry() == null) {
            throw new IllegalArgumentException("No pending verification request found.");
        }

        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new IllegalArgumentException("Verification code has expired. Please request a new code.");
        }

        if (!user.getOtpCode().equals(providedCode.trim())) {
            throw new IllegalArgumentException("Invalid verification code.");
        }
    }
}