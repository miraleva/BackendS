package com.santsg.tourvisio.dto.tourvisio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourVisioBookingResponse {
    private boolean success;
    private String reservationNumber;
    private String message;
    private String status;
}
