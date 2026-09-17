package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.FileSearchFilterDTO;
import com.example.MedRational.DTOs.StudyFileResponse;
import com.example.MedRational.DTOs.StudyFileResponseDTO;
import com.example.MedRational.Services.Interfaces.StudyFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class StudyFileController {

    private final StudyFileService studyFileService;

    // Public / Student: List files attached to a reasoning
    @GetMapping("/reasoning/{reasoningId}")
    public ResponseEntity<List<StudyFileResponse>> getFilesByReasoning(@PathVariable Long reasoningId) {
        return ResponseEntity.ok(studyFileService.getFilesByReasoning(reasoningId));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<StudyFileResponseDTO>> searchFiles(
            @ModelAttribute FileSearchFilterDTO filter,
            @PageableDefault(size = 20, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(studyFileService.searchFiles(filter, pageable));
    }

    // Admin Only: Upload a single file (PDF/Image) to a reasoning
    @PostMapping(value = "/upload/{reasoningId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudyFileResponse> uploadSingleFile(
            @PathVariable Long reasoningId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(studyFileService.uploadFileToReasoning(reasoningId, file));
    }

    // Admin Only: Upload multiple files in a single request
    @PostMapping(value = "/upload-batch/{reasoningId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<StudyFileResponse>> uploadMultipleFiles(
            @PathVariable Long reasoningId,
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(studyFileService.uploadMultipleFilesToReasoning(reasoningId, files));
    }

    // Admin Only: Delete file by ID (removes from R2 and PostgreSQL)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        studyFileService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }
}