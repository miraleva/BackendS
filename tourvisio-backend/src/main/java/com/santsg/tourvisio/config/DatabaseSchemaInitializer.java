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
