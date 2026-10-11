package com.example.MedRational.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off data fixes for the suggestion threads redesign. ddl-auto=update adds the new columns and
 * tables, but it never touches existing check constraints: Hibernate created one on suggestions.status
 * for the old PENDING/REVIEWED/RESOLVED values, which would reject CLOSED. Every statement is safe to
 * run on each startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuggestionSchemaMigration implements ApplicationRunner {

    private static final String[] STATEMENTS = {
            "ALTER TABLE IF EXISTS suggestions DROP CONSTRAINT IF EXISTS suggestions_status_check",
            // Old statuses: REVIEWED/RESOLVED meant the admin was done with it
            "UPDATE suggestions SET status = 'CLOSED' WHERE status IN ('REVIEWED', 'RESOLVED')",
            "UPDATE suggestions SET title = LEFT(message, 80) WHERE title IS NULL",
            "UPDATE suggestions SET last_user_activity_at = created_at WHERE last_user_activity_at IS NULL",
            "UPDATE suggestions SET last_activity_at = created_at WHERE last_activity_at IS NULL",
            "UPDATE suggestions SET message_count = 0 WHERE message_count IS NULL",
            "UPDATE suggestions SET closed_at = created_at WHERE status = 'CLOSED' AND closed_at IS NULL"
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : STATEMENTS) {
            try {
                jdbcTemplate.execute(sql);
            } catch (RuntimeException e) {
                // Never block startup on a cleanup step; the entity layer still validates statuses
                log.warn("Suggestion migration step failed: {}", sql, e);
            }
        }
    }
}
