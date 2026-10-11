package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.DTOs.SuggestionAttachmentDTO;
import com.example.MedRational.DTOs.SuggestionDetailDTO;
import com.example.MedRational.DTOs.SuggestionMessageDTO;
import com.example.MedRational.DTOs.SuggestionSenderDTO;
import com.example.MedRational.DTOs.SuggestionSummaryDTO;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.Suggestion;
import com.example.MedRational.Entities.SuggestionAttachment;
import com.example.MedRational.Entities.SuggestionMessage;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import com.example.MedRational.Exceptions.SuggestionClosedException;
import com.example.MedRational.Repositories.SuggestionRepository;
import com.example.MedRational.Services.Interfaces.SuggestionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SuggestionServiceImpl implements SuggestionService {

    static final int MAX_TITLE_LENGTH = 150;
    static final int MAX_TEXT_LENGTH = 5000;

    private final SuggestionRepository suggestionRepository;
    private final SuggestionAttachmentStore attachmentStore;
    private final Clock clock;

    @Override
    @Transactional
    public SuggestionDetailDTO createSuggestion(User caller, String title, String description, MultipartFile file) {
        String cleanTitle = requireText(title, "Title", MAX_TITLE_LENGTH);
        String cleanDescription = requireText(description, "Description", MAX_TEXT_LENGTH);
        // Reject a bad file before anything is saved
        if (file != null && !file.isEmpty()) {
            attachmentStore.validate(file);
        }

        LocalDateTime now = now();
        Suggestion suggestion = suggestionRepository.save(Suggestion.builder()
                .user(caller)
                .title(cleanTitle)
                .description(cleanDescription)
                .status(SuggestionStatus.PENDING)
                .lastUserActivityAt(now)
                .lastActivityAt(now)
                .userLastReadAt(now)
                .messageCount(0)
                .build());

        // Stored after the first save so the file lands under the suggestion's id
        suggestion.setAttachment(attachmentStore.store(file, suggestion.getId()));
        return toDetail(suggestionRepository.save(suggestion));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SuggestionSummaryDTO> getMySuggestions(User caller, SuggestionStatus status, Pageable pageable) {
        return suggestionRepository
                .findByUserIdAndStatusOrderByLastActivityAtDesc(caller.getId(), status, pageable)
                .map(s -> toSummary(s, false));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SuggestionSummaryDTO> getInbox(User admin, SuggestionStatus status, Pageable pageable) {
        requireAdmin(admin);
        return suggestionRepository.findByStatusOrderByLastActivityAtDesc(status, pageable)
                .map(s -> toSummary(s, true));
    }

    @Override
    @Transactional
    public SuggestionDetailDTO getSuggestion(User caller, Long suggestionId) {
        Suggestion suggestion = loadThread(suggestionId);
        boolean admin = requireAccess(caller, suggestion);
        if (admin) {
            suggestion.setAdminLastReadAt(now());
        } else {
            suggestion.setUserLastReadAt(now());
        }
        return toDetail(suggestion);
    }

    @Override
    @Transactional
    public SuggestionMessageDTO postMessage(User caller, Long suggestionId, String body, MultipartFile file) {
        Suggestion suggestion = loadThread(suggestionId);
        boolean admin = requireAccess(caller, suggestion);
        requireOpen(suggestion);
        String cleanBody = requireText(body, "Message", MAX_TEXT_LENGTH);

        LocalDateTime now = now();
        SuggestionMessage message = SuggestionMessage.builder()
                .suggestion(suggestion)
                .author(caller)
                .fromAdmin(admin)
                .body(cleanBody)
                .attachment(attachmentStore.store(file, suggestion.getId()))
                .createdAt(now)
                .build();
        suggestion.getMessages().add(message);
        suggestion.setMessageCount(count(suggestion.getMessageCount()) + 1);
        suggestion.setLastActivityAt(now);
        // Posting means you've seen everything so far
        if (admin) {
            suggestion.setLastAdminActivityAt(now);
            suggestion.setAdminLastReadAt(now);
        } else {
            suggestion.setLastUserActivityAt(now);
            suggestion.setUserLastReadAt(now);
        }
        suggestionRepository.saveAndFlush(suggestion);
        return toMessage(message);
    }

    @Override
    @Transactional
    public SuggestionDetailDTO closeSuggestion(User admin, Long suggestionId) {
        requireAdmin(admin);
        Suggestion suggestion = loadThread(suggestionId);
        requireOpen(suggestion);
        suggestion.setStatus(SuggestionStatus.CLOSED);
        suggestion.setClosedAt(now());
        return toDetail(suggestionRepository.save(suggestion));
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadableFile openSuggestionAttachment(User caller, Long suggestionId) {
        Suggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> notFound(suggestionId));
        requireAccess(caller, suggestion);
        return attachmentStore.open(requireAttachment(suggestion.getAttachment()));
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadableFile openMessageAttachment(User caller, Long suggestionId, Long messageId) {
        Suggestion suggestion = loadThread(suggestionId);
        requireAccess(caller, suggestion);
        SuggestionMessage message = suggestion.getMessages().stream()
                .filter(m -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Message not found with ID: " + messageId));
        return attachmentStore.open(requireAttachment(message.getAttachment()));
    }

    @Override
    @Transactional
    public void deleteSuggestion(Long suggestionId) {
        Suggestion suggestion = loadThread(suggestionId);
        List<SuggestionAttachment> attachments = new ArrayList<>();
        attachments.add(suggestion.getAttachment());
        suggestion.getMessages().forEach(m -> attachments.add(m.getAttachment()));
        suggestionRepository.delete(suggestion);
        attachmentStore.deleteAll(attachments);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getSuggestionStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        Arrays.stream(SuggestionStatus.values())
                .forEach(status -> counts.put(status.name(), suggestionRepository.countByStatus(status)));
        counts.put("UNREAD", suggestionRepository.countUnreadByAdmin());
        return counts;
    }

    // --- access rules -----------------------------------------------------------------------

    static boolean isAdmin(User user) {
        return user.getRole() == Role.ROLE_ADMIN;
    }

    // Returns whether the caller acts as admin; anyone else than the owner or an admin gets 403
    private boolean requireAccess(User caller, Suggestion suggestion) {
        if (isAdmin(caller)) {
            return true;
        }
        if (suggestion.getUser().getId().equals(caller.getId())) {
            return false;
        }
        throw new AccessDeniedException("You can only access your own suggestions");
    }

    private void requireAdmin(User caller) {
        if (!isAdmin(caller)) {
            throw new AccessDeniedException("Only admins can do this");
        }
    }

    private void requireOpen(Suggestion suggestion) {
        if (suggestion.getStatus() == SuggestionStatus.CLOSED) {
            throw new SuggestionClosedException("This suggestion is closed and archived.");
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private Suggestion loadThread(Long suggestionId) {
        return suggestionRepository.findWithThreadById(suggestionId).orElseThrow(() -> notFound(suggestionId));
    }

    private static EntityNotFoundException notFound(Long suggestionId) {
        return new EntityNotFoundException("Suggestion not found with ID: " + suggestionId);
    }

    private static SuggestionAttachment requireAttachment(SuggestionAttachment attachment) {
        if (attachment == null || attachment.getStorageKey() == null) {
            throw new EntityNotFoundException("No attachment");
        }
        return attachment;
    }

    private static String requireText(String value, String field, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }

    // --- mapping -------------------------------------------------------------------------------

    // Unread means the other side posted after the caller's side last opened it
    public static boolean isUnread(Suggestion s, boolean forAdmin) {
        LocalDateTime otherSideActivity = forAdmin ? s.getLastUserActivityAt() : s.getLastAdminActivityAt();
        LocalDateTime lastRead = forAdmin ? s.getAdminLastReadAt() : s.getUserLastReadAt();
        return otherSideActivity != null && (lastRead == null || otherSideActivity.isAfter(lastRead));
    }

    private SuggestionSummaryDTO toSummary(Suggestion s, boolean forAdmin) {
        User sender = s.getUser();
        return SuggestionSummaryDTO.builder()
                .id(s.getId())
                .title(s.getTitle())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .lastActivityAt(s.getLastActivityAt() != null ? s.getLastActivityAt() : s.getCreatedAt())
                .messageCount(count(s.getMessageCount()))
                .unread(isUnread(s, forAdmin))
                .senderName(displayName(sender))
                .senderEmail(sender.getEmail())
                .build();
    }

    private SuggestionDetailDTO toDetail(Suggestion s) {
        User sender = s.getUser();
        return SuggestionDetailDTO.builder()
                .id(s.getId())
                .title(s.getTitle())
                .description(s.getDescription())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .lastActivityAt(s.getLastActivityAt() != null ? s.getLastActivityAt() : s.getCreatedAt())
                .closedAt(s.getClosedAt())
                .attachment(toAttachment(s.getAttachment()))
                .sender(SuggestionSenderDTO.builder()
                        .userId(sender.getId())
                        .fullName(sender.getFullName())
                        .email(sender.getEmail())
                        .phoneNumber(sender.getPhoneNumber())
                        .build())
                .messages(s.getMessages().stream().map(this::toMessage).toList())
                .build();
    }

    private SuggestionMessageDTO toMessage(SuggestionMessage m) {
        return SuggestionMessageDTO.builder()
                .id(m.getId())
                .fromAdmin(m.isFromAdmin())
                .authorName(m.isFromAdmin() ? "Admin" : displayName(m.getAuthor()))
                .body(m.getBody())
                .attachment(toAttachment(m.getAttachment()))
                .createdAt(m.getCreatedAt())
                .build();
    }

    private static SuggestionAttachmentDTO toAttachment(SuggestionAttachment a) {
        if (a == null || a.getStorageKey() == null) {
            return null;
        }
        return SuggestionAttachmentDTO.builder()
                .fileName(a.getFileName())
                .contentType(a.getContentType())
                .sizeBytes(a.getSizeBytes())
                .build();
    }

    private static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getEmail();
    }
}
