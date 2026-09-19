package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HashtagRepository extends JpaRepository<Hashtag, Long> {
    Optional<Hashtag> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    // Under com.example.MedRational.Repositories

    // 3. Top Trending Hashtags: Ranked by how many files attach them
    @Query("""
    SELECT h.name, COUNT(f)
    FROM Hashtag h
    JOIN h.files f
    GROUP BY h.id, h.name
    ORDER BY COUNT(f) DESC
""")
    List<Object[]> findMostUsedHashtags(org.springframework.data.domain.Pageable pageable);

}