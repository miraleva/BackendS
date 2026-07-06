package com.santsg.tourvisio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelSearchResponseItem {
    private String name;
    private String region;
    private Integer stars;
    private Double price;
    private String currency;
    private String pensionType;
    private Boolean available;
}
