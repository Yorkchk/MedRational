package com.example.MedRational.Services;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.CategoryRepository;
import com.example.MedRational.Repositories.ReasoningRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Implementations.ExportDownloadServiceImpl;
import com.example.MedRational.Services.Implementations.R2StorageServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportDownloadServiceImplTest {

    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final ReasoningRepository reasoningRepository = mock(ReasoningRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final R2StorageServiceImpl r2StorageService = mock(R2StorageServiceImpl.class);

    private final ExportDownloadServiceImpl service =
            new ExportDownloadServiceImpl(studyFileRepository, reasoningRepository, categoryRepository, r2StorageService);

    private Reasoning reasoningWithFile(long id, String title, String fileName) throws IOException {
        StudyFile file = StudyFile.builder().id(id * 10).fileName(fileName).storageKey("key-" + fileName).build();
        when(r2StorageService.downloadFileBytes("key-" + fileName)).thenReturn(new byte[]{1, 2, 3});
        return Reasoning.builder().id(id).title(title).content("Some notes").files(new ArrayList<>(List.of(file)))
                .build();
    }

    private static List<String> entryNames(DownloadableFile zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (InputStream in = zip.getResource().getInputStream(); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    @Test
    void reasoningZipOnlyHoldsItsFiles() throws IOException {
        Reasoning reasoning = reasoningWithFile(1L, "ECG", "scan.pdf");
        when(reasoningRepository.findByIdWithFiles(1L)).thenReturn(Optional.of(reasoning));

        DownloadableFile zip = service.downloadReasoningAsZip(1L);

        assertEquals(List.of("files/scan.pdf"), entryNames(zip));
    }

    @Test
    void categoryZipHasAFolderPerReasoningWithOnlyItsFiles() throws IOException {
        Category category = Category.builder().id(5L).name("Cardio").build();
        when(categoryRepository.findByIdWithReasonings(5L)).thenReturn(Optional.of(category));
        List<Reasoning> reasonings = List.of(
                reasoningWithFile(1L, "ECG", "scan.pdf"),
                reasoningWithFile(2L, "Heart Failure", "notes.docx"));
        when(reasoningRepository.findByCategoryIdWithFiles(5L)).thenReturn(reasonings);

        DownloadableFile zip = service.downloadCategoryAsZip(5L);

        assertEquals(List.of("ECG/scan.pdf", "Heart_Failure/notes.docx"), entryNames(zip));
    }
}
