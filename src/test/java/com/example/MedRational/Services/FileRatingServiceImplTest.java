package com.example.MedRational.Services;

import com.example.MedRational.DTOs.FileRatingSummaryDTO;
import com.example.MedRational.DTOs.RateFileRequestDTO;
import com.example.MedRational.Entities.FileRating;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.FileRatingRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Services.Implementations.FileRatingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileRatingServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long FILE_ID = 7L;

    private final FileRatingRepository ratingRepository = mock(FileRatingRepository.class);
    private final StudyFileRepository studyFileRepository = mock(StudyFileRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final FileRatingServiceImpl service =
            new FileRatingServiceImpl(ratingRepository, studyFileRepository, userRepository);

    private final User user = User.builder().id(USER_ID).build();
    private final StudyFile file = StudyFile.builder().id(FILE_ID).fileName("notes.pdf").build();

    @BeforeEach
    void setUp() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(studyFileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
    }

    private void statsAre(double avg, long count) {
        when(ratingRepository.averageScoreByFileId(FILE_ID)).thenReturn(avg);
        when(ratingRepository.countByFileId(FILE_ID)).thenReturn(count);
    }

    @Test
    void firstRatingCreatesARowAndUpdatesTheFileStats() {
        when(ratingRepository.findByUserIdAndFileId(USER_ID, FILE_ID)).thenReturn(Optional.empty());
        statsAre(4.0, 1);

        FileRatingSummaryDTO summary = service.rateFile(USER_ID, FILE_ID, new RateFileRequestDTO(4));

        ArgumentCaptor<FileRating> saved = ArgumentCaptor.forClass(FileRating.class);
        verify(ratingRepository).saveAndFlush(saved.capture());
        assertEquals(4, saved.getValue().getScore());
        assertSame(user, saved.getValue().getUser());
        assertSame(file, saved.getValue().getFile());

        assertEquals(4.0, summary.getAvgRating());
        assertEquals(1, summary.getTotalRatings());
        assertEquals(4, summary.getUserRating());
        assertEquals(4.0, file.getAvgRating());
        assertEquals(1, file.getTotalRatings());
        verify(studyFileRepository).save(file);
    }

    @Test
    void ratingAgainChangesTheExistingRow() {
        FileRating existing = FileRating.builder().id(3L).user(user).file(file).score(2).build();
        when(ratingRepository.findByUserIdAndFileId(USER_ID, FILE_ID)).thenReturn(Optional.of(existing));
        statsAre(5.0, 1);

        FileRatingSummaryDTO summary = service.rateFile(USER_ID, FILE_ID, new RateFileRequestDTO(5));

        verify(ratingRepository).saveAndFlush(existing);
        assertEquals(5, existing.getScore());
        assertEquals(1, summary.getTotalRatings());
        assertEquals(5, summary.getUserRating());
    }

    @Test
    void averageIsRoundedToOneDecimal() {
        when(ratingRepository.findByUserIdAndFileId(USER_ID, FILE_ID)).thenReturn(Optional.empty());
        statsAre(14.0 / 3.0, 3);

        FileRatingSummaryDTO summary = service.rateFile(USER_ID, FILE_ID, new RateFileRequestDTO(5));

        assertEquals(4.7, summary.getAvgRating());
        assertEquals(4.7, file.getAvgRating());
    }

    @Test
    void removingDeletesTheRatingAndClearsTheUserScore() {
        FileRating existing = FileRating.builder().id(3L).user(user).file(file).score(2).build();
        when(ratingRepository.findByUserIdAndFileId(USER_ID, FILE_ID)).thenReturn(Optional.of(existing));
        statsAre(0.0, 0);

        FileRatingSummaryDTO summary = service.removeRating(USER_ID, FILE_ID);

        verify(ratingRepository).delete(existing);
        assertNull(summary.getUserRating());
        assertEquals(0.0, summary.getAvgRating());
        assertEquals(0, summary.getTotalRatings());
        assertEquals(0, file.getTotalRatings());
    }

    @Test
    void removingWhenNotRatedIsANoOp() {
        when(ratingRepository.findByUserIdAndFileId(USER_ID, FILE_ID)).thenReturn(Optional.empty());
        statsAre(3.0, 2);

        FileRatingSummaryDTO summary = service.removeRating(USER_ID, FILE_ID);

        verify(ratingRepository, never()).delete(any());
        assertEquals(3.0, summary.getAvgRating());
        assertEquals(2, summary.getTotalRatings());
    }

    @Test
    void summaryIncludesTheCallersScore() {
        file.setAvgRating(3.5);
        file.setTotalRatings(4);
        when(ratingRepository.findByUserIdAndFileId(USER_ID, FILE_ID))
                .thenReturn(Optional.of(FileRating.builder().score(3).build()));

        FileRatingSummaryDTO summary = service.getFileRatingSummary(FILE_ID, USER_ID);

        assertEquals(3.5, summary.getAvgRating());
        assertEquals(4, summary.getTotalRatings());
        assertEquals(3, summary.getUserRating());
    }

    @Test
    void anonymousSummaryHasNoUserScore() {
        FileRatingSummaryDTO summary = service.getFileRatingSummary(FILE_ID, null);

        assertNull(summary.getUserRating());
        verify(ratingRepository, never()).findByUserIdAndFileId(any(), any());
    }
}
