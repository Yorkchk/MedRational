package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Exceptions.UnsupportedMediaTypeException;
import com.example.MedRational.Preview.OfficeConverter;
import com.example.MedRational.Preview.PreviewKind;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Interfaces.FilePreviewService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;

@Service
@RequiredArgsConstructor
public class FilePreviewServiceImpl implements FilePreviewService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final StudyFileRepository studyFileRepository;
    private final R2StorageServiceImpl r2StorageService;
    private final OfficeConverter officeConverter;

    // Not @Transactional on purpose: LibreOffice conversion can take seconds and must not hold a DB connection
    public DownloadableFile getPreview(Long fileId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with ID: " + fileId));

        PreviewKind kind = PreviewKind.detect(file.getFileName(), file.getFileType());
        String previewName = toPdfName(file.getFileName());

        if (kind == PreviewKind.NONE) {
            throw new UnsupportedMediaTypeException("Preview is only supported for PDF, DOCX, PPTX and XLSX files.");
        }

        if (kind == PreviewKind.PDF) {
            Resource resource = r2StorageService.downloadFileAsResource(file.getStorageKey());
            return new DownloadableFile(previewName, PDF_CONTENT_TYPE, resource);
        }

        // Previously converted -> serve the cached PDF from R2
        if (file.getPreviewStorageKey() != null) {
            Resource resource = r2StorageService.downloadFileAsResource(file.getPreviewStorageKey());
            return new DownloadableFile(previewName, PDF_CONTENT_TYPE, resource);
        }

        byte[] pdf = officeConverter.convertToPdf(downloadBytes(file.getStorageKey()), kind.getSourceFormat());

        // Format: Previews/{original storage key}.pdf
        String previewKey = "Previews/" + file.getStorageKey() + ".pdf";
        r2StorageService.uploadBytes(previewKey, pdf, PDF_CONTENT_TYPE);

        // File was deleted while converting -> don't leave an orphaned preview behind
        if (studyFileRepository.updatePreviewStorageKey(fileId, previewKey) == 0) {
            r2StorageService.deleteFile(previewKey);
            throw new EntityNotFoundException("File not found with ID: " + fileId);
        }

        return new DownloadableFile(previewName, PDF_CONTENT_TYPE, new ByteArrayResource(pdf));
    }

    private byte[] downloadBytes(String storageKey) {
        try {
            return r2StorageService.downloadFileBytes(storageKey);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String toPdfName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "preview.pdf";
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base + ".pdf";
    }
}
