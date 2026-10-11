package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Interfaces.DownloadTrackingService;
import com.example.MedRational.Services.Interfaces.ExportDownloadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportControllerTest {

    private static final long FILE_ID = 42L;

    private final ExportDownloadService exportDownloadService = mock(ExportDownloadService.class);
    private final DownloadTrackingService downloadTrackingService = mock(DownloadTrackingService.class);
    private final CurrentUserGuard currentUserGuard = mock(CurrentUserGuard.class);
    private final ExportController controller =
            new ExportController(exportDownloadService, downloadTrackingService, currentUserGuard);

    private final Resource resource = new ByteArrayResource("%PDF".getBytes());

    @BeforeEach
    void setUp() {
        when(exportDownloadService.downloadSingleFile(FILE_ID))
                .thenReturn(new DownloadableFile("ecg.pdf", "application/pdf", resource));
    }

    @Test
    void signedInDownloadIsRecorded() {
        when(currentUserGuard.currentUserId()).thenReturn(Optional.of(1L));

        ResponseEntity<Resource> response = controller.downloadFile(FILE_ID);

        verify(downloadTrackingService).recordUserDownload(1L, FILE_ID);
        assertSame(resource, response.getBody());
    }

    @Test
    void anonymousDownloadIsNotRecorded() {
        when(currentUserGuard.currentUserId()).thenReturn(Optional.empty());

        controller.downloadFile(FILE_ID);

        verify(downloadTrackingService, never()).recordUserDownload(anyLong(), anyLong());
    }

    @Test
    void trackingFailureStillServesTheFile() {
        when(currentUserGuard.currentUserId()).thenReturn(Optional.of(1L));
        doThrow(new RuntimeException("db down")).when(downloadTrackingService).recordUserDownload(1L, FILE_ID);

        ResponseEntity<Resource> response = controller.downloadFile(FILE_ID);

        assertEquals(200, response.getStatusCode().value());
        assertSame(resource, response.getBody());
    }
}
