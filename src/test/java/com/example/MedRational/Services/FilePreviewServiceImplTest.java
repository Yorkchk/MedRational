package com.example.MedRational.Services;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Exceptions.UnsupportedMediaTypeException;
import com.example.MedRational.Preview.OfficeConverter;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Implementations.FilePreviewServiceImpl;
import com.example.MedRational.Services.Implementations.R2StorageServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.Optional;

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
    private final R2StorageServiceImpl r2StorageService = mock(R2StorageServiceImpl.class);
    private final OfficeConverter officeConverter = mock(OfficeConverter.class);

    private final FilePreviewServiceImpl service =
            new FilePreviewServiceImpl(studyFileRepository, r2StorageService, officeConverter);

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
}
