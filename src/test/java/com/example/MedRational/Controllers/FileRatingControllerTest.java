package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.FileRatingSummaryDTO;
import com.example.MedRational.DTOs.RateFileRequestDTO;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Interfaces.FileRatingService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileRatingControllerTest {

    private final FileRatingService service = mock(FileRatingService.class);
    private final CurrentUserGuard guard = mock(CurrentUserGuard.class);
    private final FileRatingController controller = new FileRatingController(service, guard);

    private final User me = User.builder().id(1L).role(Role.ROLE_USER).build();

    @Test
    void ratingIsRecordedForTheSignedInUser() {
        RateFileRequestDTO request = new RateFileRequestDTO(4);
        FileRatingSummaryDTO summary = FileRatingSummaryDTO.builder().fileId(7L).userRating(4).build();
        when(guard.requireCurrentUser()).thenReturn(me);
        when(service.rateFile(1L, 7L, request)).thenReturn(summary);

        assertEquals(summary, controller.rateFile(7L, request).getBody());
    }

    @Test
    void anonymousCallerCannotRateOrRemove() {
        when(guard.requireCurrentUser()).thenThrow(new AccessDeniedException("You need to be signed in"));

        assertThrows(AccessDeniedException.class, () -> controller.rateFile(7L, new RateFileRequestDTO(4)));
        assertThrows(AccessDeniedException.class, () -> controller.removeRating(7L));
        verify(service, never()).rateFile(anyLong(), anyLong(), any());
        verify(service, never()).removeRating(anyLong(), anyLong());
    }

    @Test
    void removingUsesTheSignedInUser() {
        when(guard.requireCurrentUser()).thenReturn(me);

        controller.removeRating(7L);

        verify(service).removeRating(1L, 7L);
    }

    @Test
    void anonymousSummaryIsAskedWithoutAUser() {
        when(guard.currentUserId()).thenReturn(Optional.empty());

        controller.getRatingSummary(7L);

        verify(service).getFileRatingSummary(7L, null);
    }

    @Test
    void signedInSummaryIncludesTheCaller() {
        when(guard.currentUserId()).thenReturn(Optional.of(1L));

        controller.getRatingSummary(7L);

        verify(service).getFileRatingSummary(7L, 1L);
    }
}
