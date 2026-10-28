package com.example.MedRational.DTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// The device's refresh token, for /auth/refresh and /auth/logout
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {
    @NotBlank
    private String refreshToken;
}
