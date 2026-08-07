package com.santsg.tourvisio.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Database schema initializer to ensure columns in 'users' table
 * that may be omitted during OAuth sign-up
 * (password, phone, country, gender, date_of_birth)
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

        log.info(
                "[DatabaseSchemaInitializer] Ensuring database schema is up to date...");

        String[] alterStatements = {

                // =========================================================
                // API LOGS
                // =========================================================
                "CREATE TABLE IF NOT EXISTS api_logs (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "timestamp VARCHAR(255), " +
                        "method VARCHAR(10), " +
                        "uri VARCHAR(1024), " +
                        "endpoint_type VARCHAR(50), " +
                        "latency_ms BIGINT, " +
                        "status_code INTEGER, " +
                        "status_text VARCHAR(100), " +
                        "error_message VARCHAR(1024), " +
                        "success BOOLEAN, " +
                        "request_payload TEXT, " +
                        "response_payload TEXT" +
                        ")",

                // =========================================================
                // NOTIFICATIONS
                // =========================================================
                "CREATE TABLE IF NOT EXISTS notifications (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "user_id BIGINT NOT NULL, " +
                        "title VARCHAR(200) NOT NULL, " +
                        "message VARCHAR(1000) NOT NULL, " +
                        "type VARCHAR(50) NOT NULL, " +
                        "is_read BOOLEAN NOT NULL DEFAULT FALSE, " +
                        "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                        +
                        ")",

                // =========================================================
                // USERS - NULLABLE FIELDS
                // =========================================================
                "ALTER TABLE users ALTER COLUMN password DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN phone DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN country DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN date_of_birth DROP NOT NULL",
                "ALTER TABLE users ALTER COLUMN gender DROP NOT NULL",

                // =========================================================
                // USERS - AUTH / ACCOUNT FIELDS
                // =========================================================
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS last_logout_at TIMESTAMP WITH TIME ZONE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_two_factor_enabled BOOLEAN DEFAULT FALSE",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_email_verified BOOLEAN DEFAULT FALSE",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_phone_verified BOOLEAN DEFAULT FALSE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20) DEFAULT 'LOCAL'",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'user'",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE",

                // =========================================================
                // USERS - NOTIFICATION SETTINGS
                // =========================================================
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_booking_confirmations BOOLEAN DEFAULT TRUE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_booking_changes BOOLEAN DEFAULT TRUE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_flight_reminder BOOLEAN DEFAULT TRUE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_check_in_reminder BOOLEAN DEFAULT TRUE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_hotel_reminder BOOLEAN DEFAULT TRUE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_price_changes BOOLEAN DEFAULT FALSE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_campaigns BOOLEAN DEFAULT FALSE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_in_app BOOLEAN DEFAULT TRUE",

                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_email BOOLEAN DEFAULT TRUE",

                // =========================================================
                // RESERVATIONS
                // =========================================================
                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS is_guest BOOLEAN DEFAULT FALSE",

                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS chat_session_id VARCHAR(255)",

                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'Completed'",

                "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000)",

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

                log.info(
                        "[DatabaseSchemaInitializer] Executed DDL: {}",
                        sql);

            } catch (Exception e) {

                /*
                 * Table henüz oluşmadıysa veya ilgili constraint zaten
                 * değiştirilmişse uygulamanın tamamen kapanmasını engeller.
                 */
                log.debug(
                        "[DatabaseSchemaInitializer] DDL notice for '{}': {}",
                        sql,
                        e.getMessage());
            }
        }

        // =========================================================
        // EXISTING USERS - NOTIFICATION DEFAULT VALUES
        // =========================================================

        /*
         * Kolonlar daha önceden NULL olarak oluşmuşsa mevcut kullanıcıların
         * bildirim tercihlerine varsayılan değerleri verir.
         */
        try {

            jdbcTemplate.update(
                    """
                            UPDATE users
                            SET
                                notify_booking_confirmations =
                                    COALESCE(notify_booking_confirmations, TRUE),

                                notify_booking_changes =
                                    COALESCE(notify_booking_changes, TRUE),

                                notify_flight_reminder =
                                    COALESCE(notify_flight_reminder, TRUE),

                                notify_check_in_reminder =
                                    COALESCE(notify_check_in_reminder, TRUE),

                                notify_hotel_reminder =
                                    COALESCE(notify_hotel_reminder, TRUE),

                                notify_price_changes =
                                    COALESCE(notify_price_changes, FALSE),

                                notify_campaigns =
                                    COALESCE(notify_campaigns, FALSE),

                                notify_in_app =
                                    COALESCE(notify_in_app, TRUE),

                                notify_email =
                                    COALESCE(notify_email, TRUE)
                            """);

            log.info(
                    "[DatabaseSchemaInitializer] Notification defaults checked successfully.");

        } catch (Exception e) {

            log.debug(
                    "[DatabaseSchemaInitializer] Notification defaults could not be updated yet: {}",
                    e.getMessage());
        }

        // =========================================================
        // MOCK / TEST USER CLEANUP
        // =========================================================

        try {

            log.info(
                    "[DatabaseSchemaInitializer] Temizlik işlemi başlatılıyor: " +
                            "Örnek test kullanıcıları ve bağlı kayıtları temizleniyor...");

            // 1. example.com uzantılı kullanıcıların rezervasyonlarını sil
            int deletedReservations = jdbcTemplate.update(
                    """
                            DELETE FROM reservations
                            WHERE user_id IN (
                                SELECT id
                                FROM users
                                WHERE email LIKE '%example.com'
                            )
                            """);

            // 2. example.com kullanıcılarının sohbet mesajlarını sil
            int deletedMessages = jdbcTemplate.update(
                    """
                            DELETE FROM chat_messages
                            WHERE session_id IN (
                                SELECT id
                                FROM chat_sessions
                                WHERE user_id IN (
                                    SELECT id
                                    FROM users
                                    WHERE email LIKE '%example.com'
                                )
                            )
                            """);

            // 3. example.com kullanıcılarının sohbet oturumlarını sil
            int deletedSessions = jdbcTemplate.update(
                    """
                            DELETE FROM chat_sessions
                            WHERE user_id IN (
                                SELECT id
                                FROM users
                                WHERE email LIKE '%example.com'
                            )
                            """);

            // 4. example.com kullanıcılarını sil
            int deletedUsers = jdbcTemplate.update(
                    """
                            DELETE FROM users
                            WHERE email LIKE '%example.com'
                            """);

            log.info(
                    "[DatabaseSchemaInitializer] Temizlik tamamlandı: " +
                            "{} rezervasyon, {} mesaj, {} oturum, {} test kullanıcısı silindi.",
                    deletedReservations,
                    deletedMessages,
                    deletedSessions,
                    deletedUsers);

        } catch (Exception e) {

            log.warn(
                    "[DatabaseSchemaInitializer] Temizlik esnasında hata/uyarı oluştu: {}",
                    e.getMessage());
        }
    }
}