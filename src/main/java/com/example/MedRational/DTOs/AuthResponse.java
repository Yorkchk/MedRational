package com.example.MedRational.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    // Trades for a new token when this one expires (/auth/refresh); null when no session was started
    private String refreshToken;
    private String role;
    private Long userId;
    private String email;
    private String fullName;
    private String message;
}