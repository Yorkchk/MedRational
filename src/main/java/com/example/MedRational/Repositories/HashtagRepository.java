package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HashtagRepository extends JpaRepository<Hashtag, Long> {
    Optional<Hashtag> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}