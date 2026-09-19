package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAuthResponseDTO {
    private Long userId;
    private String email;
    private String fullName;
    private String role;
    private String token;
    private String message;
}