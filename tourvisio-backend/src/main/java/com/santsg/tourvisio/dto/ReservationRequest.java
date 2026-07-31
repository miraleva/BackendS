package com.santsg.tourvisio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {

    @NotBlank(message = "Reservation type cannot be blank (e.g., HOTEL, FLIGHT)")
    private String type;

    @NotBlank(message = "Item name cannot be blank")
    private String itemName;

    @NotBlank(message = "Destination cannot be blank")
    private String destination;

    @NotNull(message = "Start date cannot be null")
    private LocalDate startDate;

    @NotNull(message = "End date cannot be null")
    private LocalDate endDate;

    @NotNull(message = "Total price cannot be null")
    @Min(value = 0, message = "Total price must be non-negative")
    private Double totalPrice;

    @NotBlank(message = "Currency cannot be blank")
    private String currency;

    private String chatSessionId;

    @NotEmpty(message = "Reservation must have at least one passenger")
    @Valid
    private List<PassengerRequest> passengers;

    @AssertTrue(message = "First passenger contact information is required.")
    public boolean isPrimaryContactValid() {
        if (passengers == null || passengers.isEmpty()) {
            return true;
        }

        PassengerRequest primary = passengers.get(0);

        boolean hasEmail = primary.getEmail() != null && !primary.getEmail().isBlank();
        boolean hasPhone = primary.getPhoneNumber() != null && !primary.getPhoneNumber().isBlank();

        return hasEmail && hasPhone;
    }

    private String imageUrl;

    private String lang;

    private String flightNumber;

    private String departureAirportCode;

    private String arrivalAirportCode;

    private String departureCity;

    private String arrivalCity;

    private String departureTime;

    private String arrivalTime;

    private String ticketClass;

    private String baggageAllowance;

    private String roomType;

    private String boardType;

    private String checkInTime;

    private String checkOutTime;

    public ReservationRequest(String type, String itemName, String destination, LocalDate startDate, LocalDate endDate, Double totalPrice, String currency, List<PassengerRequest> passengers, String chatSessionId, String imageUrl, String lang) {
        this.type = type;
        this.itemName = itemName;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalPrice = totalPrice;
        this.currency = currency;
        this.passengers = passengers;
        this.chatSessionId = chatSessionId;
        this.imageUrl = imageUrl;
        this.lang = lang;
    }
}
