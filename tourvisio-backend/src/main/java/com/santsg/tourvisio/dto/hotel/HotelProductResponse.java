package com.santsg.tourvisio.dto.hotel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for normalized hotel product response sent to frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HotelProductResponse {

    @JsonProperty("hotelId")
    private String hotelId;

    @JsonProperty("hotelName")
    private String hotelName;

    @JsonProperty("region")
    private String region;

    @JsonProperty("starRating")
    private Integer starRating;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("boardType")
    private String boardType;

    @JsonProperty("availabilityStatus")
    private String availabilityStatus;

    @JsonProperty("imageUrl")
    private String imageUrl;
}
