package com.example.MedRational.Services;

import com.example.MedRational.Entities.FileViewEvent;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.FileViewEventRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Implementations.FileViewServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileViewServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 15, 0);

    private final FileViewEventRepository viewEventRepository = mock(FileViewEventRepository.class);
    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final FileViewServiceImpl service = new FileViewServiceImpl(viewEventRepository, studyFileRepository,
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private final User student = User.builder().id(1L).role(Role.ROLE_USER).build();
    private final StudyFile file = StudyFile.builder().id(7L).build();

    @Test
    void recordsAStudentOpeningAFile() {
        when(studyFileRepository.findById(7L)).thenReturn(Optional.of(file));

        service.recordView(student, 7L);

        ArgumentCaptor<FileViewEvent> saved = ArgumentCaptor.forClass(FileViewEvent.class);
        verify(viewEventRepository).save(saved.capture());
        assertSame(file, saved.getValue().getFile());
        assertSame(student, saved.getValue().getUser());
        assertEquals(NOW, saved.getValue().getCreatedAt());
    }

    @Test
    void reopeningWithinHalfAnHourCountsOnce() {
        when(studyFileRepository.findById(7L)).thenReturn(Optional.of(file));
        when(viewEventRepository.existsByUserIdAndFileIdAndCreatedAtAfter(1L, 7L, NOW.minusMinutes(30)))
                .thenReturn(true);

        service.recordView(student, 7L);

        verify(viewEventRepository, never()).save(any());
    }

    @Test
    void adminsAreNotCounted() {
        when(studyFileRepository.findById(7L)).thenReturn(Optional.of(file));

        service.recordView(User.builder().id(2L).role(Role.ROLE_ADMIN).build(), 7L);

        verify(viewEventRepository, never()).save(any());
    }

    @Test
    void unknownFileIsNotFound() {
        when(studyFileRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.recordView(student, 7L));
        verify(viewEventRepository, never()).save(any());
    }
}
