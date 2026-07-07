package com.santsg.tourvisio.dto.hotel;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for hotel search request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HotelSearchRequest {

    @NotBlank
    @JsonProperty("locationOrHotelName")
    private String locationOrHotelName;

    @NotNull
    @JsonProperty("checkInDate")
    private LocalDate checkInDate;

    @NotNull
    @JsonProperty("checkOutDate")
    private LocalDate checkOutDate;

    @Positive
    @JsonProperty("adultCount")
    private int adultCount;

    @PositiveOrZero
    @JsonProperty("childCount")
    private int childCount;

    @JsonProperty("childAges")
    private List<Integer> childAges;

    @NotBlank
    @JsonProperty("nationality")
    private String nationality;

    @NotBlank
    @JsonProperty("currency")
    private String currency;

    @Positive
    @JsonProperty("roomCount")
    private int roomCount;

    @Min(0)
    @Max(5)
    @JsonProperty("starRating")
    private Integer starRating;

    @JsonProperty("boardType")
    private String boardType;

    @PositiveOrZero
    @JsonProperty("minPrice")
    private Double minPrice;

    @PositiveOrZero
    @JsonProperty("maxPrice")
    private Double maxPrice;

    @JsonProperty("region")
    private String region;

    @JsonProperty("sortBy")
    private String sortBy;
}
