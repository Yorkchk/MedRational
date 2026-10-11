package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.SuggestionDetailDTO;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Interfaces.SuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestionControllerTest {

    private final SuggestionService service = mock(SuggestionService.class);
    private final CurrentUserGuard guard = mock(CurrentUserGuard.class);
    private final SuggestionController controller = new SuggestionController(service, guard);

    @Test
    void suggestionIsCreatedForTheSignedInUser() {
        User me = User.builder().id(1L).role(Role.ROLE_USER).build();
        SuggestionDetailDTO created = SuggestionDetailDTO.builder().id(10L).build();
        when(guard.requireCurrentUser()).thenReturn(me);
        when(service.createSuggestion(me, "Title", "Description", null)).thenReturn(created);

        ResponseEntity<SuggestionDetailDTO> response = controller.createSuggestion("Title", "Description", null);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(10L, response.getBody().getId());
    }

    @Test
    void anonymousCallerIsRejected() {
        when(guard.requireCurrentUser()).thenThrow(new AccessDeniedException("You need to be signed in"));

        assertThrows(AccessDeniedException.class, () -> controller.getSuggestion(10L));
        verify(service, never()).getSuggestion(any(), any());
    }
}
