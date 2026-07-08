package com.santsg.tourvisio.dto.tourvisio;

import com.santsg.tourvisio.dto.HotelSearchRequest;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Bizim iç {@link HotelSearchRequest} DTO'sunu TourVisio API formatına çevirir.
 */
public class TourVisioRequestMapper {

    /**
     * Autocomplete isteği oluşturur.
     * ProductType=2 (Otel), Culture=tr-TR.
     */
    public static TourVisioAutocompleteRequest toAutocompleteRequest(HotelSearchRequest request) {
        return TourVisioAutocompleteRequest.builder()
                .productType(2)
                .query(request.getLocationOrHotelName())
                .culture("tr-TR")
                .build();
    }

    /**
     * Otel fiyat arama isteği oluşturur.
     *
     * @param request    Bizim iç HotelSearchRequest
     * @param resolvedId Autocomplete'den çözümlenen location ID
     * @param locationType Autocomplete'den gelen type (varsayılan 2 = City)
     */
    public static TourVisioHotelSearchRequest toHotelSearchRequest(
            HotelSearchRequest request, String resolvedId, int locationType) {

        // Gece sayısını hesapla
        int nights = (int) ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
        if (nights <= 0) {
            nights = 1;
        }

        // Location
        TourVisioHotelSearchRequest.LocationCriteria location =
                TourVisioHotelSearchRequest.LocationCriteria.builder()
                        .id(resolvedId)
                        .type(locationType)
                        .build();

        // Room criteria — childAges destekli
        List<Integer> childAges = request.getChildAges() != null
                ? request.getChildAges()
                : new ArrayList<>();

        // roomCount kadar oda oluştur (yoksa 1)
        int roomCount = request.getRoomCount() != null && request.getRoomCount() > 0
                ? request.getRoomCount() : 1;

        List<TourVisioHotelSearchRequest.RoomCriteria> rooms = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) {
            TourVisioHotelSearchRequest.RoomCriteria room =
                    TourVisioHotelSearchRequest.RoomCriteria.builder()
                            .adult(request.getAdultCount())
                            .childAges(i == 0 ? childAges : new ArrayList<>()) // Çocuklar 1. odaya
                            .build();
            rooms.add(room);
        }

        // Nationality
        String nationality = request.getNationality() != null && !request.getNationality().isBlank()
                ? request.getNationality() : "TR";

        // Currency
        String currency = request.getCurrency() != null && !request.getCurrency().isBlank()
                ? request.getCurrency() : "TRY";

        return TourVisioHotelSearchRequest.builder()
                .productType(2)
                .checkAllotment(true)
                .checkStopSale(true)
                .getOnlyDiscountedPrice(false)
                .getOnlyBestOffers(true)
                .arrivalLocations(List.of(location))
                .roomCriteria(rooms)
                .checkIn(request.getCheckInDate().toString())
                .night(nights)
                .currency(currency)
                .culture("tr-TR")
                .nationality(nationality)
                .build();
    }
}
