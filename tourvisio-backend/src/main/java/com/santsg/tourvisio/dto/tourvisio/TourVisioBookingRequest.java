package com.santsg.tourvisio.dto.tourvisio;

import com.santsg.tourvisio.dto.PassengerRequest;
import com.santsg.tourvisio.dto.ReservationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourVisioBookingRequest {
    private String reservationType;
    private String itemName;
    private String destination;
    private String startDate;
    private String endDate;
    private Double totalPrice;
    private String currency;
    private List<PassengerRequest> passengers;

    public static TourVisioBookingRequest fromReservationRequest(ReservationRequest request) {
        if (request == null) return null;
        return TourVisioBookingRequest.builder()
                .reservationType(request.getType())
                .itemName(request.getItemName())
                .destination(request.getDestination())
                .startDate(request.getStartDate() != null ? request.getStartDate().toString() : null)
                .endDate(request.getEndDate() != null ? request.getEndDate().toString() : null)
                .totalPrice(request.getTotalPrice())
                .currency(request.getCurrency())
                .passengers(request.getPassengers())
                .build();
    }
}
