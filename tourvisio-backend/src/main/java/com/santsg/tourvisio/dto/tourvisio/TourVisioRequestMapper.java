package com.santsg.tourvisio.dto.tourvisio;

import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.chat.SearchCriteria;

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

    /**
     * SearchCriteria'dan direkt TourVisio PriceSearch isteği oluşturur.
     *
     * <p>Chatbot tarafından toplanan kriterleri (dil, milliyet, para birimi dahil)
     * TourVisio formatına çevirir. Autocomplete'den gelen resolvedId ve locationType
     * arrivalLocations olarak eklenir.</p>
     *
     * @param criteria     Chatbot'tan gelen SearchCriteria
     * @param resolvedId   Autocomplete'den çözümlenen location ID
     * @param locationType Autocomplete'den gelen type (1=City, 2=Hotel vb.)
     */
    public static TourVisioHotelSearchRequest toHotelSearchRequestFromCriteria(
            SearchCriteria criteria, String resolvedId, int locationType) {

        // Gece sayısı
        int nights = (int) ChronoUnit.DAYS.between(criteria.getCheckInDate(), criteria.getCheckOutDate());
        if (nights <= 0) nights = 1;

        // Location
        TourVisioHotelSearchRequest.LocationCriteria location =
                TourVisioHotelSearchRequest.LocationCriteria.builder()
                        .id(resolvedId)
                        .type(locationType)
                        .build();

        // childAges
        List<Integer> childAges = criteria.getChildAges() != null
                ? criteria.getChildAges() : new ArrayList<>();

        // Oda kriterleri (varsayılan 1 oda)
        int roomCount = criteria.getRoomCount() != null && criteria.getRoomCount() > 0
                ? criteria.getRoomCount() : 1;

        List<TourVisioHotelSearchRequest.RoomCriteria> rooms = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) {
            rooms.add(TourVisioHotelSearchRequest.RoomCriteria.builder()
                    .adult(criteria.getAdultCount() != null ? criteria.getAdultCount() : 2)
                    .childAges(i == 0 ? childAges : new ArrayList<>())
                    .build());
        }

        // Nationality
        String nationality = criteria.getNationality() != null && !criteria.getNationality().isBlank()
                ? criteria.getNationality() : "TR";

        // Currency
        String currency = criteria.getCurrency() != null && !criteria.getCurrency().isBlank()
                ? criteria.getCurrency() : "EUR";

        // Culture — kullanıcının dil tercihinden TourVisio culture koduna çevir
        String culture = resolveCulture(criteria.getPreferredLanguage());

        return TourVisioHotelSearchRequest.builder()
                .productType(2)
                .checkAllotment(true)
                .checkStopSale(true)
                .getOnlyDiscountedPrice(false)
                .getOnlyBestOffers(true)
                .arrivalLocations(List.of(location))
                .roomCriteria(rooms)
                .checkIn(criteria.getCheckInDate().toString())
                .night(nights)
                .currency(currency)
                .culture(culture)
                .nationality(nationality)
                .build();
    }

    /**
     * Dil adını TourVisio culture koduna çevirir.
     * Örnek: "English" → "en-US", "German" → "de-DE"
     */
    private static String resolveCulture(String preferredLanguage) {
        if (preferredLanguage == null || preferredLanguage.isBlank()) return "tr-TR";
        return switch (preferredLanguage.toLowerCase().trim()) {
            case "english", "en", "united states", "united kingdom", "australia" -> "en-US";
            case "german", "de", "germany", "austria" -> "de-DE";
            case "french", "fr", "france", "belgium" -> "fr-FR";
            case "russian", "ru", "russia" -> "ru-RU";
            case "arabic", "ar" -> "ar-SA";
            case "dutch", "nl", "netherlands" -> "nl-NL";
            case "polish", "pl", "poland" -> "pl-PL";
            case "spanish", "es", "spain" -> "es-ES";
            case "italian", "it", "italy" -> "it-IT";
            default -> "tr-TR";
        };
    }
}
