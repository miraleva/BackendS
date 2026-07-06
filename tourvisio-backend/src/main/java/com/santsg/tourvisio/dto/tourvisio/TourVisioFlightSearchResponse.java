package com.santsg.tourvisio.dto.tourvisio;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourVisioFlightSearchResponse {
    private List<TourVisioFlightItem> flights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TourVisioFlightItem {
        private String airline;
        private String departureTime;
        private String arrivalTime;
        private String transfers;
        private String baggage;
        private double price;
        private String currency;
    }
}
