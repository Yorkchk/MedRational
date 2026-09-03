package com.example.MedRational.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAdminRequest {
    @NotBlank @Email
    private String email;
    @NotBlank
    private String password;
}