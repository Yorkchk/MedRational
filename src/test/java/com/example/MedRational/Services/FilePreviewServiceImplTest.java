package com.example.MedRational.Services;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.WorkshopAttachment;
import com.example.MedRational.Exceptions.UnsupportedMediaTypeException;
import com.example.MedRational.Preview.OfficeConverter;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.WorkshopAttachmentRepository;
import com.example.MedRational.Services.Implementations.FilePreviewServiceImpl;
import com.example.MedRational.Services.Implementations.R2StorageServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import jakarta.persistence.EntityNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FilePreviewServiceImplTest {

    private static final long FILE_ID = 7L;
    private static final String STORAGE_KEY = "Categories/Cardio/ECG/abcd1234-scan.jpg";

    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final WorkshopAttachmentRepository attachmentRepository = mock(WorkshopAttachmentRepository.class);
    private final R2StorageServiceImpl r2StorageService = mock(R2StorageServiceImpl.class);
    private final OfficeConverter officeConverter = mock(OfficeConverter.class);

    private final FilePreviewServiceImpl service =
            new FilePreviewServiceImpl(studyFileRepository, attachmentRepository, r2StorageService, officeConverter);

    private void givenFile(String fileName, String fileType) {
        StudyFile file = StudyFile.builder().id(FILE_ID).fileName(fileName).fileType(fileType)
                .storageKey(STORAGE_KEY).build();
        when(studyFileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
    }

    @Test
    void imageIsStreamedAsIsWithItsContentType() {
        givenFile("scan.jpg", "application/octet-stream");
        Resource original = new ByteArrayResource(new byte[]{1, 2, 3});
        when(r2StorageService.downloadFileAsResource(STORAGE_KEY)).thenReturn(original);

        DownloadableFile preview = service.getPreview(FILE_ID);

        assertEquals("scan.jpg", preview.getFileName());
        assertEquals("image/jpeg", preview.getContentType());
        assertSame(original, preview.getResource());
        verifyNoInteractions(officeConverter);
        verify(r2StorageService, never()).uploadBytes(any(), any(), any());
    }

    @Test
    void unsupportedFileIsRejected() {
        givenFile("bundle.zip", "application/zip");

        assertThrows(UnsupportedMediaTypeException.class, () -> service.getPreview(FILE_ID));
    }

    // --- workshop attachments ---------------------------------------------------------------------

    private static final long ATTACHMENT_ID = 9L;
    private static final String ATTACHMENT_KEY = "WorkshopAttachments/Project_3/ab12cd34-";

    private WorkshopAttachment givenAttachment(String fileName, String previewStorageKey) {
        WorkshopAttachment attachment = WorkshopAttachment.builder()
                .id(ATTACHMENT_ID)
                .fileName(fileName)
                // What the backend used to hand out as a "public" URL: the private R2 endpoint
                .fileUrl("https://account.r2.cloudflarestorage.com/bucket/" + ATTACHMENT_KEY + fileName)
                .previewStorageKey(previewStorageKey)
                .build();
        when(attachmentRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(attachment));
        return attachment;
    }

    @Test
    void attachmentPdfIsStreamedFromItsStorageKey() {
        givenAttachment("program.pdf", null);
        Resource original = new ByteArrayResource(new byte[]{1});
        when(r2StorageService.downloadFileAsResource(ATTACHMENT_KEY + "program.pdf")).thenReturn(original);

        DownloadableFile preview = service.getAttachmentPreview(ATTACHMENT_ID);

        assertEquals("application/pdf", preview.getContentType());
        assertSame(original, preview.getResource());
        verifyNoInteractions(officeConverter);
    }

    @Test
    void attachmentImageIsStreamedWithItsContentType() {
        givenAttachment("room.png", null);
        when(r2StorageService.downloadFileAsResource(any())).thenReturn(new ByteArrayResource(new byte[]{1}));

        assertEquals("image/png", service.getAttachmentPreview(ATTACHMENT_ID).getContentType());
    }

    @Test
    void attachmentOfficeFileIsConvertedOnceAndItsPreviewSaved() throws Exception {
        givenAttachment("schedule.docx", null);
        byte[] pdf = {4, 5, 6};
        when(r2StorageService.downloadFileBytes(ATTACHMENT_KEY + "schedule.docx")).thenReturn(new byte[]{1});
        when(officeConverter.convertToPdf(any(), any())).thenReturn(pdf);
        String previewKey = "Previews/" + ATTACHMENT_KEY + "schedule.docx.pdf";
        when(attachmentRepository.updatePreviewStorageKey(ATTACHMENT_ID, previewKey)).thenReturn(1);

        DownloadableFile preview = service.getAttachmentPreview(ATTACHMENT_ID);

        assertEquals("schedule.pdf", preview.getFileName());
        assertArrayEquals(pdf, preview.getResource().getContentAsByteArray());
        verify(r2StorageService).uploadBytes(previewKey, pdf, "application/pdf");
        verify(attachmentRepository).updatePreviewStorageKey(ATTACHMENT_ID, previewKey);
    }

    @Test
    void attachmentCachedPreviewIsReusedWithoutConverting() {
        givenAttachment("schedule.docx", "Previews/cached.pdf");
        Resource cached = new ByteArrayResource(new byte[]{7});
        when(r2StorageService.downloadFileAsResource("Previews/cached.pdf")).thenReturn(cached);

        assertSame(cached, service.getAttachmentPreview(ATTACHMENT_ID).getResource());
        verifyNoInteractions(officeConverter);
    }

    @Test
    void attachmentDeletedWhileConvertingLeavesNoOrphanedPreview() throws Exception {
        givenAttachment("schedule.docx", null);
        when(r2StorageService.downloadFileBytes(any())).thenReturn(new byte[]{1});
        when(officeConverter.convertToPdf(any(), any())).thenReturn(new byte[]{2});
        when(attachmentRepository.updatePreviewStorageKey(any(), any())).thenReturn(0);

        assertThrows(EntityNotFoundException.class, () -> service.getAttachmentPreview(ATTACHMENT_ID));
        verify(r2StorageService).deleteFile("Previews/" + ATTACHMENT_KEY + "schedule.docx.pdf");
    }

    @Test
    void attachmentThatCantBePreviewedIsRejected() {
        givenAttachment("bundle.zip", null);

        assertThrows(UnsupportedMediaTypeException.class, () -> service.getAttachmentPreview(ATTACHMENT_ID));
    }

    @Test
    void missingAttachmentIsNotFound() {
        when(attachmentRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.getAttachmentPreview(ATTACHMENT_ID));
    }
}
