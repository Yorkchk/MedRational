package com.example.MedRational.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

// One signed-in device. Its refresh token is renewed in place, so the table holds one row per
// device, not one per token. Only the token's SHA-256 is stored, so a database leak can't sign
// anyone in
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    // Pushed forward on every renewal: a device unused for that long has to sign in again
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
