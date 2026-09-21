package com.example.MedRational.Specifications;

import com.example.MedRational.DTOs.FileSearchFilterDTO;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Hashtag;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class StudyFileSpecifications {

    /**
     * Unified Instagram/Facebook style search:
     * Matches a single search term across file name, reasoning title, category name, OR hashtag.
     * Optionally narrows down results if a specific category is selected in the UI.
     */
    public static Specification<StudyFile> searchFiles(String query, Long categoryId) {
        return (root, criteriaQuery, cb) -> {
            criteriaQuery.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            // Safe Left Joins so files without hashtags or standalone items still appear
            Join<StudyFile, Reasoning> reasoningJoin = root.join("reasoning", JoinType.LEFT);
            Join<Reasoning, Category> categoryJoin = reasoningJoin.join("category", JoinType.LEFT);
            Join<StudyFile, Hashtag> hashtagJoin = root.join("hashtags", JoinType.LEFT);

            // 1. If a category filter is selected (e.g. from top chips/dropdown)
            if (categoryId != null && categoryId > 0) {
                predicates.add(cb.equal(categoryJoin.get("id"), categoryId));
            }

            // 2. Search query matches fileName OR reasoning title OR category name OR hashtag
            if (query != null && !query.trim().isBlank()) {
                String cleanQuery = query.trim().toLowerCase();
                String rawTag = cleanQuery.replaceAll("^#+", "");
                String pattern = "%" + cleanQuery + "%";
                String tagPattern = "%" + rawTag + "%";

                Predicate fileNameMatch = cb.like(cb.lower(root.get("fileName")), pattern);
                Predicate reasoningTitleMatch = cb.like(cb.lower(reasoningJoin.get("title")), pattern);
                Predicate categoryNameMatch = cb.like(cb.lower(categoryJoin.get("name")), pattern);
                Predicate hashtagMatch = cb.like(cb.lower(hashtagJoin.get("name")), tagPattern);

                predicates.add(cb.or(fileNameMatch, reasoningTitleMatch, categoryNameMatch, hashtagMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Preserved: Strict multi-field AND filter
     */
    public static Specification<StudyFile> withFilters(FileSearchFilterDTO filter) {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getFileName() != null && !filter.getFileName().trim().isEmpty()) {
                String pattern = "%" + filter.getFileName().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("fileName")), pattern));
            }

            if (filter.getReasoningTitle() != null && !filter.getReasoningTitle().trim().isEmpty()) {
                Join<StudyFile, Reasoning> reasoningJoin = root.join("reasoning", JoinType.INNER);
                String pattern = "%" + filter.getReasoningTitle().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(reasoningJoin.get("title")), pattern));
            }

            if (filter.getCategoryName() != null && !filter.getCategoryName().trim().isEmpty()) {
                Join<StudyFile, Reasoning> reasoningJoin = root.join("reasoning", JoinType.INNER);
                Join<Reasoning, Category> categoryJoin = reasoningJoin.join("category", JoinType.INNER);
                String pattern = "%" + filter.getCategoryName().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(categoryJoin.get("name")), pattern));
            }

            if (filter.getHashtag() != null && !filter.getHashtag().trim().isEmpty()) {
                String cleanTag = filter.getHashtag().trim().toLowerCase().replaceAll("^#+", "");
                Join<StudyFile, Hashtag> hashtagJoin = root.join("hashtags", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(hashtagJoin.get("name")), cleanTag));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}