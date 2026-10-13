package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.StatisticsRows.BucketCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Find admin by email for authentication
    Optional<User> findByEmail(String email);

    // Check if an email is already registered
    boolean existsByEmail(String email);

    // List all admins or guests by role
    List<User> findByRole(Role role);

    // --- Admin statistics ---

    long countByRole(Role role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.createdAt >= :from AND u.createdAt < :to")
    long countByRoleCreatedBetween(@Param("role") Role role,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    // Student sign-ups per bucket of bucketDays days counted back from today
    @Query(value = """
            SELECT (CAST(:today AS date) - CAST(u.created_at AS date)) / :bucketDays AS bucket,
                   COUNT(*) AS total
            FROM users u
            WHERE u.role = 'ROLE_USER' AND u.created_at >= :since
            GROUP BY 1
            """, nativeQuery = true)
    List<BucketCount> countStudentSignUpsPerBucket(@Param("today") LocalDate today,
                                                   @Param("since") LocalDateTime since,
                                                   @Param("bucketDays") int bucketDays);

    // Students who downloaded or opened at least one file
    @Query(value = """
            SELECT COUNT(*) FROM users u
            WHERE u.role = 'ROLE_USER'
              AND (EXISTS (SELECT 1 FROM file_download_events e WHERE e.user_id = u.id)
                   OR EXISTS (SELECT 1 FROM file_view_events v WHERE v.user_id = u.id))
            """, nativeQuery = true)
    long countActivatedStudents();
}