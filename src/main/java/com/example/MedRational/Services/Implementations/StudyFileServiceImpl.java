package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Preview.PreviewKind;
import com.example.MedRational.DTOs.StudyFileResponse;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.FileRatingRepository;
import com.example.MedRational.Repositories.ReasoningRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Interfaces.NewFilesNotificationService;
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
import com.example.MedRational.Specifications.StudyFileSpecifications;
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
    private final FileRatingRepository fileRatingRepository;
    private final NewFilesNotificationService newFilesNotificationService;

    @Transactional
    public StudyFileResponse uploadFileToReasoning(Long reasoningId, MultipartFile file) throws IOException {
        StudyFile saved = store(reasoningId, file);
        newFilesNotificationService.announce(List.of(saved));
        return mappingService.toStudyFileResponse(saved);
    }

    // One push for the whole batch, not one per file
    @Transactional
    public List<StudyFileResponse> uploadMultipleFilesToReasoning(Long reasoningId, List<MultipartFile> files) throws IOException {
        List<StudyFile> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            saved.add(store(reasoningId, file));
        }
        newFilesNotificationService.announce(saved);
        return saved.stream().map(mappingService::toStudyFileResponse).collect(Collectors.toList());
    }

    private StudyFile store(Long reasoningId, MultipartFile file) throws IOException {
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

        return studyFileRepository.save(studyFile);
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
        if (studyFile.getPreviewStorageKey() != null) {
            r2StorageService.deleteFile(studyFile.getPreviewStorageKey());
        }
        // file_ratings has no ON DELETE CASCADE
        fileRatingRepository.deleteAllByFileId(fileId);
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
        Specification<StudyFile> spec = StudyFileSpecifications.withFilters(filter);
        Page<StudyFile> filePage = studyFileRepository.findAll(spec, pageable);

        return filePage.map(this::mapToDTO);
    }

    private StudyFileResponseDTO mapToDTO(StudyFile file) {
        return StudyFileResponseDTO.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .publicUrl(file.getPublicUrl())
                .previewable(PreviewKind.detect(file.getFileName(), file.getFileType()).isPreviewable())
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