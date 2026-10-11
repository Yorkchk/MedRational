package com.example.MedRational.DTOs;

import lombok.*;

// The user who sent a suggestion, shown to admins on the detail screen
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionSenderDTO {
    private Long userId;
    private String fullName;
    private String email;
    private String phoneNumber;
}
