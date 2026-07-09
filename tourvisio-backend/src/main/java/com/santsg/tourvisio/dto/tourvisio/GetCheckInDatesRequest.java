package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request DTO for TourVisio GetCheckInDates API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetCheckInDatesRequest {

    @JsonProperty("ProductType")
    private int productType;

    @JsonProperty("IncludeSubLocations")
    private boolean includeSubLocations;

    @JsonProperty("Product")
    private String product;

    @JsonProperty("ArrivalLocations")
    private List<ArrivalLocation> arrivalLocations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArrivalLocation {
        @JsonProperty("Id")
        private String id;

        @JsonProperty("Type")
        private int type;
    }
}
