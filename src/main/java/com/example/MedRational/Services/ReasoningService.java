package com.example.MedRational.Services;

import com.example.MedRational.DTOs.ReasoningRequest;
import com.example.MedRational.DTOs.ReasoningResponse;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Repositories.CategoryRepository;
import com.example.MedRational.Repositories.ReasoningRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReasoningService {

    private final ReasoningRepository reasoningRepository;
    private final CategoryRepository categoryRepository;
    private final MappingService mappingService;

    @Transactional(readOnly = true)
    public List<ReasoningResponse> getReasoningsByCategory(Long categoryId) {
        return reasoningRepository.findByCategoryIdWithFiles(categoryId).stream()
                .map(mappingService::toReasoningResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReasoningResponse getReasoningById(Long id) {
        Reasoning reasoning = reasoningRepository.findByIdWithFiles(id)
                .orElseThrow(() -> new EntityNotFoundException("Reasoning not found with ID: " + id));
        return mappingService.toReasoningResponse(reasoning);
    }

    @Transactional
    public ReasoningResponse createReasoning(ReasoningRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + request.getCategoryId()));

        Reasoning reasoning = Reasoning.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .category(category)
                .build();

        return mappingService.toReasoningResponse(reasoningRepository.save(reasoning));
    }

    @Transactional
    public ReasoningResponse updateReasoning(Long id, ReasoningRequest request) {
        Reasoning reasoning = reasoningRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Reasoning not found with ID: " + id));

        reasoning.setTitle(request.getTitle());
        reasoning.setContent(request.getContent());

        if (!reasoning.getCategory().getId().equals(request.getCategoryId())) {
            Category newCategory = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + request.getCategoryId()));
            reasoning.setCategory(newCategory);
        }

        return mappingService.toReasoningResponse(reasoningRepository.save(reasoning));
    }

    @Transactional
    public void deleteReasoning(Long id) {
        if (!reasoningRepository.existsById(id)) {
            throw new EntityNotFoundException("Reasoning not found with ID: " + id);
        }
        reasoningRepository.deleteById(id);
    }
}