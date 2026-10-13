package com.example.MedRational.Services.Support;

import com.example.MedRational.Entities.User;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;

// Emailed 6-digit codes stored on the user row, valid for 5 minutes.
// A wrong or expired code is an IllegalArgumentException (400), never a 401/403, because the app
// signs the user out on 401/403.
public final class OtpCodes {

    public static final int VALID_MINUTES = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpCodes() {
    }

    // Stores a fresh code on the user and returns it; the caller saves the user and emails the code
    public static String issue(User user, Clock clock) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        user.setOtpCode(code);
        user.setOtpExpiry(LocalDateTime.now(clock).plusMinutes(VALID_MINUTES));
        return code;
    }

    // Checks the code and clears it so it can't be used twice
    public static void consume(User user, String providedCode, Clock clock) {
        if (user.getOtpCode() == null || user.getOtpExpiry() == null) {
            throw new IllegalArgumentException("No pending verification request found.");
        }
        if (LocalDateTime.now(clock).isAfter(user.getOtpExpiry())) {
            throw new IllegalArgumentException("Verification code has expired. Please request a new code.");
        }
        if (providedCode == null || !user.getOtpCode().equals(providedCode.trim())) {
            throw new IllegalArgumentException("Invalid verification code.");
        }
        user.setOtpCode(null);
        user.setOtpExpiry(null);
    }
}
