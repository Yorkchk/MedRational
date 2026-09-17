package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.enums.ProjectStatus;
import com.example.MedRational.Entities.enums.ProjectType;
import com.example.MedRational.Entities.WorkshopProject;
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

    Page<WorkshopProject> findByProjectTypeOrderByCreatedAtDesc(ProjectType projectType, Pageable pageable);

    // Schedulers: 10 minutes prior to opening (for student/admin notifications)
    @Query("SELECT p FROM WorkshopProject p WHERE p.status = 'UPCOMING' AND p.registrationOpensAt BETWEEN :start AND :end")
    List<WorkshopProject> findProjectsOpeningBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Schedulers: Countdown reaches 0 -> transition from UPCOMING to OPEN
    @Query("SELECT p FROM WorkshopProject p WHERE p.status = 'UPCOMING' AND p.registrationOpensAt <= :now")
    List<WorkshopProject> findProjectsReadyToOpen(@Param("now") LocalDateTime now);

    // Schedulers: 10 minutes prior to deadline (reminder to close/last chance)
    @Query("SELECT p FROM WorkshopProject p WHERE p.status = 'OPEN' AND p.registrationClosesAt BETWEEN :start AND :end")
    List<WorkshopProject> findProjectsClosingBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Schedulers: Countdown reaches 0 -> transition from OPEN to CLOSED
    @Query("SELECT p FROM WorkshopProject p WHERE p.status = 'OPEN' AND p.registrationClosesAt <= :now")
    List<WorkshopProject> findProjectsReadyToClose(@Param("now") LocalDateTime now);
}