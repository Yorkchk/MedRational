package com.example.MedRational.Services;

import com.example.MedRational.DTOs.HashtagResponseDTO;
import com.example.MedRational.Entities.StudyFile;

import java.util.List;
import java.util.Set;

public interface HashtagService {

    // Used during file upload or later to add tags to an existing file
    Set<HashtagResponseDTO> attachHashtagsToFile(Long fileId, Set<String> tagNames);

    // Helper method called directly inside StudyFileService during file creation
    void attachHashtagsToExistingFileEntity(StudyFile file, Set<String> tagNames);

    // Detaches a specific hashtag from a file and deletes the hashtag if no other file uses it
    void removeHashtagFromFile(Long fileId, Long hashtagId);

    // Retrieves all hashtags currently attached to a given file
    List<HashtagResponseDTO> getHashtagsForFile(Long fileId);
}