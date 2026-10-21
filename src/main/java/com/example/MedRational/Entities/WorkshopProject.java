package com.example.MedRational.Entities;

import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Entities.enums.ProjectType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workshop_projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null once the account is deleted: the row stays, no longer traceable to anyone
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 50)
    private ProjectType projectType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.UPCOMING;

    // Form/Registration URL (hidden in responses when status != OPEN)
    @Column(name = "form_link", length = 1000)
    private String formLink;

    // Number of places; null means no limit. Informational only: sign-ups happen on the form link,
    // so an admin closes registration early through the status override once it is full
    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "registration_opens_at", nullable = false)
    private LocalDateTime registrationOpensAt;

    @Column(name = "registration_closes_at", nullable = false)
    private LocalDateTime registrationClosesAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkshopAttachment> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Add to com.example.MedRational.Entities.WorkshopProject

    @Column(name = "opening_reminder_sent", nullable = false)
    @Builder.Default
    private boolean openingReminderSent = false;

    @Column(name = "launch_notification_sent", nullable = false)
    @Builder.Default
    private boolean launchNotificationSent = false;

    @Column(name = "closing_reminder_sent", nullable = false)
    @Builder.Default
    private boolean closingReminderSent = false;

    @Column(name = "closed_notification_sent", nullable = false)
    @Builder.Default
    private boolean closedNotificationSent = false;
}