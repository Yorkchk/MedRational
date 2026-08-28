package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Find category by name (case-insensitive)
    Optional<Category> findByNameIgnoreCase(String name);

    // Search categories containing a keyword
    List<Category> findByNameContainingIgnoreCase(String keyword);

    // Fetch category with its reasonings pre-loaded in a single query (prevents N+1)
    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.reasonings WHERE c.id = :id")
    Optional<Category> findByIdWithReasonings(@Param("id") Long id);
}