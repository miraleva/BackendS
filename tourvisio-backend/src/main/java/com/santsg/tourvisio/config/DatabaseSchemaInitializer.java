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
                "CREATE TABLE IF NOT EXISTS api_logs (id BIGSERIAL PRIMARY KEY, timestamp VARCHAR(255), method VARCHAR(10), uri VARCHAR(1024), endpoint_type VARCHAR(50), latency_ms BIGINT, status_code INTEGER, status_text VARCHAR(100), error_message VARCHAR(1024), success BOOLEAN, request_payload TEXT, response_payload TEXT)",
                "ALTER TABLE users ALTER COLUMN password DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN phone DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN country DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN date_of_birth DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN gender DROP NOT NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS last_logout_at TIMESTAMP WITH TIME ZONE",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_two_factor_enabled BOOLEAN DEFAULT FALSE",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS flight_number VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS departure_airport_code VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS arrival_airport_code VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS departure_city VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS arrival_city VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS departure_time VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS arrival_time VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS ticket_class VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS baggage_allowance VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS room_type VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS board_type VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS check_in_time VARCHAR(255)",
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS check_out_time VARCHAR(255)"
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

        // Mock verileri temizleme rutini (Jane Doe ve Test User'ları temizler)
        try {
            log.info("[DatabaseSchemaInitializer] Temizlik işlemi başlatılıyor: Örnek test kullanıcıları ve bağlı kayıtları temizleniyor...");
            
            // 1. example.com uzantılı kullanıcıların rezervasyonlarını sil
            int deletedReservations = jdbcTemplate.update(
                "DELETE FROM reservations WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%example.com')"
            );
            
            // 2. example.com uzantılı kullanıcıların sohbet mesajlarını sil
            int deletedMessages = jdbcTemplate.update(
                "DELETE FROM chat_messages WHERE session_id IN (SELECT id FROM chat_sessions WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%example.com'))"
            );
            
            // 3. example.com uzantılı kullanıcıların sohbet oturumlarını sil
            int deletedSessions = jdbcTemplate.update(
                "DELETE FROM chat_sessions WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%example.com')"
            );
            
            // 4. example.com uzantılı kullanıcıları sil
            int deletedUsers = jdbcTemplate.update(
                "DELETE FROM users WHERE email LIKE '%example.com'"
            );
            
            log.info("[DatabaseSchemaInitializer] Temizlik tamamlandı: {} rezervasyon, {} mesaj, {} oturum, {} test kullanıcısı silindi.", 
                deletedReservations, deletedMessages, deletedSessions, deletedUsers);
        } catch (Exception e) {
            log.warn("[DatabaseSchemaInitializer] Temizlik esnasında hata/uyarı oluştu: {}", e.getMessage());
        }
    }
}
