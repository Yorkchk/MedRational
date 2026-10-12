package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.WorkshopAttachment;
import com.example.MedRational.Exceptions.UnsupportedMediaTypeException;
import com.example.MedRational.Preview.OfficeConverter;
import com.example.MedRational.Preview.PreviewKind;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.WorkshopAttachmentRepository;
import com.example.MedRational.Services.Interfaces.FilePreviewService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class FilePreviewServiceImpl implements FilePreviewService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final StudyFileRepository studyFileRepository;
    private final WorkshopAttachmentRepository workshopAttachmentRepository;
    private final R2StorageServiceImpl r2StorageService;
    private final OfficeConverter officeConverter;

    // Not @Transactional on purpose: LibreOffice conversion can take seconds and must not hold a DB connection
    public DownloadableFile getPreview(Long fileId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with ID: " + fileId));

        return render(file.getFileName(), file.getFileType(), file.getStorageKey(), file.getPreviewStorageKey(),
                previewKey -> studyFileRepository.updatePreviewStorageKey(fileId, previewKey) > 0,
                "File not found with ID: " + fileId);
    }

    // Not @Transactional, for the same reason as getPreview
    public DownloadableFile getAttachmentPreview(Long attachmentId) {
        String notFound = "Attachment not found with id: " + attachmentId;
        WorkshopAttachment attachment = workshopAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException(notFound));
        String storageKey = attachment.storageKey();
        if (storageKey == null) {
            throw new EntityNotFoundException(notFound);
        }

        // Attachments store no content type: the extension decides
        return render(attachment.getFileName(), null, storageKey, attachment.getPreviewStorageKey(),
                previewKey -> workshopAttachmentRepository.updatePreviewStorageKey(attachmentId, previewKey) > 0,
                notFound);
    }

    /**
     * The inline rendition of a stored file. [savePreviewKey] records a freshly converted PDF and returns
     * false when the file was deleted meanwhile.
     */
    private DownloadableFile render(String fileName, String fileType, String storageKey, String cachedPreviewKey,
                                    Predicate<String> savePreviewKey, String notFoundMessage) {
        PreviewKind kind = PreviewKind.detect(fileName, fileType);
        String previewName = toPdfName(fileName);

        if (kind == PreviewKind.NONE) {
            throw new UnsupportedMediaTypeException("Preview is only supported for PDF, DOCX, PPTX, XLSX and image files.");
        }

        // The R2 bucket isn't public, so images are streamed through here instead of their publicUrl
        if (kind == PreviewKind.IMAGE) {
            Resource resource = r2StorageService.downloadFileAsResource(storageKey);
            String contentType = PreviewKind.imageContentType(fileName, fileType);
            return new DownloadableFile(fileName, contentType, resource);
        }

        if (kind == PreviewKind.PDF) {
            Resource resource = r2StorageService.downloadFileAsResource(storageKey);
            return new DownloadableFile(previewName, PDF_CONTENT_TYPE, resource);
        }

        // Previously converted -> serve the cached PDF from R2
        if (cachedPreviewKey != null) {
            Resource resource = r2StorageService.downloadFileAsResource(cachedPreviewKey);
            return new DownloadableFile(previewName, PDF_CONTENT_TYPE, resource);
        }

        byte[] pdf = officeConverter.convertToPdf(downloadBytes(storageKey), kind.getSourceFormat());

        // Format: Previews/{original storage key}.pdf
        String previewKey = "Previews/" + storageKey + ".pdf";
        r2StorageService.uploadBytes(previewKey, pdf, PDF_CONTENT_TYPE);

        // File was deleted while converting -> don't leave an orphaned preview behind
        if (!savePreviewKey.test(previewKey)) {
            r2StorageService.deleteFile(previewKey);
            throw new EntityNotFoundException(notFoundMessage);
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
