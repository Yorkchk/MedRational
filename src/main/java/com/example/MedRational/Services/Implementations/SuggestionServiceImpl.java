package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.CreateSuggestionRequestDTO;
import com.example.MedRational.DTOs.SuggestionResponseDTO;
import com.example.MedRational.DTOs.UpdateSuggestionStatusDTO;
import com.example.MedRational.Entities.Suggestion;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import com.example.MedRational.Repositories.SuggestionRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Services.Interfaces.SuggestionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuggestionServiceImpl implements SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public SuggestionResponseDTO submitSuggestion(Long userId, CreateSuggestionRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        Suggestion suggestion = Suggestion.builder()
                .user(user)
                .message(request.getMessage().trim())
                .attachmentUrl(request.getAttachmentUrl())
                .status(SuggestionStatus.PENDING)
                .build();

        return mapToDTO(suggestionRepository.save(suggestion));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SuggestionResponseDTO> getAllSuggestions(Pageable pageable) {
        return suggestionRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SuggestionResponseDTO> getSuggestionsByStatus(SuggestionStatus status, Pageable pageable) {
        return suggestionRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(this::mapToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public SuggestionResponseDTO getSuggestionById(Long suggestionId) {
        Suggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new EntityNotFoundException("Suggestion not found with ID: " + suggestionId));
        return mapToDTO(suggestion);
    }

    @Override
    @Transactional
    public SuggestionResponseDTO updateSuggestionStatus(Long suggestionId, UpdateSuggestionStatusDTO request) {
        Suggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new EntityNotFoundException("Suggestion not found with ID: " + suggestionId));

        suggestion.setStatus(request.getStatus());
        return mapToDTO(suggestionRepository.save(suggestion));
    }

    @Override
    @Transactional
    public void deleteSuggestion(Long suggestionId) {
        if (!suggestionRepository.existsById(suggestionId)) {
            throw new EntityNotFoundException("Suggestion not found with ID: " + suggestionId);
        }
        suggestionRepository.deleteById(suggestionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getSuggestionStatusCounts() {
        return Arrays.stream(SuggestionStatus.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        suggestionRepository::countByStatus
                ));
    }

    private SuggestionResponseDTO mapToDTO(Suggestion suggestion) {
        User user = suggestion.getUser();
        return SuggestionResponseDTO.builder()
                .id(suggestion.getId())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .message(suggestion.getMessage())
                .attachmentUrl(suggestion.getAttachmentUrl())
                .status(suggestion.getStatus())
                .createdAt(suggestion.getCreatedAt())
                .build();
    }
}