package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    // With the user, which the renewal needs to build the new access token
    @Query("SELECT s FROM UserSession s JOIN FETCH s.user WHERE s.tokenHash = :tokenHash")
    Optional<UserSession> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
