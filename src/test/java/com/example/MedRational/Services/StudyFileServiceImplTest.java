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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyFileServiceImplTest {

    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final ReasoningRepository reasoningRepository = mock(ReasoningRepository.class);
    private final R2StorageServiceImpl r2 = mock(R2StorageServiceImpl.class);
    private final MappingServiceImpl mappingService = mock(MappingServiceImpl.class);
    private final FileRatingRepository fileRatingRepository = mock(FileRatingRepository.class);

    private final StudyFileServiceImpl service = new StudyFileServiceImpl(
            studyFileRepository, reasoningRepository, r2, mappingService, fileRatingRepository);

    // NewFilesNotificationServiceImpl announces the uploads marked pending
    @Test
    void uploadIsMarkedForTheNewFilesPush() throws Exception {
        Reasoning reasoning = Reasoning.builder()
                .id(7L)
                .title("Heart Failure")
                .category(Category.builder().id(1L).name("Cardiology").build())
                .build();
        when(reasoningRepository.findById(7L)).thenReturn(Optional.of(reasoning));
        when(r2.uploadFile(any(), anyString())).thenReturn("key");
        when(r2.buildPublicUrl("key")).thenReturn("https://r2/key");
        when(studyFileRepository.save(any(StudyFile.class))).thenAnswer(call -> call.getArgument(0));

        service.uploadFileToReasoning(7L, new MockMultipartFile("file", "ECG.pdf", "application/pdf", new byte[]{1}));

        ArgumentCaptor<StudyFile> saved = ArgumentCaptor.forClass(StudyFile.class);
        verify(studyFileRepository).save(saved.capture());
        assertEquals(true, saved.getValue().getPushPending());
    }
}
