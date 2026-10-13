package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.AuthResponse;
import com.example.MedRational.DTOs.ConfirmCodeRequestDTO;
import com.example.MedRational.DTOs.UpdateProfileRequestDTO;
import com.example.MedRational.DTOs.UserProfileResponseDTO;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Interfaces.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileControllerTest {

    private final UserProfileService service = mock(UserProfileService.class);
    private final CurrentUserGuard guard = mock(CurrentUserGuard.class);
    private final UserProfileController controller = new UserProfileController(service, guard);

    private final User me = User.builder().id(1L).email("me@example.com").role(Role.ROLE_USER).build();

    @Test
    void profileIsTheSignedInUsers() {
        UserProfileResponseDTO profile = UserProfileResponseDTO.builder().id(1L).build();
        when(guard.requireCurrentUser()).thenReturn(me);
        when(service.getProfile(me)).thenReturn(profile);

        assertEquals(profile, controller.getProfile().getBody());
    }

    @Test
    void updatesApplyToTheSignedInUser() {
        UpdateProfileRequestDTO request = new UpdateProfileRequestDTO("Sara", "Idrissi", null);
        when(guard.requireCurrentUser()).thenReturn(me);

        controller.updateProfile(request);

        verify(service).updateProfile(me, request);
    }

    @Test
    void emailConfirmationReturnsTheNewToken() {
        ConfirmCodeRequestDTO request = new ConfirmCodeRequestDTO("123456");
        AuthResponse response = AuthResponse.builder().token("new-token").build();
        when(guard.requireCurrentUser()).thenReturn(me);
        when(service.confirmEmailChange(me, request)).thenReturn(response);

        assertEquals("new-token", controller.confirmEmailChange(request).getBody().getToken());
    }

    @Test
    void anonymousCallerIsRejected() {
        when(guard.requireCurrentUser()).thenThrow(new AccessDeniedException("You need to be signed in"));

        assertThrows(AccessDeniedException.class, controller::getProfile);
        assertThrows(AccessDeniedException.class, controller::requestPasswordChangeCode);
        verify(service, never()).getProfile(any());
        verify(service, never()).requestPasswordChangeCode(any());
    }
}
