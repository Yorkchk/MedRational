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

    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // Under com.example.MedRational.Repositories

    // 1. Category Demand: Aggregate downloads by category
    @Query("""
    SELECT c.name, COUNT(e)
    FROM Category c
    JOIN c.reasonings r
    JOIN r.files f
    JOIN FileDownloadEvent e ON e.file.id = f.id
    GROUP BY c.id, c.name
    ORDER BY COUNT(e) DESC
""")
    List<Object[]> findDownloadVolumeByCategory();

    // 2. Storage Footprint: Total bytes per category
    @Query("""
    SELECT c.name, COALESCE(SUM(f.fileSizeBytes), 0L), COUNT(f)
    FROM Category c
    LEFT JOIN c.reasonings r
    LEFT JOIN r.files f
    GROUP BY c.id, c.name
    ORDER BY SUM(f.fileSizeBytes) DESC
""")
    List<Object[]> findStorageUsageByCategory();

}