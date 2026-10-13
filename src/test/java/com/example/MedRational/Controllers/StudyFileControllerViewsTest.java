package com.example.MedRational.Controllers;

import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Interfaces.FilePreviewService;
import com.example.MedRational.Services.Interfaces.FileViewService;
import com.example.MedRational.Services.Interfaces.StudyFileService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyFileControllerViewsTest {

    private final FileViewService fileViewService = mock(FileViewService.class);
    private final CurrentUserGuard guard = mock(CurrentUserGuard.class);
    private final StudyFileController controller = new StudyFileController(
            mock(StudyFileService.class), mock(FilePreviewService.class), fileViewService, guard);

    @Test
    void viewIsRecordedForTheSignedInUser() {
        User me = User.builder().id(1L).role(Role.ROLE_USER).build();
        when(guard.requireCurrentUser()).thenReturn(me);

        assertEquals(204, controller.recordView(7L).getStatusCode().value());
        verify(fileViewService).recordView(me, 7L);
    }

    @Test
    void anonymousCallerIsRejected() {
        when(guard.requireCurrentUser()).thenThrow(new AccessDeniedException("You need to be signed in"));

        assertThrows(AccessDeniedException.class, () -> controller.recordView(7L));
        verify(fileViewService, never()).recordView(any(), anyLong());
    }
}
