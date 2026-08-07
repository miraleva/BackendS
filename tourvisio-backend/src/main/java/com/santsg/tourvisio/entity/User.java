package com.santsg.tourvisio.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 40)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 40)
    private String lastName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone", unique = true, nullable = true, length = 16)
    private String phone;

    @Column(name = "password", nullable = true, length = 255)
    @JsonIgnore
    private String password;

    @Column(name = "country", nullable = true, length = 100)
    private String country;

    @Column(name = "gender", nullable = true, length = 20)
    private String gender;

    @Column(name = "date_of_birth", nullable = true)
    private LocalDate dateOfBirth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Authentication provider: LOCAL, GOOGLE */
    @Column(name = "auth_provider", nullable = true, length = 20)
    @Builder.Default
    private String authProvider = "LOCAL";

    @Column(name = "role", length = 20)
    @Builder.Default
    private String role = "user";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_email_verified")
    @Builder.Default
    private Boolean isEmailVerified = false;

    @Column(name = "is_phone_verified")
    @Builder.Default
    private Boolean isPhoneVerified = false;

    @Column(name = "is_two_factor_enabled")
    @Builder.Default
    private Boolean isTwoFactorEnabled = false;

    // =========================================================
    // NOTIFICATION SETTINGS
    // =========================================================

    @Column(name = "notify_booking_confirmations")
    @Builder.Default
    private Boolean notifyBookingConfirmations = true;

    @Column(name = "notify_booking_changes")
    @Builder.Default
    private Boolean notifyBookingChanges = true;

    @Column(name = "notify_flight_reminder")
    @Builder.Default
    private Boolean notifyFlightReminder = true;

    @Column(name = "notify_check_in_reminder")
    @Builder.Default
    private Boolean notifyCheckInReminder = true;

    @Column(name = "notify_hotel_reminder")
    @Builder.Default
    private Boolean notifyHotelReminder = true;

    @Column(name = "notify_price_changes")
    @Builder.Default
    private Boolean notifyPriceChanges = false;

    @Column(name = "notify_campaigns")
    @Builder.Default
    private Boolean notifyCampaigns = false;

    @Column(name = "notify_in_app")
    @Builder.Default
    private Boolean notifyInApp = true;

    @Column(name = "notify_email")
    @Builder.Default
    private Boolean notifyEmail = true;

    // =========================================================

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_logout_at")
    private Instant lastLogoutAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatSession> chatSessions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public String getRole() {
        return role != null ? role : "user";
    }

    public Boolean getIsActive() {
        return isActive != null ? isActive : true;
    }

    public Boolean getIsTwoFactorEnabled() {
        return isTwoFactorEnabled != null
                ? isTwoFactorEnabled
                : false;
    }

    // =========================================================
    // NOTIFICATION DEFAULT GETTERS
    // =========================================================

    public Boolean getNotifyBookingConfirmations() {
        return notifyBookingConfirmations != null
                ? notifyBookingConfirmations
                : true;
    }

    public Boolean getNotifyBookingChanges() {
        return notifyBookingChanges != null
                ? notifyBookingChanges
                : true;
    }

    public Boolean getNotifyFlightReminder() {
        return notifyFlightReminder != null
                ? notifyFlightReminder
                : true;
    }

    public Boolean getNotifyCheckInReminder() {
        return notifyCheckInReminder != null
                ? notifyCheckInReminder
                : true;
    }

    public Boolean getNotifyHotelReminder() {
        return notifyHotelReminder != null
                ? notifyHotelReminder
                : true;
    }

    public Boolean getNotifyPriceChanges() {
        return notifyPriceChanges != null
                ? notifyPriceChanges
                : false;
    }

    public Boolean getNotifyCampaigns() {
        return notifyCampaigns != null
                ? notifyCampaigns
                : false;
    }

    public Boolean getNotifyInApp() {
        return notifyInApp != null
                ? notifyInApp
                : true;
    }

    public Boolean getNotifyEmail() {
        return notifyEmail != null
                ? notifyEmail
                : true;
    }
}