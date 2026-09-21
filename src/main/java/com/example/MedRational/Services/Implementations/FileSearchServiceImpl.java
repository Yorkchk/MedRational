package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.FileSearchResultDTO;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Interfaces.FileSearchService;
import com.example.MedRational.Specifications.StudyFileSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class FileSearchServiceImpl implements FileSearchService {

    private final StudyFileRepository studyFileRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<FileSearchResultDTO> searchFiles(String query, Long categoryId, Pageable pageable) {
        Page<StudyFile> files = studyFileRepository.findAll(
                StudyFileSpecifications.searchFiles(query, categoryId),
                pageable
        );

        return files.map(file -> FileSearchResultDTO.builder()
                .id(file.getId())
                .reasoningId(file.getReasoning() != null ? file.getReasoning().getId() : null)
                .reasoningTitle(file.getReasoning() != null ? file.getReasoning().getTitle() : "")
                .categoryId(file.getReasoning() != null && file.getReasoning().getCategory() != null
                        ? file.getReasoning().getCategory().getId() : null)
                .categoryName(file.getReasoning() != null && file.getReasoning().getCategory() != null
                        ? file.getReasoning().getCategory().getName() : "")
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .publicUrl(file.getPublicUrl())
                .fileSizeBytes(file.getFileSizeBytes())
                .hashtags(file.getHashtags() != null
                        ? file.getHashtags().stream().map(h -> h.getName()).toList()
                        : Collections.emptyList())
                .build());
    }
}