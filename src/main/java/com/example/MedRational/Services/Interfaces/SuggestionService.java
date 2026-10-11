package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.DTOs.SuggestionDetailDTO;
import com.example.MedRational.DTOs.SuggestionMessageDTO;
import com.example.MedRational.DTOs.SuggestionSummaryDTO;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Suggestions are visible to the user who sent them and to admins only; every method that takes a
 * caller enforces that (403 otherwise). Closed suggestions are read-only (409).
 */
public interface SuggestionService {

    // A signed-in user sends a suggestion, optionally with one attachment
    SuggestionDetailDTO createSuggestion(User caller, String title, String description, MultipartFile file);

    // The caller's own suggestions with the given status
    Page<SuggestionSummaryDTO> getMySuggestions(User caller, SuggestionStatus status, Pageable pageable);

    // Admin inbox (PENDING) or archive (CLOSED)
    Page<SuggestionSummaryDTO> getInbox(User admin, SuggestionStatus status, Pageable pageable);

    // Full thread; also marks it read for the caller's side
    SuggestionDetailDTO getSuggestion(User caller, Long suggestionId);

    SuggestionMessageDTO postMessage(User caller, Long suggestionId, String body, MultipartFile file);

    // Admin only; permanent
    SuggestionDetailDTO closeSuggestion(User admin, Long suggestionId);

    DownloadableFile openSuggestionAttachment(User caller, Long suggestionId);

    DownloadableFile openMessageAttachment(User caller, Long suggestionId, Long messageId);

    // Admin only; also removes the attachments from storage
    void deleteSuggestion(Long suggestionId);

    // Counts per status plus UNREAD (pending suggestions with new user activity) for admin badges
    Map<String, Long> getSuggestionStatusCounts();
}
