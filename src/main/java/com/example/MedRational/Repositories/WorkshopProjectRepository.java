package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.WorkshopProject;
import com.example.MedRational.Entities.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WorkshopProjectRepository extends JpaRepository<WorkshopProject, Long> {

    @EntityGraph(attributePaths = {"attachments", "author"})
    Page<WorkshopProject> findByStatusOrderByRegistrationOpensAtAsc(ProjectStatus status, Pageable pageable);

    long countByStatus(ProjectStatus status);

    // 1. Exactly ~10 minutes before launch
    @Query("""
        SELECT p FROM WorkshopProject p 
        WHERE p.status = 'UPCOMING' 
          AND p.openingReminderSent = false 
          AND p.registrationOpensAt <= :threshold
    """)
    List<WorkshopProject> findProjectsDueForOpeningReminder(@Param("threshold") LocalDateTime threshold);

    // 2. Launch moment (UPCOMING -> OPEN). launchNotificationSent marks the moment as handled, so a
    // project an admin set back to UPCOMING after it opened stays upcoming
    @Query("""
        SELECT p FROM WorkshopProject p 
        WHERE p.status = 'UPCOMING' 
          AND p.launchNotificationSent = false 
          AND p.registrationOpensAt <= :now
    """)
    List<WorkshopProject> findProjectsReadyToOpen(@Param("now") LocalDateTime now);

    // 3. Exactly ~10 minutes before closing
    @Query("""
        SELECT p FROM WorkshopProject p 
        WHERE p.status = 'OPEN' 
          AND p.closingReminderSent = false 
          AND p.registrationClosesAt <= :threshold
    """)
    List<WorkshopProject> findProjectsDueForClosingReminder(@Param("threshold") LocalDateTime threshold);

    // 4. Closing moment (OPEN -> CLOSED). closedNotificationSent marks the moment as handled, so a
    // project an admin reopened after its deadline stays open
    @Query("""
        SELECT p FROM WorkshopProject p 
        WHERE p.status = 'OPEN' 
          AND p.closedNotificationSent = false 
          AND p.registrationClosesAt <= :now
    """)
    List<WorkshopProject> findProjectsReadyToClose(@Param("now") LocalDateTime now);
}