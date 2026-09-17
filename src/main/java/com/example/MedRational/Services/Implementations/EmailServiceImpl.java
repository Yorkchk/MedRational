package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Services.Interfaces.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your MedRational Admin Login Code");
        message.setText("Your one-time verification code is: " + otp + "\n\nThis code expires in 5 minutes.");
        mailSender.send(message);
    }
}