package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.CreateSuggestionRequestDTO;
import com.example.MedRational.DTOs.SuggestionResponseDTO;
import com.example.MedRational.DTOs.UpdateSuggestionStatusDTO;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import com.example.MedRational.Services.Interfaces.SuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    // Student/User submits suggestion
    @PostMapping
    public ResponseEntity<SuggestionResponseDTO> submitSuggestion(
            @RequestParam Long userId,
            @Valid @RequestBody CreateSuggestionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suggestionService.submitSuggestion(userId, request));
    }

    // Admin views inbox with optional status filter
    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Page<SuggestionResponseDTO>> getSuggestions(
            @RequestParam(required = false) SuggestionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (status != null) {
            return ResponseEntity.ok(suggestionService.getSuggestionsByStatus(status, pageable));
        }
        return ResponseEntity.ok(suggestionService.getAllSuggestions(pageable));
    }

    // Admin views single suggestion
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<SuggestionResponseDTO> getSuggestionById(@PathVariable Long id) {
        return ResponseEntity.ok(suggestionService.getSuggestionById(id));
    }

    // Admin updates suggestion status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<SuggestionResponseDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSuggestionStatusDTO request) {
        return ResponseEntity.ok(suggestionService.updateSuggestionStatus(id, request));
    }

    // Admin deletes suggestion
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteSuggestion(@PathVariable Long id) {
        suggestionService.deleteSuggestion(id);
        return ResponseEntity.noContent().build();
    }

    // Admin badge counter per status
    @GetMapping("/counts")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Long>> getStatusCounts() {
        return ResponseEntity.ok(suggestionService.getSuggestionStatusCounts());
    }
}