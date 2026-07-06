package com.santsg.tourvisio.dto.tourvisio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourVisioHotelSearchRequest {
    private String checkInDate;
    private String checkOutDate;
    private String destination;
    private String nationality;
    private int adultCount;
    private String currency;
}
