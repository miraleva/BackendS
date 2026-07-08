package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TourVisio PriceSearch / HotelPriceSearch gerçek response yapısı.
 *
 * <p>TourVisio response formatı:
 * <pre>
 * {
 *   "body": {
 *     "searchId": "...",
 *     "hotels": [
 *       {
 *         "id": "4481",
 *         "name": "HOTEL NAME",
 *         "stars": 5,
 *         "address": "...",
 *         "city": { "id": "23494", "name": "Antalya" },
 *         "town": { "name": "BELEK TV" },
 *         "thumbnailFull": "...",
 *         "offers": [
 *           {
 *             "offerId": "...",
 *             "isRefundable": false,
 *             "price": { "amount": 56.005, "currency": "EUR" },
 *             "rooms": [
 *               { "boardName": "ALL INCLUSIVE", "accomName": "DOUBLE ROOM" }
 *             ]
 *           }
 *         ]
 *       }
 *     ]
 *   }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourVisioHotelSearchResponse {

    private Header header;
    private Body body;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String requestId;
        private boolean success;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private String searchId;
        private String expiresOn;
        private List<HotelItem> hotels;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HotelItem {
        private String id;
        private String name;
        private int stars;
        private String address;
        private IdName city;
        private IdName town;
        private IdName country;
        private String thumbnail;
        private String thumbnailFull;
        private int provider;
        private List<Offer> offers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Offer {
        private String offerId;
        private boolean isRefundable;
        private boolean ownOffer;
        private Price price;
        private String checkIn;
        private int night;
        private List<Room> rooms;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Room {
        private String roomId;
        private String roomName;
        private String accomName;
        private String boardId;
        private String boardName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Price {
        private double amount;
        private String currency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IdName {
        private String id;
        private String name;
    }
}
