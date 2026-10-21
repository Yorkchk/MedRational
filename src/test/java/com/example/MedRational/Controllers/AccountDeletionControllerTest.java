package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.AccountDeletionCodeRequestDTO;
import com.example.MedRational.DTOs.AccountDeletionConfirmDTO;
import com.example.MedRational.DTOs.AuthMessageResponse;
import com.example.MedRational.Services.Interfaces.AccountDeletionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountDeletionControllerTest {

    private final AccountDeletionService service = mock(AccountDeletionService.class);
    private final AccountDeletionController controller = new AccountDeletionController(service);

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
