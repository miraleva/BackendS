package com.santsg.tourvisio.dto.tourvisio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourVisioFlightSearchRequest {
    private int productType; // typically 13 or specific flight type
    private List<LocationCriteria> departureLocations;
    private List<LocationCriteria> arrivalLocations;
    private String checkIn; // departureDate yyyy-MM-dd
    private String checkOut; // optional returnDate yyyy-MM-dd
    private int night;
    private String currency;
    private String culture;
    private String nationality;
    private List<RoomCriteria> roomCriteria;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationCriteria {
        private String id;
        private int type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomCriteria {
        private int adult;
        private List<Integer> childAges;
    }
}
