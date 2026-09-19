package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.CreateSuggestionRequestDTO;
import com.example.MedRational.DTOs.SuggestionResponseDTO;
import com.example.MedRational.DTOs.UpdateSuggestionStatusDTO;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface SuggestionService {

    // User submits suggestion (user must not be null)
    SuggestionResponseDTO submitSuggestion(Long userId, CreateSuggestionRequestDTO request);

    // Admin inbox (all suggestions)
    Page<SuggestionResponseDTO> getAllSuggestions(Pageable pageable);

    // Admin inbox filtered by status
    Page<SuggestionResponseDTO> getSuggestionsByStatus(SuggestionStatus status, Pageable pageable);

    // Admin gets single suggestion
    SuggestionResponseDTO getSuggestionById(Long suggestionId);

    // Admin updates status (e.g. APPROVED, REJECTED)
    SuggestionResponseDTO updateSuggestionStatus(Long suggestionId, UpdateSuggestionStatusDTO request);

    // Admin deletes suggestion
    void deleteSuggestion(Long suggestionId);

    // Status counts for admin badge counters
    Map<String, Long> getSuggestionStatusCounts();
}