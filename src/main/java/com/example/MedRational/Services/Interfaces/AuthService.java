package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.*;

public interface AuthService {

    String createAdmin(CreateAdminRequest request);

    // Email + password only: no code at sign-in
    AuthResponse login(LoginRequest request);

    // --- Normal User (Non-Admin) Flows ---
    // Starts sign-up: saves the unverified user as non-admin, generates and emails OTP
    String initiateUserAuth(UserRegisterRequestDTO request);

    // Verifies the sign-up OTP, marks the email verified and returns JWT token + user details
    UserAuthResponseDTO verifyUserOtp(UserVerifyOtpRequestDTO request);
    String initiatePasswordReset(ForgotPasswordRequest request);
    String resetPassword(ResetPasswordRequest request);
}