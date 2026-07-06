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
public class HotelSearchRequest {

    @NotBlank(message = "Location cannot be empty")
    private String location;

    @NotNull(message = "Check-in date cannot be null")
    private LocalDate checkInDate;

    @NotNull(message = "Check-out date cannot be null")
    private LocalDate checkOutDate;

    @NotNull(message = "Adults count cannot be null")
    @Min(value = 1, message = "Adults count must be at least 1")
    private Integer adultsCount;

    @NotBlank(message = "Currency cannot be empty")
    private String currency;
}
