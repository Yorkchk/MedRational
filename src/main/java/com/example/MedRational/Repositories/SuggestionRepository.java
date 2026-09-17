package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Suggestion;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    // Eagerly fetches the User to provide the full name, email, and phone in the admin inbox
    @EntityGraph(attributePaths = {"user"})
    Page<Suggestion> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<Suggestion> findByStatusOrderByCreatedAtDesc(SuggestionStatus status, Pageable pageable);

    long countByStatus(SuggestionStatus status);
}