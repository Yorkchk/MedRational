package com.example.MedRational.Config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationSchemaMigrationTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EmailVerificationSchemaMigration migration = new EmailVerificationSchemaMigration(jdbcTemplate);

    // Otherwise every existing account would be locked out once sign-in needs a verified email
    @Test
    void marksAccountsThatAlreadySignedInAndAdminsAsVerified() {
        migration.run(null);

        verify(jdbcTemplate).update("UPDATE users SET email_verified = TRUE WHERE email_verified IS NULL"
                + " AND (last_login_at IS NOT NULL OR role = 'ROLE_ADMIN')");
    }

    @Test
    void aFailureDoesNotStopStartup() {
        when(jdbcTemplate.update(EmailVerificationSchemaMigration.BACKFILL))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertDoesNotThrow(() -> migration.run(null));
    }
}
