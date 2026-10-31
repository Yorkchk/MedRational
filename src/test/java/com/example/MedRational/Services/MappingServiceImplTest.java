package com.example.MedRational.Services;

import com.example.MedRational.DTOs.StudyFileResponse;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Services.Implementations.MappingServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MappingServiceImplTest {

    private final MappingServiceImpl mapping = new MappingServiceImpl();

    @Test
    void studyFileResponseCarriesTheRatingStats() {
        StudyFile file = StudyFile.builder().id(7L).fileName("notes.pdf").avgRating(4.5).totalRatings(12).build();

        StudyFileResponse response = mapping.toStudyFileResponse(file);

        assertEquals(4.5, response.getAvgRating());
        assertEquals(12, response.getTotalRatings());
    }

    @Test
    void studyFileResponseCarriesTheDescription() {
        StudyFile file = StudyFile.builder().id(7L).fileName("ecg.pdf").description("Normal sinus rhythm").build();

        assertEquals("Normal sinus rhythm", mapping.toStudyFileResponse(file).getDescription());
    }
}
