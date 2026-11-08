package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.AccountDeletionCodeRequestDTO;
import com.example.MedRational.DTOs.AccountDeletionConfirmDTO;
import com.example.MedRational.DTOs.AuthMessageResponse;
import com.example.MedRational.Security.RateLimitedAction;
import com.example.MedRational.Security.RateLimiter;
import com.example.MedRational.Services.Interfaces.AccountDeletionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Used by the public /delete-account page (the Google Play deletion link), so no sign-in: the emailed
// code proves the caller owns the address. Signed-in users delete from /api/v1/users/me instead.
@RestController
@RequestMapping("/api/v1/auth/delete-account")
@RequiredArgsConstructor
public class AccountDeletionController {

    static final String CODE_SENT = "If an account uses this email, a verification code has been sent to it.";

    private final AccountDeletionService accountDeletionService;
    private final RateLimiter rateLimiter;

    // Same answer whether or not the account exists
    @PostMapping("/code")
    public ResponseEntity<AuthMessageResponse> requestCode(@Valid @RequestBody AccountDeletionCodeRequestDTO request) {
        rateLimiter.check(RateLimitedAction.SEND_CODE, request.getEmail());
        accountDeletionService.requestCode(request.getEmail());
        return ResponseEntity.ok(new AuthMessageResponse(CODE_SENT));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody AccountDeletionConfirmDTO request) {
        rateLimiter.check(RateLimitedAction.VERIFY_CODE, request.getEmail());
        accountDeletionService.deleteAccount(request.getEmail(), request.getCode());
        return ResponseEntity.noContent().build();
    }
}
