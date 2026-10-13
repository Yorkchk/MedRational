package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.DTOs.SuggestionDetailDTO;
import com.example.MedRational.DTOs.SuggestionMessageDTO;
import com.example.MedRational.DTOs.SuggestionSummaryDTO;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Interfaces.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

// Suggestions are private conversations between the user who sent one and the admins.
// The caller always comes from the JWT, never from a request parameter.
@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final CurrentUserGuard currentUserGuard;

    // User sends a suggestion: title, description and an optional file (max 10 MB)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuggestionDetailDTO> createSuggestion(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam(required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                suggestionService.createSuggestion(currentUserGuard.requireCurrentUser(), title, description, file));
    }

    // The caller's own suggestions; PENDING = open, CLOSED = archived
    @GetMapping("/mine")
    public ResponseEntity<Page<SuggestionSummaryDTO>> getMySuggestions(
            @RequestParam(defaultValue = "PENDING") SuggestionStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                suggestionService.getMySuggestions(currentUserGuard.requireCurrentUser(), status, pageable));
    }

    // Admin inbox (PENDING) or archive (CLOSED)
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Page<SuggestionSummaryDTO>> getInbox(
            @RequestParam(defaultValue = "PENDING") SuggestionStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(suggestionService.getInbox(currentUserGuard.requireCurrentUser(), status, pageable));
    }

    // Admin badge counters: PENDING, CLOSED and UNREAD
    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Long>> getStatusCounts() {
        return ResponseEntity.ok(suggestionService.getSuggestionStatusCounts());
    }

    // Owner or admin: the full conversation; marks it read for the caller
    @GetMapping("/{id}")
    public ResponseEntity<SuggestionDetailDTO> getSuggestion(@PathVariable Long id) {
        return ResponseEntity.ok(suggestionService.getSuggestion(currentUserGuard.requireCurrentUser(), id));
    }

    // Owner or admin replies, optionally with a file; 409 once the suggestion is closed
    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuggestionMessageDTO> postMessage(
            @PathVariable Long id,
            @RequestParam String body,
            @RequestParam(required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                suggestionService.postMessage(currentUserGuard.requireCurrentUser(), id, body, file));
    }

    // Admin closes (archives) a suggestion for good
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<SuggestionDetailDTO> closeSuggestion(@PathVariable Long id) {
        return ResponseEntity.ok(suggestionService.closeSuggestion(currentUserGuard.requireCurrentUser(), id));
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<Resource> downloadSuggestionAttachment(@PathVariable Long id) {
        return fileResponse(suggestionService.openSuggestionAttachment(currentUserGuard.requireCurrentUser(), id));
    }

    @GetMapping("/{id}/messages/{messageId}/attachment")
    public ResponseEntity<Resource> downloadMessageAttachment(@PathVariable Long id, @PathVariable Long messageId) {
        return fileResponse(
                suggestionService.openMessageAttachment(currentUserGuard.requireCurrentUser(), id, messageId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteSuggestion(@PathVariable Long id) {
        suggestionService.deleteSuggestion(id);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<Resource> fileResponse(DownloadableFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(file.getResource());
    }
}
