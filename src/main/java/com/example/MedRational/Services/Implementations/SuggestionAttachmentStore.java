package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Config.SuggestionAttachmentProperties;
import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Entities.SuggestionAttachment;
import com.example.MedRational.Exceptions.AttachmentTooLargeException;
import com.example.MedRational.Exceptions.UnsupportedMediaTypeException;
import com.example.MedRational.Services.Interfaces.R2StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Validates, stores and serves the private files attached to suggestions and their replies. */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SuggestionAttachmentProperties.class)
public class SuggestionAttachmentStore {

    private static final String PREFIX = "Suggestions/";
    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private final R2StorageService r2StorageService;
    private final SuggestionAttachmentProperties properties;

    /** Returns null when no file was sent. */
    public SuggestionAttachment store(MultipartFile file, Long suggestionId) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        validate(file);
        try {
            String storageKey = r2StorageService.uploadFile(file, PREFIX + suggestionId);
            return SuggestionAttachment.builder()
                    .storageKey(storageKey)
                    .fileName(displayName(file))
                    .contentType(file.getContentType() != null ? file.getContentType() : FALLBACK_CONTENT_TYPE)
                    .sizeBytes(file.getSize())
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't store the attachment", e);
        }
    }

    public void validate(MultipartFile file) {
        long max = properties.getMaxSize().toBytes();
        if (file.getSize() > max) {
            throw new AttachmentTooLargeException(
                    "Attachments must be " + properties.getMaxSize().toMegabytes() + " MB or smaller.");
        }
        String extension = extension(displayName(file));
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new UnsupportedMediaTypeException("Attachments must be one of: "
                    + String.join(", ", properties.getAllowedExtensions()) + ".");
        }
    }

    public DownloadableFile open(SuggestionAttachment attachment) {
        return new DownloadableFile(
                attachment.getFileName(),
                attachment.getContentType() != null ? attachment.getContentType() : FALLBACK_CONTENT_TYPE,
                r2StorageService.downloadFileAsResource(attachment.getStorageKey()));
    }

    // Best effort: a leftover object in R2 must not block deleting the suggestion
    public void deleteAll(List<SuggestionAttachment> attachments) {
        List<String> keys = attachments.stream()
                .filter(Objects::nonNull)
                .map(SuggestionAttachment::getStorageKey)
                .filter(Objects::nonNull)
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        try {
            r2StorageService.deleteFiles(keys);
        } catch (RuntimeException e) {
            log.warn("Couldn't delete suggestion attachments {}", keys, e);
        }
    }

    private static String displayName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "attachment" : name;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
