package com.example.MedRational.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate created a check constraint on notifications.type listing the NotifType values of the
 * time, and ddl-auto=update never updates it, so it would reject newer values such as
 * WORKSHOP_CREATED. The entity's enum still validates the type. Safe to run on each startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSchemaMigration implements ApplicationRunner {

    static final String DROP_TYPE_CHECK = "ALTER TABLE IF EXISTS notifications DROP CONSTRAINT IF EXISTS notifications_type_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(DROP_TYPE_CHECK);
        } catch (RuntimeException e) {
            // Never block startup on a cleanup step
            log.warn("Notification migration step failed: {}", DROP_TYPE_CHECK, e);
        }
    }
}
