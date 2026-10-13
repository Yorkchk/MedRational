package com.example.MedRational.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmCodeRequestDTO {

    @NotBlank(message = "Verification code is required")
    @Size(min = 6, max = 6, message = "Code must be 6 digits")
    private String code;
}
