package com.example.MedRational.Config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationSchemaMigrationTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NotificationSchemaMigration migration = new NotificationSchemaMigration(jdbcTemplate);

    // Otherwise the old constraint rejects WORKSHOP_CREATED notifications
    @Test
    void dropsTheStaleTypeCheck() {
        migration.run(null);

        verify(jdbcTemplate).execute("ALTER TABLE IF EXISTS notifications DROP CONSTRAINT IF EXISTS notifications_type_check");
    }

    @Test
    void aFailureDoesNotBlockStartup() {
        doThrow(new DataAccessResourceFailureException("down")).when(jdbcTemplate).execute(NotificationSchemaMigration.DROP_TYPE_CHECK);

        assertDoesNotThrow(() -> migration.run(null));
    }
}
