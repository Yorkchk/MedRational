package com.example.MedRational.Config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountDeletionSchemaMigrationTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AccountDeletionSchemaMigration migration = new AccountDeletionSchemaMigration(jdbcTemplate);

    // Otherwise deleting an author or a rater fails on the NOT NULL columns
    @Test
    void relaxesTheAuthorAndRaterColumns() {
        migration.run(null);

        verify(jdbcTemplate).execute("ALTER TABLE IF EXISTS workshop_projects ALTER COLUMN author_id DROP NOT NULL");
        verify(jdbcTemplate).execute("ALTER TABLE IF EXISTS suggestion_messages ALTER COLUMN author_id DROP NOT NULL");
        verify(jdbcTemplate).execute("ALTER TABLE IF EXISTS file_ratings ALTER COLUMN user_id DROP NOT NULL");
    }

    @Test
    void aFailingStepDoesNotStopTheOthersNorStartup() {
        doThrow(new DataAccessResourceFailureException("down"))
                .when(jdbcTemplate).execute(AccountDeletionSchemaMigration.STATEMENTS.get(0));

        assertDoesNotThrow(() -> migration.run(null));

        verify(jdbcTemplate).execute(AccountDeletionSchemaMigration.STATEMENTS.get(2));
    }
}
