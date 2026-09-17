package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.ReasoningRequest;
import com.example.MedRational.DTOs.ReasoningResponse;
import com.example.MedRational.Services.Implementations.ReasoningServiceImpl;
import com.example.MedRational.Services.Interfaces.ReasoningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reasonings")
@RequiredArgsConstructor
public class ReasoningController {

    private final ReasoningService reasoningService;

    // Public / Student: Get all reasonings for a category
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ReasoningResponse>> getReasoningsByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(reasoningService.getReasoningsByCategory(categoryId));
    }

    // Public / Student: Get single reasoning by ID
    @GetMapping("/{id}")
    public ResponseEntity<ReasoningResponse> getReasoningById(@PathVariable Long id) {
        return ResponseEntity.ok(reasoningService.getReasoningById(id));
    }

    // Admin Only: Create new reasoning
    @PostMapping
    public ResponseEntity<ReasoningResponse> createReasoning(@Valid @RequestBody ReasoningRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reasoningService.createReasoning(request));
    }

    // Admin Only: Update reasoning
    @PutMapping("/{id}")
    public ResponseEntity<ReasoningResponse> updateReasoning(
            @PathVariable Long id,
            @Valid @RequestBody ReasoningRequest request
    ) {
        return ResponseEntity.ok(reasoningService.updateReasoning(id, request));
    }

    // Admin Only: Delete reasoning
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReasoning(@PathVariable Long id) {
        reasoningService.deleteReasoning(id);
        return ResponseEntity.noContent().build();
    }
}