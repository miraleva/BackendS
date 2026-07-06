package com.santsg.tourvisio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchResponseItem {
    private String airline;
    private String departureTime;
    private String arrivalTime;
    private String transfers;
    private String baggage;
    private Double price;
    private String currency;
}
