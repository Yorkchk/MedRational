package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.AuthMessageResponse;
import com.example.MedRational.DTOs.AuthResponse;
import com.example.MedRational.DTOs.ChangePasswordRequestDTO;
import com.example.MedRational.DTOs.ConfirmCodeRequestDTO;
import com.example.MedRational.DTOs.RequestEmailChangeDTO;
import com.example.MedRational.DTOs.UpdateProfileRequestDTO;
import com.example.MedRational.DTOs.UserProfileResponseDTO;
import com.example.MedRational.Security.CurrentUserGuard;
import com.example.MedRational.Services.Interfaces.AccountDeletionService;
import com.example.MedRational.Services.Interfaces.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The caller's own profile; the user always comes from the JWT.
// Wrong or expired codes answer 400, so the app doesn't sign the user out.
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final CurrentUserGuard currentUserGuard;
    private final AccountDeletionService accountDeletionService;

    @GetMapping
    public ResponseEntity<UserProfileResponseDTO> getProfile() {
        return ResponseEntity.ok(userProfileService.getProfile(currentUserGuard.requireCurrentUser()));
    }

    // First name, last name and phone; no code needed
    @PutMapping
    public ResponseEntity<UserProfileResponseDTO> updateProfile(@Valid @RequestBody UpdateProfileRequestDTO request) {
        return ResponseEntity.ok(userProfileService.updateProfile(currentUserGuard.requireCurrentUser(), request));
    }

    // Emails a code to the current address
    @PostMapping("/password/code")
    public ResponseEntity<AuthMessageResponse> requestPasswordChangeCode() {
        String message = userProfileService.requestPasswordChangeCode(currentUserGuard.requireCurrentUser());
        return ResponseEntity.ok(new AuthMessageResponse(message));
    }

    // Returns new tokens: the change signs every other device out. Still has "message", so apps that
    // only read that keep working
    @PutMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequestDTO request) {
        return ResponseEntity.ok(userProfileService.changePassword(currentUserGuard.requireCurrentUser(), request));
    }

    // Emails a code to the new address
    @PostMapping("/email/code")
    public ResponseEntity<AuthMessageResponse> requestEmailChange(@Valid @RequestBody RequestEmailChangeDTO request) {
        String message = userProfileService.requestEmailChange(currentUserGuard.requireCurrentUser(), request);
        return ResponseEntity.ok(new AuthMessageResponse(message));
    }

    // Returns a new token, because the old one is tied to the old email
    @PutMapping("/email")
    public ResponseEntity<AuthResponse> confirmEmailChange(@Valid @RequestBody ConfirmCodeRequestDTO request) {
        return ResponseEntity.ok(userProfileService.confirmEmailChange(currentUserGuard.requireCurrentUser(), request));
    }

    // Emails a code to the current address before deleting the account
    @PostMapping("/delete/code")
    public ResponseEntity<AuthMessageResponse> requestAccountDeletionCode() {
        String message = accountDeletionService.requestCode(currentUserGuard.requireCurrentUser());
        return ResponseEntity.ok(new AuthMessageResponse(message));
    }

    // Deletes the account; the token stops working right after
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody ConfirmCodeRequestDTO request) {
        accountDeletionService.deleteAccount(currentUserGuard.requireCurrentUser(), request.getCode());
        return ResponseEntity.noContent().build();
    }
}
