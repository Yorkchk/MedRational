package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.AccountDeletionCodeRequestDTO;
import com.example.MedRational.DTOs.AccountDeletionConfirmDTO;
import com.example.MedRational.DTOs.AuthMessageResponse;
import com.example.MedRational.Exceptions.TooManyRequestsException;
import com.example.MedRational.Security.RateLimitedAction;
import com.example.MedRational.Security.RateLimiter;
import com.example.MedRational.Services.Interfaces.AccountDeletionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AccountDeletionControllerTest {

    private final AccountDeletionService service = mock(AccountDeletionService.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final AccountDeletionController controller = new AccountDeletionController(service, rateLimiter);

    // The public deletion page is rate limited like the app: per email and per IP
    @Test
    void bothStepsAreRateLimitedPerEmail() {
        controller.requestCode(new AccountDeletionCodeRequestDTO("sara@example.com"));
        controller.confirm(new AccountDeletionConfirmDTO("sara@example.com", "123456"));

        verify(rateLimiter).check(RateLimitedAction.SEND_CODE, "sara@example.com");
        verify(rateLimiter).check(RateLimitedAction.VERIFY_CODE, "sara@example.com");
    }

    @Test
    void aRefusedCodeRequestSendsNothing() {
        doThrow(new TooManyRequestsException(60)).when(rateLimiter).check(RateLimitedAction.SEND_CODE, "sara@example.com");

        assertThrows(TooManyRequestsException.class,
                () -> controller.requestCode(new AccountDeletionCodeRequestDTO("sara@example.com")));
        verify(service, never()).requestCode(anyString());
    }

    // The answer never tells whether the email has an account
    @Test
    void theCodeRequestAlwaysGivesTheSameAnswer() {
        ResponseEntity<AuthMessageResponse> known = controller.requestCode(new AccountDeletionCodeRequestDTO("sara@example.com"));
        ResponseEntity<AuthMessageResponse> unknown = controller.requestCode(new AccountDeletionCodeRequestDTO("nobody@example.com"));

        assertEquals(200, known.getStatusCode().value());
        assertEquals(known.getBody(), unknown.getBody());
        assertEquals(AccountDeletionController.CODE_SENT, known.getBody().getMessage());
        verify(service).requestCode("sara@example.com");
        verify(service).requestCode("nobody@example.com");
    }

    @Test
    void confirmingDeletesTheAccount() {
        ResponseEntity<Void> response = controller.confirm(new AccountDeletionConfirmDTO("sara@example.com", "123456"));

        assertEquals(204, response.getStatusCode().value());
        verify(service).deleteAccount("sara@example.com", "123456");
    }

    // The exception handler turns it into a 400
    @Test
    void aWrongCodeIsABadRequest() {
        doThrow(new IllegalArgumentException("Invalid verification code."))
                .when(service).deleteAccount("sara@example.com", "000000");

        assertThrows(IllegalArgumentException.class,
                () -> controller.confirm(new AccountDeletionConfirmDTO("sara@example.com", "000000")));
    }
}
