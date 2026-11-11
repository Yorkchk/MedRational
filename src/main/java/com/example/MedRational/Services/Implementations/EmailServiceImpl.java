package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Services.Interfaces.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    // Amazon SES only sends from a verified identity, and its SMTP username is an access key, not an address
    private final String from;

    public EmailServiceImpl(JavaMailSender mailSender, @Value("${mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendOtpEmail(String toEmail, String otp) {
        sendOtpEmail(toEmail, otp, "Your MedRational Admin Login Code");
    }

    public void sendOtpEmail(String toEmail, String otp, String subject) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText("Your one-time verification code is: " + otp + "\n\nThis code expires in 5 minutes.");
        mailSender.send(message);
    }
}
