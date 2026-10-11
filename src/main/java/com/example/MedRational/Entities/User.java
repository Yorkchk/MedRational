package com.example.MedRational.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Role role = Role.ROLE_USER;

    @Column(name = "otp_code", length = 6)
    private String otpCode;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

        @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // Novelties baseline: files uploaded after this are "new". Only moves when the user opens the
    // novelties feed (login doesn't touch it); null means the account was never shown the feed
    @Column(name = "novelties_seen_at")
    private LocalDateTime noveltiesSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


}