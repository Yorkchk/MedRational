package com.example.MedRational.Services;

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
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        service.uploadFileToReasoning(7L, pdf("ECG.pdf"));

        List<StudyFile> files = announced();
        assertEquals(1, files.size());
        assertEquals("ECG.pdf", files.get(0).getFileName());
    }

    // One push for the batch, not one per file
    @Test
    void batchUploadIsAnnouncedOnce() throws Exception {
        service.uploadMultipleFilesToReasoning(7L, List.of(pdf("a.pdf"), pdf("b.pdf"), pdf("c.pdf")));

        assertEquals(List.of("a.pdf", "b.pdf", "c.pdf"), announced().stream().map(StudyFile::getFileName).toList());
    }
}
