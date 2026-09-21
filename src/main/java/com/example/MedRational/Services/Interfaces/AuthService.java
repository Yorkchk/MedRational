package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.*;

public interface AuthService {

    String createAdmin(CreateAdminRequest request);

    String initiateLogin(LoginRequest request);

    AuthResponse verifyOtp(VerifyOtpRequest request);

    // --- Normal User (Non-Admin) Flows ---
    // Initiates registration or login: saves user as non-admin, generates and emails OTP
    String initiateUserAuth(UserRegisterRequestDTO request);

    // Verifies OTP and returns JWT token + user details
    UserAuthResponseDTO verifyUserOtp(UserVerifyOtpRequestDTO request);
    String initiatePasswordReset(ForgotPasswordRequest request);
    String resetPassword(ResetPasswordRequest request);
}