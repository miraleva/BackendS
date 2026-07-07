package com.santsg.tourvisio.dto.tourvisio;

import com.santsg.tourvisio.dto.HotelSearchRequest;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps our internal {@link HotelSearchRequest} into TourVisio-compliant request structures.
 */
public class TourVisioRequestMapper {

    public static TourVisioAutocompleteRequest toAutocompleteRequest(HotelSearchRequest request) {
        return TourVisioAutocompleteRequest.builder()
                .productType(2)
                .query(request.getLocationOrHotelName())
                .masterProductTypes(List.of(2))
                .culture("tr-TR")
                .build();
    }

    public static TourVisioHotelSearchRequest toHotelSearchRequest(HotelSearchRequest request, String resolvedId) {
        long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());

        TourVisioHotelSearchRequest.LocationCriteria location = TourVisioHotelSearchRequest.LocationCriteria.builder()
                .id(resolvedId != null ? resolvedId : "23494") // Fallback default city ID if unresolved
                .type(2)
                .build();

        TourVisioHotelSearchRequest.RoomCriteria room = TourVisioHotelSearchRequest.RoomCriteria.builder()
                .adult(request.getAdultCount())
                .childAges(new ArrayList<>())
                .build();

        return TourVisioHotelSearchRequest.builder()
                .productType(2)
                .checkAllotment(true)
                .checkStopSale(true)
                .getOnlyDiscountedPrice(false)
                .getOnlyBestOffers(true)
                .arrivalLocations(List.of(location))
                .roomCriteria(List.of(room))
                .checkIn(request.getCheckInDate().toString() + "T00:00:00")
                .checkOut(request.getCheckOutDate().toString() + "T00:00:00")
                .currency(request.getCurrency())
                .culture("tr-TR")
                .nationality("TR")
                .build();
    }
}
