package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Entities.Suggestion;
import com.example.MedRational.Entities.SuggestionAttachment;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.FavoriteRepository;
import com.example.MedRational.Repositories.FileDownloadEventRepository;
import com.example.MedRational.Repositories.FileRatingRepository;
import com.example.MedRational.Repositories.FileViewEventRepository;
import com.example.MedRational.Repositories.NotificationRepository;
import com.example.MedRational.Repositories.SuggestionRepository;
import com.example.MedRational.Repositories.UserFileDownloadRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Repositories.WorkshopProjectRepository;
import com.example.MedRational.Services.Interfaces.AccountDeletionService;
import com.example.MedRational.Services.Interfaces.EmailService;
import com.example.MedRational.Services.Support.OtpCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

// Deletes what can identify the user (account, favorites, recent downloads, own notifications,
// suggestions and their files). What only feeds counts stays with the user set to null: download and
// view events, ratings (so averages don't move), and an admin's projects and replies.
// user_daily_activity goes with the user row (ON DELETE CASCADE).
@Service
@RequiredArgsConstructor
public class AccountDeletionServiceImpl implements AccountDeletionService {

    static final String EMAIL_SUBJECT = "Confirm the deletion of your MedRational account";

    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserFileDownloadRepository userFileDownloadRepository;
    private final NotificationRepository notificationRepository;
    private final SuggestionRepository suggestionRepository;
    private final FileDownloadEventRepository downloadEventRepository;
    private final FileViewEventRepository viewEventRepository;
    private final FileRatingRepository ratingRepository;
    private final WorkshopProjectRepository workshopProjectRepository;
    private final SuggestionAttachmentStore attachmentStore;
    private final EmailService emailService;
    private final Clock clock;

    @Override
    @Transactional
    public String requestCode(User user) {
        String code = OtpCodes.issue(user, clock);
        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), code, EMAIL_SUBJECT);
        return "Verification code sent to " + user.getEmail();
    }

    @Override
    @Transactional
    public void requestCode(String email) {
        userRepository.findByEmail(normalize(email)).ifPresent(this::requestCode);
    }

    @Override
    @Transactional
    public void deleteAccount(User user, String code) {
        OtpCodes.consume(user, code, clock);
        delete(user);
    }

    @Override
    @Transactional
    public void deleteAccount(String email, String code) {
        // Same answer as a wrong code, so unknown emails can't be told apart
        User user = userRepository.findByEmail(normalize(email))
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification code."));
        deleteAccount(user, code);
    }

    private void delete(User user) {
        Long userId = user.getId();
        // Suggestions go through the entity so their messages cascade
        List<Suggestion> suggestions = suggestionRepository.findAllByUserId(userId);
        List<SuggestionAttachment> attachments = new ArrayList<>();
        suggestions.forEach(s -> {
            attachments.add(s.getAttachment());
            s.getMessages().forEach(m -> attachments.add(m.getAttachment()));
        });
        suggestionRepository.deleteAll(suggestions);
        suggestionRepository.flush();

        favoriteRepository.deleteAllByUserId(userId);
        userFileDownloadRepository.deleteAllByUserId(userId);
        notificationRepository.deleteAllByUserId(userId);

        downloadEventRepository.anonymizeByUserId(userId);
        viewEventRepository.anonymizeByUserId(userId);
        ratingRepository.anonymizeByUserId(userId);
        workshopProjectRepository.anonymizeByAuthorId(userId);
        suggestionRepository.anonymizeMessagesByAuthorId(userId);

        // Through the entity, so the code just cleared on it isn't flushed to a deleted row
        userRepository.delete(user);
        // Fail here, before touching R2, if a reference to the user was missed
        userRepository.flush();

        // Best effort: a leftover object in R2 must not undo the deletion
        attachmentStore.deleteAll(attachments);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
