package com.santsg.tourvisio.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Database schema initializer to ensure columns in 'users' table
 * that may be omitted during OAuth sign-up (password, phone, country, gender, date_of_birth)
 * do not have NOT NULL constraints in PostgreSQL database.
 */
@Component
@Slf4j
public class DatabaseSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("[DatabaseSchemaInitializer] Ensuring 'users' table columns allow NULL values for OAuth sign-up...");
        String[] alterStatements = {
                "ALTER TABLE users ALTER COLUMN password DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN phone DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN country DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN date_of_birth DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN gender DROP NOT NULL"
        };

        for (String sql : alterStatements) {
            try {
                jdbcTemplate.execute(sql);
                log.info("[DatabaseSchemaInitializer] Executed DDL: {}", sql);
            } catch (Exception e) {
                // Ignore errors if table does not exist yet (e.g. H2 ddl-auto) or constraint already dropped
                log.debug("[DatabaseSchemaInitializer] DDL notice for '{}': {}", sql, e.getMessage());
            }
        }
    }
}
