package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.UserDailyActivity;
import com.example.MedRational.Repositories.StatisticsRows.BucketCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserDailyActivityRepository extends JpaRepository<UserDailyActivity, Long> {

    // Safe to call several times a day: the (user, day) unique constraint keeps a single row
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO user_daily_activity (user_id, activity_date)
            VALUES (:userId, :day)
            ON CONFLICT (user_id, activity_date) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId, @Param("day") LocalDate day);

    // Distinct students active between two days, both included
    @Query("""
            SELECT COUNT(DISTINCT a.user.id) FROM UserDailyActivity a
            WHERE a.user.role = com.example.MedRational.Entities.Role.ROLE_USER
              AND a.activityDate >= :from AND a.activityDate <= :to
            """)
    long countActiveStudents(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // Distinct active students per bucket of bucketDays days counted back from today
    @Query(value = """
            SELECT (CAST(:today AS date) - a.activity_date) / :bucketDays AS bucket,
                   COUNT(DISTINCT a.user_id) AS total
            FROM user_daily_activity a
            JOIN users u ON u.id = a.user_id
            WHERE u.role = 'ROLE_USER' AND a.activity_date >= :since
            GROUP BY 1
            """, nativeQuery = true)
    List<BucketCount> countActiveStudentsPerBucket(@Param("today") LocalDate today,
                                                   @Param("since") LocalDate since,
                                                   @Param("bucketDays") int bucketDays);
}
