package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TourVisio Flight Product - Departure/Arrival Autocomplete istek formati.
 *
 * <p>Otel autocomplete'inden farkli olarak "ServiceType" (1=OneWay) ve,
 * varis (arrival) aramasinda, kalkis lokasyonunu bildiren
 * "DepartureLocations" alani gerekir.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class TourVisioFlightAutocompleteRequest {

    private int productType; // 3 = Flight
    private String query;
    private String serviceType; // "1" = OneWay
    private List<TourVisioFlightSearchRequest.LocationCriteria> departureLocations; // sadece arrival aramasinda
    private String culture;
}
