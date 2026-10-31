package com.example.MedRational.Services.Interfaces;

import java.time.LocalDateTime;

/** Tells students about new study files, one push per batch of uploads. */
public interface NewFilesNotificationService {

    /** Announces the pending uploads once the admin has stopped uploading, as of {@code now}. */
    void announcePendingUploads(LocalDateTime now);
}
