package com.example.MedRational.Services.Interfaces;

public interface EmailService {

    void sendOtpEmail(String toEmail, String otp);

    // Same code email, with a subject that says what the code is for
    void sendOtpEmail(String toEmail, String otp, String subject);
}