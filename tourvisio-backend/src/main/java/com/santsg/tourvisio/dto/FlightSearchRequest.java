package com.santsg.tourvisio.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchRequest {

    @jakarta.validation.constraints.Size(max = 100, message = "Departure location cannot exceed 100 characters")
    private String departureLocation;

    @jakarta.validation.constraints.Size(max = 100, message = "Arrival location cannot exceed 100 characters")
    private String arrivalLocation;

    @NotNull(message = "Departure date cannot be null")
    private LocalDate departureDate;

    @NotNull(message = "Passenger count cannot be null")
    @Min(value = 1, message = "Passenger count must be at least 1")
    private Integer passengerCount;

    @NotBlank(message = "Trip type cannot be empty")
    private String tripType; // "ONE_WAY" or "ROUND_TRIP"

    @NotBlank(message = "Currency cannot be empty")
    private String currency;

    // New fields requested by user
    private String departureAirport;
    private String arrivalAirport;
    private LocalDate returnDate;
    private Integer childCount;
    private java.util.List<Integer> childAges;
    private Integer infantCount;
    private Integer roomCount;

    // Custom constructor for backward compatibility with 6-arg usage in tests/controllers
    public FlightSearchRequest(String departureLocation, String arrivalLocation, LocalDate departureDate,
                               Integer passengerCount, String tripType, String currency) {
        this.departureLocation = departureLocation;
        this.arrivalLocation = arrivalLocation;
        this.departureDate = departureDate;
        this.passengerCount = passengerCount;
        this.tripType = tripType;
        this.currency = currency;
        // Map departureLocation to departureAirport and vice versa as a fallback
        this.departureAirport = departureLocation;
        this.arrivalAirport = arrivalLocation;
    }
}
