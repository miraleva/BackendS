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
public class TourVisioHotelSearchRequest {
    private int productType; // 2
    private boolean checkAllotment;
    private boolean checkStopSale;
    private boolean getOnlyDiscountedPrice;
    private boolean getOnlyBestOffers;
    private List<LocationCriteria> arrivalLocations;
    private List<RoomCriteria> roomCriteria;
    private String checkIn; // ISO Format yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss
    private String checkOut; // ISO Format yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss
    private String currency;
    private String culture; // "tr-TR" or "en-US"
    private String nationality; // "TR"

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationCriteria {
        private String id;
        private int type; // typically 2
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
