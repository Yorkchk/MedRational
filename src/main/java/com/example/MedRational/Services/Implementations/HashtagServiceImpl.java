package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.HashtagResponseDTO;
import com.example.MedRational.Entities.Hashtag;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.HashtagRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Interfaces.HashtagService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HashtagServiceImpl implements HashtagService {

    private final HashtagRepository hashtagRepository;
    private final StudyFileRepository studyFileRepository;

    @Override
    @Transactional
    public Set<HashtagResponseDTO> attachHashtagsToFile(Long fileId, Set<String> tagNames) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        attachHashtagsToExistingFileEntity(file, tagNames);
        studyFileRepository.save(file);

        return file.getHashtags().stream()
                .map(h -> new HashtagResponseDTO(h.getId(), h.getName()))
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void attachHashtagsToExistingFileEntity(StudyFile file, Set<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }

        for (String rawName : tagNames) {
            String sanitized = sanitizeTag(rawName);
            if (sanitized.isEmpty()) {
                continue;
            }

            // Find existing hashtag across the platform, or persist a new one for this file
            Hashtag hashtag = hashtagRepository.findByNameIgnoreCase(sanitized)
                    .orElseGet(() -> hashtagRepository.save(
                            Hashtag.builder()
                                    .name(sanitized)
                                    .build()
                    ));

            file.getHashtags().add(hashtag);
        }
    }

    @Override
    @Transactional
    public void removeHashtagFromFile(Long fileId, Long hashtagId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        Hashtag hashtag = hashtagRepository.findById(hashtagId)
                .orElseThrow(() -> new EntityNotFoundException("Hashtag not found with id: " + hashtagId));

        // Disassociate the hashtag from the target file
        boolean removed = file.getHashtags().removeIf(h -> h.getId().equals(hashtagId));

        if (removed) {
            studyFileRepository.save(file);

            // Rule: "a hashtag should never exist by itself"
            // If no other file is linked to this hashtag, delete it completely
            long remainingFilesCount = studyFileRepository.findByHashtagName(hashtag.getName(), PageRequest.of(0, 1)).getTotalElements();
            if (remainingFilesCount == 0) {
                hashtagRepository.delete(hashtag);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<HashtagResponseDTO> getHashtagsForFile(Long fileId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        return file.getHashtags().stream()
                .map(h -> new HashtagResponseDTO(h.getId(), h.getName()))
                .collect(Collectors.toList());
    }

    private String sanitizeTag(String rawTag) {
        if (rawTag == null) return "";
        return rawTag.trim().toLowerCase().replaceAll("^#+", "");
    }
}