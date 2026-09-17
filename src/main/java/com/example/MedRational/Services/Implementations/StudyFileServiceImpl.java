package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.StudyFileResponse;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.ReasoningRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Interfaces.StudyFileService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.example.MedRational.DTOs.FileSearchFilterDTO;
import com.example.MedRational.DTOs.StudyFileResponseDTO;
import com.example.MedRational.Entities.Hashtag;
import com.example.MedRational.Specifications.StudyFileSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;


@Service
@RequiredArgsConstructor
public class StudyFileServiceImpl implements StudyFileService {

    private final StudyFileRepository studyFileRepository;
    private final ReasoningRepository reasoningRepository;
    private final R2StorageServiceImpl r2StorageService;
    private final MappingServiceImpl mappingService;

    @Transactional
    public StudyFileResponse uploadFileToReasoning(Long reasoningId, MultipartFile file) throws IOException {
        Reasoning reasoning = reasoningRepository.findById(reasoningId)
                .orElseThrow(() -> new EntityNotFoundException("Reasoning not found with ID: " + reasoningId));

        String categoryFolder = sanitizePath(reasoning.getCategory().getName());
        String reasoningFolder = sanitizePath(reasoning.getTitle());

        // Constructs: Categories/Cardiology/Heart_Failure/
        String folderPrefix = "Categories/" + categoryFolder + "/" + reasoningFolder;

        String storageKey = r2StorageService.uploadFile(file, folderPrefix);
        String publicUrl = r2StorageService.buildPublicUrl(storageKey);

        StudyFile studyFile = StudyFile.builder()
                .fileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .storageKey(storageKey)
                .publicUrl(publicUrl)
                .reasoning(reasoning)
                .build();

        return mappingService.toStudyFileResponse(studyFileRepository.save(studyFile));
    }

    @Transactional
    public List<StudyFileResponse> uploadMultipleFilesToReasoning(Long reasoningId, List<MultipartFile> files) throws IOException {
        List<StudyFileResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            responses.add(uploadFileToReasoning(reasoningId, file));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<StudyFileResponse> getFilesByReasoning(Long reasoningId) {
        return studyFileRepository.findByReasoningId(reasoningId).stream()
                .map(mappingService::toStudyFileResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteFile(Long fileId) {
        StudyFile studyFile = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with ID: " + fileId));

        r2StorageService.deleteFile(studyFile.getStorageKey());
        studyFileRepository.delete(studyFile);
    }

    private String sanitizePath(String input) {
        if (input == null || input.isBlank()) {
            return "unnamed";
        }
        return input.trim().replaceAll("[^a-zA-Z0-9-_]", "_");
    }


    @Override
    @Transactional(readOnly = true)
    public Page<StudyFileResponseDTO> searchFiles(FileSearchFilterDTO filter, Pageable pageable) {
        Specification<StudyFile> spec = StudyFileSpecification.withFilters(filter);
        Page<StudyFile> filePage = studyFileRepository.findAll(spec, pageable);

        return filePage.map(this::mapToDTO);
    }

    private StudyFileResponseDTO mapToDTO(StudyFile file) {
        return StudyFileResponseDTO.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .publicUrl(file.getPublicUrl())
                .fileSizeBytes(file.getFileSizeBytes())
                .avgRating(file.getAvgRating())
                .totalRatings(file.getTotalRatings())
                .reasoningId(file.getReasoning() != null ? file.getReasoning().getId() : null)
                .reasoningTitle(file.getReasoning() != null ? file.getReasoning().getTitle() : null)
                .categoryId(file.getReasoning() != null && file.getReasoning().getCategory() != null
                        ? file.getReasoning().getCategory().getId() : null)
                .categoryName(file.getReasoning() != null && file.getReasoning().getCategory() != null
                        ? file.getReasoning().getCategory().getName() : null)
                .hashtags(file.getHashtags().stream().map(Hashtag::getName).collect(Collectors.toSet()))
                .uploadedAt(file.getUploadedAt())
                .build();
    }
}