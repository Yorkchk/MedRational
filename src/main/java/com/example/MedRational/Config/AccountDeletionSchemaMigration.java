package com.example.MedRational.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deleting an account keeps projects, admin replies and ratings with their author set to null, but
 * Hibernate created these columns NOT NULL and ddl-auto=update never relaxes them. Safe to run on
 * each startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionSchemaMigration implements ApplicationRunner {

    static final List<String> STATEMENTS = List.of(
            "ALTER TABLE IF EXISTS workshop_projects ALTER COLUMN author_id DROP NOT NULL",
            "ALTER TABLE IF EXISTS suggestion_messages ALTER COLUMN author_id DROP NOT NULL",
            "ALTER TABLE IF EXISTS file_ratings ALTER COLUMN user_id DROP NOT NULL"
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : STATEMENTS) {
            try {
                jdbcTemplate.execute(sql);
            } catch (RuntimeException e) {
                // Never block startup on a cleanup step
                log.warn("Account deletion migration step failed: {}", sql, e);
            }
        }
    }
}
