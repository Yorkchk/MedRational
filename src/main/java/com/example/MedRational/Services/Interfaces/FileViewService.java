package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.Entities.User;

public interface FileViewService {

    // Counts the user opening the file (404 when it doesn't exist). Only students are counted, and a
    // file reopened by the same student within a short window counts once
    void recordView(User user, Long fileId);
}
