package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for TourVisio GetProductInfo API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetProductInfoRequest {

    @JsonProperty("productType")
    private int productType;

    @JsonProperty("ownerProvider")
    private int ownerProvider;

    @JsonProperty("product")
    private String product;

    @JsonProperty("culture")
    private String culture;
}
