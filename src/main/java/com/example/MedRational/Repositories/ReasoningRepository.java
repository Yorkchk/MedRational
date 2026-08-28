package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Reasoning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReasoningRepository extends JpaRepository<Reasoning, Long> {

    // Find all reasonings under a specific category
    List<Reasoning> findByCategoryId(Long categoryId);

    // Search reasonings by title keyword
    List<Reasoning> findByTitleContainingIgnoreCase(String keyword);

    // Fetch reasoning with all its attached files pre-loaded
    @Query("SELECT DISTINCT r FROM Reasoning r LEFT JOIN FETCH r.files WHERE r.id = :id")
    Optional<Reasoning> findByIdWithFiles(@Param("id") Long id);

    // Fetch all reasonings for a category along with their files
    @Query("SELECT DISTINCT r FROM Reasoning r LEFT JOIN FETCH r.files WHERE r.category.id = :categoryId")
    List<Reasoning> findByCategoryIdWithFiles(@Param("categoryId") Long categoryId);
}