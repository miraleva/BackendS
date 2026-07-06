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

    @NotBlank(message = "Departure location cannot be empty")
    private String departureLocation;

    @NotBlank(message = "Arrival location cannot be empty")
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
}
