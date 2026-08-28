package com.example.MedRational.Services;

import com.example.MedRational.DTOs.StudyFileResponse;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.ReasoningRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyFileService {

    private final StudyFileRepository studyFileRepository;
    private final ReasoningRepository reasoningRepository;
    private final R2StorageService r2StorageService;
    private final MappingService mappingService;

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
}