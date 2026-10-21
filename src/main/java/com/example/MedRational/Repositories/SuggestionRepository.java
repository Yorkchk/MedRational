package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Suggestion;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    // Admin inbox / archive, most recent activity first. Eagerly fetches the sender for the list
    @EntityGraph(attributePaths = {"user"})
    Page<Suggestion> findByStatusOrderByLastActivityAtDesc(SuggestionStatus status, Pageable pageable);

    // A user's own suggestions, most recent activity first
    @EntityGraph(attributePaths = {"user"})
    Page<Suggestion> findByUserIdAndStatusOrderByLastActivityAtDesc(Long userId, SuggestionStatus status, Pageable pageable);

    // Detail view: sender plus the whole thread with each author
    @EntityGraph(attributePaths = {"user", "messages", "messages.author"})
    Optional<Suggestion> findWithThreadById(Long id);

    long countByStatus(SuggestionStatus status);

    // Pending suggestions where the user posted since an admin last opened them
    @Query("""
            SELECT COUNT(s) FROM Suggestion s
            WHERE s.status = com.example.MedRational.Entities.enums.SuggestionStatus.PENDING
              AND (s.adminLastReadAt IS NULL OR s.lastUserActivityAt > s.adminLastReadAt)
            """)
    long countUnreadByAdmin();

    // Account deletion: the user's suggestions with their threads, to collect the attachments
    @EntityGraph(attributePaths = {"messages"})
    List<Suggestion> findAllByUserId(Long userId);

    // Account deletion: replies the user wrote in other people's threads stay, with no author
    @Modifying
    @Query("UPDATE SuggestionMessage m SET m.author = null WHERE m.author.id = :userId")
    void anonymizeMessagesByAuthorId(@Param("userId") Long userId);
}
