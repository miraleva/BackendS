package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TourVisio Flight Product - Price Search cevap formati.
 *
 * <p>Sadece bizim kullandigimiz alanlar modellendi; docs-ai.santsg.com'daki
 * tam cevap cok daha fazla alan iceriyor (services, fees, seatInfo vb.),
 * ignoreUnknown=true ile bunlar sessizce yok sayilir.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourVisioFlightSearchResponse {

    private Header header;
    private Body body;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String requestId;
        private boolean success;
        private List<Message> messages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String code;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private String searchId;
        private List<FlightGroup> flights;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlightGroup {
        private int provider;
        private String id;
        // Tek yonde 1 eleman; gidis-donuste TourVisio bircok saglayici icin
        // 2 eleman doner: items[0]=gidis, items[1]=donus (ayni grup, TEK
        // offer/toplam fiyatla paketlenmis). Canli veriyle dogrulanmistir.
        private List<FlightItem> items;
        // Bazi saglayicilar tekil "offer" (bu grubun toplam fiyati), bazilari
        // coklu "offers" + groupKeys ile gidis/donus eslestirmesi ister.
        // Ikisini de destekliyoruz.
        private Offer offer;
        private List<Offer> offers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlightItem {
        private String flightNo;
        private String flightDate;
        private Airline airline;
        private Airline marketingAirline;
        private int duration;
        private FlightPoint departure;
        private FlightPoint arrival;
        private List<Segment> segments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Segment {
        private String id;
        private String flightNo;
        private FlightPoint departure;
        private FlightPoint arrival;
        private int duration;
        private List<BaggageInformation> baggageInformations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BaggageInformation {
        private double weight;
        private int piece;
        private int baggageType; // 1 = Checkin, 2 = Hand
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Airline {
        private String id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlightPoint {
        private NamedRef city;
        private Airport airport;
        private String date;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Airport {
        private String id;
        private String name;
        private String code;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NamedRef {
        private String id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Offer {
        private int segmentNumber;
        private Money singleAdultPrice;
        // Gidis-donus eslestirmesi icin: bu teklifin hangi "grup"lara ait
        // oldugunu belirtir. Iki farkli ucusun (gidis/donus) teklifleri en az
        // bir ortak groupKey paylasiyorsa birlikte satin alinabilir demektir.
        // Bkz. docs-ai.santsg.com/tourvisio > Flight Product >
        // "How to use group keys in roundtrip response".
        private List<String> groupKeys;
        private List<OfferId> offerIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OfferId {
        private String offerId;
        private String groupKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Money {
        private double amount;
        private String currency;
    }
}
