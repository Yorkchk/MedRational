package com.example.MedRational.Services;

import com.example.MedRational.Config.SuggestionAttachmentProperties;
import com.example.MedRational.Entities.SuggestionAttachment;
import com.example.MedRational.Exceptions.AttachmentTooLargeException;
import com.example.MedRational.Exceptions.UnsupportedMediaTypeException;
import com.example.MedRational.Services.Implementations.SuggestionAttachmentStore;
import com.example.MedRational.Services.Interfaces.R2StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestionAttachmentStoreTest {

    private final R2StorageService r2 = mock(R2StorageService.class);
    private final SuggestionAttachmentStore store = new SuggestionAttachmentStore(r2, new SuggestionAttachmentProperties());

    private static MockMultipartFile file(String name, int sizeBytes) {
        byte[] bytes = new byte[sizeBytes];
        Arrays.fill(bytes, (byte) 1);
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    @Test
    void storesUnderTheSuggestionPrefix() throws IOException {
        MockMultipartFile pdf = file("Notes.PDF", 1024);
        when(r2.uploadFile(pdf, "Suggestions/10")).thenReturn("Suggestions/10/abcd-Notes.PDF");

        SuggestionAttachment stored = store.store(pdf, 10L);

        assertEquals("Suggestions/10/abcd-Notes.PDF", stored.getStorageKey());
        assertEquals("Notes.PDF", stored.getFileName());
        assertEquals(1024L, stored.getSizeBytes());
    }

    @Test
    void noFileMeansNoAttachment() throws IOException {
        assertNull(store.store(null, 10L));
        assertNull(store.store(file("empty.pdf", 0), 10L));
        verify(r2, never()).uploadFile(any(), any());
    }

    @Test
    void exactlyTenMegabytesIsAccepted() {
        assertDoesNotThrow(() -> store.validate(file("scan.pdf", 10 * 1024 * 1024)));
    }

    @Test
    void overTenMegabytesIsRejected() throws IOException {
        MockMultipartFile big = file("scan.pdf", 10 * 1024 * 1024 + 1);

        assertThrows(AttachmentTooLargeException.class, () -> store.store(big, 10L));
        verify(r2, never()).uploadFile(any(), any());
    }

    @Test
    void executablesAreRejected() {
        assertThrows(UnsupportedMediaTypeException.class, () -> store.validate(file("setup.exe", 10)));
        assertThrows(UnsupportedMediaTypeException.class, () -> store.validate(file("no-extension", 10)));
    }

    @Test
    void storageFailureWhileDeletingIsSwallowed() {
        doThrow(new RuntimeException("R2 down")).when(r2).deleteFiles(anyList());

        assertDoesNotThrow(() -> store.deleteAll(List.of(
                SuggestionAttachment.builder().storageKey("Suggestions/10/a.pdf").build())));
    }

    @Test
    void nothingToDeleteMakesNoCall() {
        store.deleteAll(Arrays.asList(null, SuggestionAttachment.builder().build()));

        verify(r2, never()).deleteFiles(anyList());
    }
}
