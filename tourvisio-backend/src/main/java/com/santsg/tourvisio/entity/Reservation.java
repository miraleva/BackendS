package com.santsg.tourvisio.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_number", unique = true, nullable = false)
    private String reservationNumber;

    @Column(name = "user_id", nullable = true)
    private Long userId;

    @Column(name = "is_guest")
    @Builder.Default
    private Boolean isGuest = false;

    @Column(nullable = false)
    private String type; // e.g., HOTEL, FLIGHT

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(nullable = false)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_price", nullable = false)
    private Double totalPrice;

    @Column(nullable = false)
    private String currency;

    @Column(name = "chat_session_id", length = 100)
    private String chatSessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "status")
    @Builder.Default
    private String status = "Completed";

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "flight_number")
    private String flightNumber;

    @Column(name = "departure_airport_code")
    private String departureAirportCode;

    @Column(name = "arrival_airport_code")
    private String arrivalAirportCode;

    @Column(name = "departure_city")
    private String departureCity;

    @Column(name = "arrival_city")
    private String arrivalCity;

    @Column(name = "departure_time")
    private String departureTime;

    @Column(name = "arrival_time")
    private String arrivalTime;

    @Column(name = "ticket_class")
    private String ticketClass;

    @Column(name = "baggage_allowance")
    private String baggageAllowance;

    @Column(name = "room_type")
    private String roomType;

    @Column(name = "board_type")
    private String boardType;

    @Column(name = "check_in_time")
    private String checkInTime;

    @Column(name = "check_out_time")
    private String checkOutTime;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<Passenger> passengers = new ArrayList<>();

    @Transient
    public String getPnrCode() {
        return this.reservationNumber;
    }

    @Transient
    public String getBookingNumber() {
        return this.reservationNumber;
    }

    @Transient
    public String getPrimaryGuestName() {
        if (passengers != null && !passengers.isEmpty()) {
            Passenger p = passengers.get(0);
            return ((p.getFirstName() != null ? p.getFirstName() : "") + " " + (p.getLastName() != null ? p.getLastName() : "")).trim();
        }
        return null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
