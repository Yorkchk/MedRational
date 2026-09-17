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

public class StudyFileSpecification {

    public static Specification<StudyFile> withFilters(FileSearchFilterDTO filter) {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            // 1. Search by File Name (partial match, case-insensitive)
            if (filter.getFileName() != null && !filter.getFileName().trim().isEmpty()) {
                String pattern = "%" + filter.getFileName().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("fileName")), pattern));
            }

            // 2. Search by Reasoning Title (partial match, case-insensitive)
            if (filter.getReasoningTitle() != null && !filter.getReasoningTitle().trim().isEmpty()) {
                Join<StudyFile, Reasoning> reasoningJoin = root.join("reasoning", JoinType.INNER);
                String pattern = "%" + filter.getReasoningTitle().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(reasoningJoin.get("title")), pattern));
            }

            // 3. Search by Category Name (partial match, case-insensitive)
            if (filter.getCategoryName() != null && !filter.getCategoryName().trim().isEmpty()) {
                Join<StudyFile, Reasoning> reasoningJoin = root.join("reasoning", JoinType.INNER);
                Join<Reasoning, Category> categoryJoin = reasoningJoin.join("category", JoinType.INNER);
                String pattern = "%" + filter.getCategoryName().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(categoryJoin.get("name")), pattern));
            }

            // 4. Search by Hashtag (exact match, case-insensitive, strip leading '#')
            if (filter.getHashtag() != null && !filter.getHashtag().trim().isEmpty()) {
                String cleanTag = filter.getHashtag().trim().toLowerCase().replaceAll("^#+", "");
                Join<StudyFile, Hashtag> hashtagJoin = root.join("hashtags", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(hashtagJoin.get("name")), cleanTag));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}