package com.santsg.tourvisio.dto.tourvisio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourVisioFlightSearchRequest {
    private String departureLocation;
    private String arrivalLocation;
    private String departureDate;
    private int passengerCount;
    private String tripType; // ONE_WAY or ROUND_TRIP
    private String currency;
}
