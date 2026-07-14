package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TourVisio Flight Product - Price Search istek formati.
 *
 * <p>Otel aramasindan farkli olarak "night"/"roomCriteria" degil,
 * "serviceTypes" (1=OneWay, 2=Roundtrip, 3=Multicity) ve "passengers"
 * kullanir. Bkz. docs-ai.santsg.com/tourvisio &gt; Flight Product &gt;
 * Price Search Method.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class TourVisioFlightSearchRequest {

    private int productType; // 3 = Flight
    private List<String> serviceTypes; // ["1"]=OneWay, ["2"]=Roundtrip
    private String checkIn; // yyyy-MM-dd, gidis tarihi
    private String checkOut; // yyyy-MM-dd, gidis-donus ise donus tarihi (opsiyonel)
    // Bazi rotalar/saglayicilar (paket fiyatlandirma motoruna dusenler) bu alan
    // gonderilmezse "Night Parameter can not be null" hatasi veriyor; guvenlik
    // icin her zaman gonderiyoruz.
    private int night;
    private List<LocationCriteria> departureLocations;
    private List<LocationCriteria> arrivalLocations;
    private List<PassengerCriteria> passengers;
    private boolean showOnlyNonStopFlight;
    private boolean acceptPendingProviders;
    private boolean forceFlightBundlePackage;
    private boolean disablePackageOfferTotalPrice;
    private boolean calculateFlightFees;
    private String culture;
    private String currency;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationCriteria {
        private String id;
        private int type; // 5 = Airport (Location Types tablosu)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerCriteria {
        private int type; // 1=Adult, 2=Child, 3=Infant (Passenger Types tablosu)
        private int count;
    }
}
