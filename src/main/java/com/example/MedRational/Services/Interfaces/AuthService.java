package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.AuthResponse;
import com.example.MedRational.DTOs.CreateAdminRequest;
import com.example.MedRational.DTOs.LoginRequest;
import com.example.MedRational.DTOs.VerifyOtpRequest;

public interface AuthService {

    String createAdmin(CreateAdminRequest request);

    String initiateAdminLogin(LoginRequest request);

    AuthResponse verifyOtp(VerifyOtpRequest request);
}