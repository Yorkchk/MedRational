package com.example.MedRational.Services;

import com.example.MedRational.DTOs.UpdateStudyFileRequestDTO;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.FileRatingRepository;
import com.example.MedRational.Repositories.ReasoningRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Implementations.MappingServiceImpl;
import com.example.MedRational.Services.Implementations.R2StorageServiceImpl;
import com.example.MedRational.Services.Implementations.StudyFileServiceImpl;
import com.example.MedRational.Services.Interfaces.NewFilesNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyFileServiceImplTest {

    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final ReasoningRepository reasoningRepository = mock(ReasoningRepository.class);
    private final R2StorageServiceImpl r2 = mock(R2StorageServiceImpl.class);
    private final MappingServiceImpl mappingService = mock(MappingServiceImpl.class);
    private final FileRatingRepository fileRatingRepository = mock(FileRatingRepository.class);
    private final NewFilesNotificationService newFiles = mock(NewFilesNotificationService.class);

    private final StudyFileServiceImpl service = new StudyFileServiceImpl(
            studyFileRepository, reasoningRepository, r2, mappingService, fileRatingRepository, newFiles);

    @BeforeEach
    void setUp() throws Exception {
        Reasoning reasoning = Reasoning.builder()
                .id(7L)
                .title("Heart Failure")
                .category(Category.builder().id(1L).name("Cardiology").build())
                .build();
        when(reasoningRepository.findById(7L)).thenReturn(Optional.of(reasoning));
        when(r2.uploadFile(any(), anyString())).thenReturn("key");
        when(r2.buildPublicUrl("key")).thenReturn("https://r2/key");
        AtomicLong ids = new AtomicLong();
        when(studyFileRepository.save(any(StudyFile.class))).thenAnswer(call -> {
            StudyFile file = call.getArgument(0);
            file.setId(ids.incrementAndGet());
            return file;
        });
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", new byte[]{1});
    }

    @SuppressWarnings("unchecked")
    private List<StudyFile> announced() {
        ArgumentCaptor<List<StudyFile>> files = ArgumentCaptor.forClass(List.class);
        verify(newFiles, times(1)).announce(files.capture());
        return files.getValue();
    }

    // Students hear about it right away, not after a delay
    @Test
    void uploadIsAnnouncedRightAway() throws Exception {
        service.uploadFileToReasoning(7L, pdf("ECG.pdf"), null);

        List<StudyFile> files = announced();
        assertEquals(1, files.size());
        assertEquals("ECG.pdf", files.get(0).getFileName());
    }

    // One push for the batch, not one per file
    @Test
    void batchUploadIsAnnouncedOnce() throws Exception {
        service.uploadMultipleFilesToReasoning(7L, List.of(pdf("a.pdf"), pdf("b.pdf"), pdf("c.pdf")), null);

        assertEquals(List.of("a.pdf", "b.pdf", "c.pdf"), announced().stream().map(StudyFile::getFileName).toList());
    }

    @Test
    void uploadKeepsTheTrimmedDescription() throws Exception {
        service.uploadFileToReasoning(7L, pdf("ECG.pdf"), "  Normal sinus rhythm  ");

        assertEquals("Normal sinus rhythm", announced().get(0).getDescription());
    }

    @Test
    void blankDescriptionIsSavedAsNull() throws Exception {
        service.uploadFileToReasoning(7L, pdf("ECG.pdf"), "   ");

        assertNull(announced().get(0).getDescription());
    }

    // Like the hashtags, one description goes on every file of the batch
    @Test
    void batchUploadPutsTheDescriptionOnEveryFile() throws Exception {
        service.uploadMultipleFilesToReasoning(7L, List.of(pdf("a.pdf"), pdf("b.pdf")), "Week 3 slides");

        assertEquals(List.of("Week 3 slides", "Week 3 slides"),
                announced().stream().map(StudyFile::getDescription).toList());
    }

    private StudyFile givenStoredFile(String fileName, String description) {
        StudyFile file = StudyFile.builder().id(9L).fileName(fileName).description(description).build();
        when(studyFileRepository.findById(9L)).thenReturn(Optional.of(file));
        return file;
    }

    @Test
    void renamingKeepsTheExtension() {
        StudyFile file = givenStoredFile("scan_001.pdf", "Week 3 slides");

        service.updateFile(9L, new UpdateStudyFileRequestDTO("  ECG basics ", "  Lead placement  "));

        assertEquals("ECG basics.pdf", file.getFileName());
        assertEquals("Lead placement", file.getDescription());
        verify(studyFileRepository).save(file);
    }

    // Typing the extension doesn't double it
    @Test
    void titleWithTheExtensionIsNotDoubled() {
        StudyFile file = givenStoredFile("scan.PDF", null);

        service.updateFile(9L, new UpdateStudyFileRequestDTO("ECG.pdf", null));

        assertEquals("ECG.pdf", file.getFileName());
    }

    // A slash would make a folder in zips and break the download header
    @Test
    void slashesAreReplaced() {
        StudyFile file = givenStoredFile("scan.pdf", null);

        service.updateFile(9L, new UpdateStudyFileRequestDTO("ECG/Holter\\2024", null));

        assertEquals("ECG_Holter_2024.pdf", file.getFileName());
    }

    @Test
    void blankDescriptionClearsIt() {
        StudyFile file = givenStoredFile("scan.pdf", "Week 3 slides");

        service.updateFile(9L, new UpdateStudyFileRequestDTO("ECG", "   "));

        assertNull(file.getDescription());
    }

    @Test
    void unknownFileIsNotFound() {
        when(studyFileRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.updateFile(404L, new UpdateStudyFileRequestDTO("ECG", null)));
    }
}
