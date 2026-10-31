package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.Entities.StudyFile;

import java.util.List;

/** Tells students about new study files, one push per upload request. */
public interface NewFilesNotificationService {

    /** Announces files that were just uploaded together; nothing when the list is empty. */
    void announce(List<StudyFile> files);
}
