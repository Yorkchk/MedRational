package com.example.MedRational.Services;

import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.Suggestion;
import com.example.MedRational.Entities.SuggestionAttachment;
import com.example.MedRational.Entities.SuggestionMessage;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.FavoriteRepository;
import com.example.MedRational.Repositories.FileDownloadEventRepository;
import com.example.MedRational.Repositories.FileRatingRepository;
import com.example.MedRational.Repositories.FileViewEventRepository;
import com.example.MedRational.Repositories.NotificationRepository;
import com.example.MedRational.Repositories.SuggestionRepository;
import com.example.MedRational.Repositories.UserFileDownloadRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Repositories.UserSessionRepository;
import com.example.MedRational.Repositories.WorkshopProjectRepository;
import com.example.MedRational.Services.Implementations.AccountDeletionServiceImpl;
import com.example.MedRational.Services.Implementations.SuggestionAttachmentStore;
import com.example.MedRational.Services.Interfaces.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final long USER_ID = 7L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FavoriteRepository favoriteRepository = mock(FavoriteRepository.class);
    private final UserFileDownloadRepository userFileDownloadRepository = mock(UserFileDownloadRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final SuggestionRepository suggestionRepository = mock(SuggestionRepository.class);
    private final FileDownloadEventRepository downloadEventRepository = mock(FileDownloadEventRepository.class);
    private final FileViewEventRepository viewEventRepository = mock(FileViewEventRepository.class);
    private final FileRatingRepository ratingRepository = mock(FileRatingRepository.class);
    private final WorkshopProjectRepository workshopProjectRepository = mock(WorkshopProjectRepository.class);
    private final SuggestionAttachmentStore attachmentStore = mock(SuggestionAttachmentStore.class);
    private final EmailService emailService = mock(EmailService.class);
    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);

    private final AccountDeletionServiceImpl service = new AccountDeletionServiceImpl(
            userRepository, favoriteRepository, userFileDownloadRepository, notificationRepository,
            suggestionRepository, downloadEventRepository, viewEventRepository, ratingRepository,
            workshopProjectRepository, attachmentStore, emailService, CLOCK, sessionRepository);

    private User user() {
        return User.builder().id(USER_ID).email("sara@example.com").fullName("Sara Idrissi")
                .phoneNumber("0612345678").role(Role.ROLE_USER).build();
    }

    private User withCode(User user, String code, LocalDateTime expiry) {
        user.setOtpCode(code);
        user.setOtpExpiry(expiry);
        return user;
    }

    // --- code -----------------------------------------------------------------------------------

    @Test
    void theCodeIsEmailedToTheSignedInUser() {
        User user = user();

        String message = service.requestCode(user);

        assertNotNull(user.getOtpCode());
        assertEquals(NOW.plusMinutes(5), user.getOtpExpiry());
        verify(userRepository).save(user);
        verify(emailService).sendOtpEmail(eq("sara@example.com"), eq(user.getOtpCode()), anyString());
        assertEquals("Verification code sent to sara@example.com", message);
    }

    @Test
    void theWebPageSendsTheCodeToTheAccountWithThatEmail() {
        User user = user();
        when(userRepository.findByEmail("sara@example.com")).thenReturn(Optional.of(user));

        service.requestCode("  Sara@Example.com ");

        verify(emailService).sendOtpEmail(eq("sara@example.com"), eq(user.getOtpCode()), anyString());
    }

    // The page answers the same either way, so nobody learns who has an account
    @Test
    void anUnknownEmailGetsNoCodeAndNoError() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestCode("nobody@example.com");

        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    // --- deletion -------------------------------------------------------------------------------

    @Test
    void aWrongCodeDeletesNothing() {
        User user = withCode(user(), "123456", NOW.plusMinutes(3));

        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount(user, "654321"));

        verify(userRepository, never()).delete(any());
        verify(favoriteRepository, never()).deleteAllByUserId(anyLong());
        verify(suggestionRepository, never()).findAllByUserId(anyLong());
    }

    @Test
    void anExpiredCodeDeletesNothing() {
        User user = withCode(user(), "123456", NOW.minusMinutes(1));

        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount(user, "123456"));

        verify(userRepository, never()).delete(any());
    }

    @Test
    void theRightCodeDeletesWhatIdentifiesTheUserAndAnonymisesTheRest() {
        User user = withCode(user(), "123456", NOW.plusMinutes(3));
        when(suggestionRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        service.deleteAccount(user, "123456");

        verify(favoriteRepository).deleteAllByUserId(USER_ID);
        verify(userFileDownloadRepository).deleteAllByUserId(USER_ID);
        verify(notificationRepository).deleteAllByUserId(USER_ID);
        // Every device is signed out
        verify(sessionRepository).deleteAllByUserId(USER_ID);
        verify(downloadEventRepository).anonymizeByUserId(USER_ID);
        verify(viewEventRepository).anonymizeByUserId(USER_ID);
        verify(ratingRepository).anonymizeByUserId(USER_ID);
        verify(workshopProjectRepository).anonymizeByAuthorId(USER_ID);
        verify(suggestionRepository).anonymizeMessagesByAuthorId(USER_ID);
        verify(userRepository).delete(user);
    }

    // Otherwise the foreign keys reject the delete
    @Test
    void theUserRowGoesLast() {
        User user = withCode(user(), "123456", NOW.plusMinutes(3));
        when(suggestionRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        service.deleteAccount(user, "123456");

        InOrder order = inOrder(favoriteRepository, sessionRepository, ratingRepository, workshopProjectRepository,
                suggestionRepository, userRepository);
        order.verify(favoriteRepository).deleteAllByUserId(USER_ID);
        order.verify(sessionRepository).deleteAllByUserId(USER_ID);
        order.verify(ratingRepository).anonymizeByUserId(USER_ID);
        order.verify(workshopProjectRepository).anonymizeByAuthorId(USER_ID);
        order.verify(suggestionRepository).anonymizeMessagesByAuthorId(USER_ID);
        order.verify(userRepository).delete(user);
        order.verify(userRepository).flush();
    }

    @Test
    @SuppressWarnings("unchecked")
    void theUsersSuggestionsAndTheirFilesAreDeleted() {
        User user = withCode(user(), "123456", NOW.plusMinutes(3));
        SuggestionAttachment own = SuggestionAttachment.builder().storageKey("Suggestions/1/notes.pdf").build();
        SuggestionAttachment reply = SuggestionAttachment.builder().storageKey("Suggestions/1/reply.png").build();
        Suggestion suggestion = Suggestion.builder().id(1L).user(user).attachment(own).build();
        suggestion.getMessages().add(SuggestionMessage.builder().id(2L).suggestion(suggestion).attachment(reply).build());
        when(suggestionRepository.findAllByUserId(USER_ID)).thenReturn(List.of(suggestion));

        service.deleteAccount(user, "123456");

        verify(suggestionRepository).deleteAll(List.of(suggestion));
        ArgumentCaptor<List<SuggestionAttachment>> deleted = ArgumentCaptor.forClass(List.class);
        verify(attachmentStore).deleteAll(deleted.capture());
        assertTrue(deleted.getValue().containsAll(List.of(own, reply)));
    }

    @Test
    void theWebPageDeletesTheAccountWithThatEmail() {
        User user = withCode(user(), "123456", NOW.plusMinutes(3));
        when(userRepository.findByEmail("sara@example.com")).thenReturn(Optional.of(user));
        when(suggestionRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        service.deleteAccount("Sara@example.com", "123456");

        verify(userRepository).delete(user);
    }

    // Same error as a wrong code, so the page doesn't reveal which emails have an account
    @Test
    void anUnknownEmailAnswersLikeAWrongCode() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.deleteAccount("nobody@example.com", "123456"));

        assertEquals("Invalid verification code.", error.getMessage());
        verify(userRepository, never()).delete(any());
    }
}
