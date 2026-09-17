package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.ReasoningRequest;
import com.example.MedRational.DTOs.ReasoningResponse;

import java.util.List;

public interface ReasoningService {

    List<ReasoningResponse> getReasoningsByCategory(Long categoryId);

    ReasoningResponse getReasoningById(Long id);

    ReasoningResponse createReasoning(ReasoningRequest request);

    ReasoningResponse updateReasoning(Long id, ReasoningRequest request);

    void deleteReasoning(Long id);
}