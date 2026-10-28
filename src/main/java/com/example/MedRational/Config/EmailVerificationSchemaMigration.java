package com.example.MedRational.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sign-in no longer asks for a code, so only verified emails may sign in. ddl-auto=update adds
 * users.email_verified as null for existing accounts: mark the ones that already proved their email
 * (anyone who ever entered a code has last_login_at) and admins, which are created by bootstrap.
 * Abandoned sign-ups stay unverified. Safe to run on each startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationSchemaMigration implements ApplicationRunner {

    static final String BACKFILL = "UPDATE users SET email_verified = TRUE WHERE email_verified IS NULL"
            + " AND (last_login_at IS NOT NULL OR role = 'ROLE_ADMIN')";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.update(BACKFILL);
        } catch (RuntimeException e) {
            // Never block startup on a cleanup step
            log.warn("Email verification backfill failed: {}", BACKFILL, e);
        }
    }
}
