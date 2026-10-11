package com.example.MedRational.Entities;

import com.example.MedRational.Entities.enums.SuggestionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "suggestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Nullable in the database so rows created before titles existed still load; the API requires it
    @Column(length = 150)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Embedded
    private SuggestionAttachment attachment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Closed suggestions are archived and read-only for both sides
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // Last time each side posted; drives "last activity" and the other side's unread dot
    @Column(name = "last_user_activity_at")
    private LocalDateTime lastUserActivityAt;

    @Column(name = "last_admin_activity_at")
    private LocalDateTime lastAdminActivityAt;

    // Latest post from either side; lists are sorted by it
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    // Replies in the thread, kept here so lists don't load every conversation.
    // Nullable so the column can be added to existing rows; the migration backfills 0
    @Column(name = "message_count")
    @Builder.Default
    private Integer messageCount = 0;

    // Last time each side opened the suggestion
    @Column(name = "user_last_read_at")
    private LocalDateTime userLastReadAt;

    @Column(name = "admin_last_read_at")
    private LocalDateTime adminLastReadAt;

    @OneToMany(mappedBy = "suggestion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<SuggestionMessage> messages = new ArrayList<>();
}
