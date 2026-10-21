package com.example.MedRational.Services;

import com.example.MedRational.DTOs.SuggestionDetailDTO;
import com.example.MedRational.DTOs.SuggestionMessageDTO;
import com.example.MedRational.DTOs.SuggestionSummaryDTO;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.Suggestion;
import com.example.MedRational.Entities.SuggestionAttachment;
import com.example.MedRational.Entities.SuggestionMessage;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Entities.enums.SuggestionStatus;
import com.example.MedRational.Exceptions.SuggestionClosedException;
import com.example.MedRational.Repositories.SuggestionRepository;
import com.example.MedRational.Services.Implementations.SuggestionAttachmentStore;
import com.example.MedRational.Services.Implementations.SuggestionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestionServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final SuggestionRepository repository = mock(SuggestionRepository.class);
    private final SuggestionAttachmentStore attachmentStore = mock(SuggestionAttachmentStore.class);
    private final SuggestionServiceImpl service =
            new SuggestionServiceImpl(repository, attachmentStore, Clock.fixed(NOW, ZoneOffset.UTC));

    private final User owner = User.builder().id(1L).email("student@example.com").fullName("Sara Student")
            .phoneNumber("+212600000000").role(Role.ROLE_USER).build();
    private final User stranger = User.builder().id(2L).email("other@example.com").role(Role.ROLE_USER).build();
    private final User admin = User.builder().id(3L).email("admin@example.com").fullName("Dr Admin")
            .role(Role.ROLE_ADMIN).build();

    private final AtomicLong ids = new AtomicLong(100);
    private final MockMultipartFile pdf = new MockMultipartFile("file", "notes.pdf", "application/pdf", "%PDF".getBytes());
    private final SuggestionAttachment storedPdf = SuggestionAttachment.builder()
            .storageKey("Suggestions/10/abc-notes.pdf").fileName("notes.pdf").contentType("application/pdf").sizeBytes(4L)
            .build();

    @BeforeEach
    void setUp() {
        // Simulates the database: assigns ids on save and echoes the entity back
        when(repository.save(any(Suggestion.class))).thenAnswer(invocation -> {
            Suggestion s = invocation.getArgument(0);
            if (s.getId() == null) s.setId(10L);
            return s;
        });
        when(repository.saveAndFlush(any(Suggestion.class))).thenAnswer(invocation -> {
            Suggestion s = invocation.getArgument(0);
            s.getMessages().forEach(m -> { if (m.getId() == null) m.setId(ids.incrementAndGet()); });
            return s;
        });
    }

    private Suggestion openSuggestion() {
        Suggestion s = Suggestion.builder()
                .id(10L).user(owner).title("Add ECG atlas").description("Please add more ECGs")
                .status(SuggestionStatus.PENDING).createdAt(NOW_LOCAL.minusDays(2))
                .lastUserActivityAt(NOW_LOCAL.minusDays(2)).lastActivityAt(NOW_LOCAL.minusDays(2))
                .userLastReadAt(NOW_LOCAL.minusDays(2)).messageCount(0)
                .build();
        when(repository.findWithThreadById(10L)).thenReturn(Optional.of(s));
        when(repository.findById(10L)).thenReturn(Optional.of(s));
        return s;
    }

    // --- create ---------------------------------------------------------------------------------

    @Test
    void createStoresTheAttachmentUnderTheNewSuggestion() {
        when(attachmentStore.store(pdf, 10L)).thenReturn(storedPdf);

        SuggestionDetailDTO detail = service.createSuggestion(owner, "  Add ECG atlas ", "Please add more ECGs", pdf);

        verify(attachmentStore).validate(pdf);
        verify(attachmentStore).store(pdf, 10L);
        assertEquals("Add ECG atlas", detail.getTitle());
        assertEquals(SuggestionStatus.PENDING, detail.getStatus());
        assertEquals("notes.pdf", detail.getAttachment().getFileName());
        assertEquals(1L, detail.getSender().getUserId());
    }

    @Test
    void createWithoutFileHasNoAttachment() {
        SuggestionDetailDTO detail = service.createSuggestion(owner, "Title", "Description", null);

        verify(attachmentStore, never()).validate(any());
        assertNull(detail.getAttachment());
    }

    @Test
    void invalidAttachmentIsRejectedBeforeAnythingIsSaved() {
        doThrow(new com.example.MedRational.Exceptions.AttachmentTooLargeException("too big"))
                .when(attachmentStore).validate(pdf);

        assertThrows(com.example.MedRational.Exceptions.AttachmentTooLargeException.class,
                () -> service.createSuggestion(owner, "Title", "Description", pdf));
        verify(repository, never()).save(any());
    }

    @Test
    void blankTitleIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createSuggestion(owner, "   ", "Description", null));
        verify(repository, never()).save(any());
    }

    @Test
    void tooLongTitleIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createSuggestion(owner, "x".repeat(151), "Description", null));
    }

    // --- lists ----------------------------------------------------------------------------------

    @Test
    void mySuggestionsOnlyQueriesTheCallersOwn() {
        Suggestion own = openSuggestion();
        when(repository.findByUserIdAndStatusOrderByLastActivityAtDesc(eq(1L), eq(SuggestionStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(own)));

        List<SuggestionSummaryDTO> mine =
                service.getMySuggestions(owner, SuggestionStatus.PENDING, PageRequest.of(0, 20)).getContent();

        assertEquals(1, mine.size());
        verify(repository).findByUserIdAndStatusOrderByLastActivityAtDesc(eq(1L), eq(SuggestionStatus.PENDING), any());
    }

    @Test
    void inboxIsAdminOnly() {
        assertThrows(AccessDeniedException.class,
                () -> service.getInbox(owner, SuggestionStatus.PENDING, PageRequest.of(0, 20)));
    }

    @Test
    void unreadForAdminWhenTheUserPostedSinceTheAdminLastLooked() {
        Suggestion s = openSuggestion();
        assertTrue(SuggestionServiceImpl.isUnread(s, true));

        s.setAdminLastReadAt(NOW_LOCAL.minusDays(1));
        assertFalse(SuggestionServiceImpl.isUnread(s, true));

        s.setLastUserActivityAt(NOW_LOCAL);
        assertTrue(SuggestionServiceImpl.isUnread(s, true));
    }

    @Test
    void unreadForUserOnlyAfterAnAdminReply() {
        Suggestion s = openSuggestion();
        assertFalse(SuggestionServiceImpl.isUnread(s, false));

        s.setLastAdminActivityAt(NOW_LOCAL.minusDays(1));
        assertTrue(SuggestionServiceImpl.isUnread(s, false));

        s.setUserLastReadAt(NOW_LOCAL);
        assertFalse(SuggestionServiceImpl.isUnread(s, false));
    }

    // --- detail and access ----------------------------------------------------------------------

    @Test
    void ownerCanOpenTheirSuggestionAndItIsMarkedRead() {
        Suggestion s = openSuggestion();

        SuggestionDetailDTO detail = service.getSuggestion(owner, 10L);

        assertEquals("Add ECG atlas", detail.getTitle());
        assertEquals(NOW_LOCAL, s.getUserLastReadAt());
        assertNull(s.getAdminLastReadAt());
    }

    @Test
    void adminCanOpenAnySuggestionAndSeesTheSender() {
        Suggestion s = openSuggestion();

        SuggestionDetailDTO detail = service.getSuggestion(admin, 10L);

        assertEquals("Sara Student", detail.getSender().getFullName());
        assertEquals("+212600000000", detail.getSender().getPhoneNumber());
        assertEquals(NOW_LOCAL, s.getAdminLastReadAt());
    }

    @Test
    void previewableAttachmentsAreFlagged() {
        Suggestion s = openSuggestion();
        s.setAttachment(storedPdf);
        assertTrue(service.getSuggestion(admin, 10L).getAttachment().isPreviewable());

        s.setAttachment(SuggestionAttachment.builder()
                .storageKey("Suggestions/10/abc-notes.txt").fileName("notes.txt").contentType("text/plain").build());
        assertFalse(service.getSuggestion(admin, 10L).getAttachment().isPreviewable());
    }

    @Test
    void anotherUserCannotOpenIt() {
        openSuggestion();

        assertThrows(AccessDeniedException.class, () -> service.getSuggestion(stranger, 10L));
    }

    @Test
    void anotherUserCannotDownloadItsAttachment() {
        openSuggestion().setAttachment(storedPdf);

        assertThrows(AccessDeniedException.class, () -> service.openSuggestionAttachment(stranger, 10L));
        verify(attachmentStore, never()).open(any());
    }

    // --- messages -------------------------------------------------------------------------------

    @Test
    void userReplyUpdatesTheUserSide() {
        Suggestion s = openSuggestion();

        SuggestionMessageDTO message = service.postMessage(owner, 10L, " Any update? ", null);

        assertFalse(message.isFromAdmin());
        assertEquals("Any update?", message.getBody());
        assertEquals("Sara Student", message.getAuthorName());
        assertEquals(1, s.getMessageCount());
        assertEquals(NOW_LOCAL, s.getLastUserActivityAt());
        assertEquals(NOW_LOCAL, s.getLastActivityAt());
        assertNull(s.getLastAdminActivityAt());
    }

    @Test
    void adminReplyWithAFileUpdatesTheAdminSide() {
        Suggestion s = openSuggestion();
        when(attachmentStore.store(pdf, 10L)).thenReturn(storedPdf);

        SuggestionMessageDTO message = service.postMessage(admin, 10L, "Added, thanks!", pdf);

        assertTrue(message.isFromAdmin());
        assertEquals("Admin", message.getAuthorName());
        assertEquals("notes.pdf", message.getAttachment().getFileName());
        assertEquals(NOW_LOCAL, s.getLastAdminActivityAt());
        assertEquals(NOW_LOCAL, s.getAdminLastReadAt());
        assertSame(storedPdf, s.getMessages().get(0).getAttachment());
    }

    @Test
    void strangerCannotReply() {
        openSuggestion();

        assertThrows(AccessDeniedException.class, () -> service.postMessage(stranger, 10L, "Hi", null));
    }

    @Test
    void blankReplyIsRejected() {
        openSuggestion();

        assertThrows(IllegalArgumentException.class, () -> service.postMessage(owner, 10L, "  ", null));
    }

    @Test
    void nobodyCanReplyOnceClosed() {
        openSuggestion().setStatus(SuggestionStatus.CLOSED);

        assertThrows(SuggestionClosedException.class, () -> service.postMessage(owner, 10L, "Hello?", null));
        assertThrows(SuggestionClosedException.class, () -> service.postMessage(admin, 10L, "One more thing", null));
        verify(attachmentStore, never()).store(any(), anyLong());
    }

    // --- closing --------------------------------------------------------------------------------

    @Test
    void adminClosesASuggestion() {
        Suggestion s = openSuggestion();

        SuggestionDetailDTO detail = service.closeSuggestion(admin, 10L);

        assertEquals(SuggestionStatus.CLOSED, detail.getStatus());
        assertEquals(NOW_LOCAL, s.getClosedAt());
    }

    @Test
    void closingTwiceIsAConflict() {
        openSuggestion().setStatus(SuggestionStatus.CLOSED);

        assertThrows(SuggestionClosedException.class, () -> service.closeSuggestion(admin, 10L));
    }

    @Test
    void usersCannotClose() {
        openSuggestion();

        assertThrows(AccessDeniedException.class, () -> service.closeSuggestion(owner, 10L));
    }

    // Authors who deleted their account leave their messages with no author
    @Test
    void messagesWhoseAuthorDeletedTheirAccountStillShow() {
        Suggestion s = openSuggestion();
        s.getMessages().add(SuggestionMessage.builder().id(5L).suggestion(s).author(null).fromAdmin(true)
                .body("We added it").build());
        s.getMessages().add(SuggestionMessage.builder().id(6L).suggestion(s).author(null).fromAdmin(false)
                .body("Thanks").build());

        SuggestionDetailDTO detail = service.getSuggestion(admin, 10L);

        assertEquals("Admin", detail.getMessages().get(0).getAuthorName());
        assertNull(detail.getMessages().get(1).getAuthorName());
        assertEquals("Thanks", detail.getMessages().get(1).getBody());
    }

    // --- delete and counts ----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void deletingRemovesEveryAttachmentFromStorage() {
        Suggestion s = openSuggestion();
        s.setAttachment(storedPdf);
        SuggestionAttachment replyFile = SuggestionAttachment.builder().storageKey("Suggestions/10/reply.png").build();
        s.getMessages().add(SuggestionMessage.builder().id(5L).suggestion(s).author(admin).attachment(replyFile).build());

        service.deleteSuggestion(10L);

        verify(repository).delete(s);
        ArgumentCaptor<List<SuggestionAttachment>> deleted = ArgumentCaptor.forClass(List.class);
        verify(attachmentStore).deleteAll(deleted.capture());
        assertTrue(deleted.getValue().containsAll(List.of(storedPdf, replyFile)));
    }

    @Test
    void countsIncludeUnread() {
        when(repository.countByStatus(SuggestionStatus.PENDING)).thenReturn(4L);
        when(repository.countByStatus(SuggestionStatus.CLOSED)).thenReturn(7L);
        when(repository.countUnreadByAdmin()).thenReturn(2L);

        assertEquals(Map.of("PENDING", 4L, "CLOSED", 7L, "UNREAD", 2L), service.getSuggestionStatusCounts());
    }
}
