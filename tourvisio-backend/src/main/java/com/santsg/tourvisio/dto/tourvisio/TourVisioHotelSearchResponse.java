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
public class TourVisioHotelSearchResponse {
    private List<TourVisioHotelItem> hotels;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TourVisioHotelItem {
        private String name;
        private String region;
        private int stars;
        private double price;
        private String currency;
        private String pensionType;
        private boolean available;
    }
}
