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
import org.springframework.security.authentication.BadCredentialsException;
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
    public String initiateLogin(LoginRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        // 1. Look up user by email (regardless of whether they are ADMIN or USER)
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // 2. Validate password
        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // 3. Generate 6-digit OTP
        String otp = String.format("%06d", new SecureRandom().nextInt(999999));
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        // 4. Send email
        emailService.sendOtpEmail(user.getEmail(), otp);

        return "Verification code sent to " + user.getEmail();
    }

    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (user.getOtpCode() == null || !user.getOtpCode().equals(request.getCode().trim())) {
            throw new BadCredentialsException("Invalid verification code");
        }

        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Verification code has expired");
        }

        // Clear OTP once used
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate token with user details and their actual role (ROLE_ADMIN or ROLE_USER)
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .role(user.getRole().name())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .message("Authentication successful")
                .build();
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
                        .role(Role.ROLE_USER)
                        .build());

        // Update profile info
        user.setFullName(cleanFullName);

        // 1. Save phone number if provided
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }

        // 2. Hash and save the password
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        // Generate 6-digit OTP valid for 5 minutes
        String otp = String.format("%06d", new SecureRandom().nextInt(999999));
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        // Send OTP via email
        emailService.sendOtpEmail(user.getEmail(), otp);
        return "Verification code sent to " + user.getEmail();
    }

    // Inside com.example.MedRational.Services.Implementations.AuthServiceImpl

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

        // Update login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Issue JWT token
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

    @Override
    @Transactional
    public String initiatePasswordReset(ForgotPasswordRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("No account registered with this email."));

        // Generate 6-digit OTP valid for 5 minutes
        String otp = String.format("%06d", new SecureRandom().nextInt(999999));
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        // Send code to user's email
        emailService.sendOtpEmail(user.getEmail(), otp);

        return "Password reset code sent to " + user.getEmail();
    }

    @Override
    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email."));

        if (user.getOtpCode() == null || user.getOtpExpiry() == null) {
            throw new IllegalArgumentException("No active password reset request found.");
        }

        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new IllegalArgumentException("Reset code has expired. Please request a new one.");
        }

        if (!user.getOtpCode().equals(request.getCode().trim())) {
            throw new IllegalArgumentException("Invalid verification code.");
        }

        // Set new hashed password & clear OTP
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        return "Password updated successfully. You can now log in.";
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