package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * TourVisio PriceSearch / HotelPriceSearch request formatı.
 *
 * <p>TourVisio dokümantasyonundaki zorunlu alanlar:
 * productType, arrivalLocations, roomCriteria, nationality,
 * checkIn, night, currency, culture</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourVisioHotelSearchRequest {
    private int productType;        // 2 = Hotel
    private boolean checkAllotment;
    private boolean checkStopSale;
    private boolean getOnlyDiscountedPrice;
    private boolean getOnlyBestOffers;
    private List<LocationCriteria> arrivalLocations;
    private List<RoomCriteria> roomCriteria;
    private String checkIn;         // ISO Format: yyyy-MM-dd
    private int night;              // Gece sayısı
    private String currency;
    private String culture;         // "tr-TR" or "en-US"
    private String nationality;     // "TR"

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationCriteria {
        private String id;
        private int type;           // 2 = City
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
