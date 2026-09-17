package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.FileSearchFilterDTO;
import com.example.MedRational.DTOs.StudyFileResponse;
import com.example.MedRational.DTOs.StudyFileResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface StudyFileService {

    StudyFileResponse uploadFileToReasoning(Long reasoningId, MultipartFile file) throws IOException;

    List<StudyFileResponse> uploadMultipleFilesToReasoning(Long reasoningId, List<MultipartFile> files) throws IOException;

    List<StudyFileResponse> getFilesByReasoning(Long reasoningId);

    void deleteFile(Long fileId);

    Page<StudyFileResponseDTO> searchFiles(FileSearchFilterDTO filter, Pageable pageable);
}