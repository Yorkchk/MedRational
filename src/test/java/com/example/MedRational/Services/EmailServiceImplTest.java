package com.example.MedRational.Services;

import com.example.MedRational.Services.Implementations.EmailServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailServiceImplTest {

    private static final String FROM = "SWISRA <no-reply@example.com>";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailServiceImpl service = new EmailServiceImpl(mailSender, FROM);

    // Amazon SES only sends from a verified identity, and its SMTP username is an access key,
    // so the server can't fill the sender in the way Gmail did
    @Test
    void codeEmailIsSentFromTheConfiguredSender() {
        service.sendOtpEmail("student@example.com", "123456", "Your code");

        ArgumentCaptor<SimpleMailMessage> email = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(email.capture());
        assertEquals(FROM, email.getValue().getFrom());
        assertArrayEquals(new String[] {"student@example.com"}, email.getValue().getTo());
        assertEquals("Your code", email.getValue().getSubject());
        assertTrue(email.getValue().getText().contains("123456"));
    }
}
