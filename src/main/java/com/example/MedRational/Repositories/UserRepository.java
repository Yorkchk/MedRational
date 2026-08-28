package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}