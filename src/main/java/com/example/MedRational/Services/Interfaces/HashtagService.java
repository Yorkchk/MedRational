package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.HashtagResponseDTO;
import com.example.MedRational.Entities.StudyFile;

import java.util.List;
import java.util.Set;

public interface HashtagService {

    Set<HashtagResponseDTO> attachHashtagsToFile(Long fileId, Set<String> tagNames);

    void attachHashtagsToExistingFileEntity(StudyFile file, Set<String> tagNames);

    void removeHashtagFromFile(Long fileId, Long hashtagId);

    List<HashtagResponseDTO> getHashtagsForFile(Long fileId);
}