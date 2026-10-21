package com.example.MedRational.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Public account deletion page, step 1
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionCodeRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
