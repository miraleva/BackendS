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

        // TourVisio API beklentisi:
        // roomCriteria içinde adult = adultCount,
        // childAges = hem çocuklar hem de bebekler için yaş listesi.
        // Yaş verilmediyse çocuklar için varsayılan 7, bebekler için varsayılan 1 atanır.
        int explicitChildCount = request.getChildCount() != null ? request.getChildCount() : 0;
        int explicitInfantCount = request.getInfantCount() != null ? request.getInfantCount() : 0;
        int totalNonAdults = explicitChildCount + explicitInfantCount;

        List<Integer> mappedChildAges = new ArrayList<>();
        List<Integer> customChildAges = request.getChildAges() != null ? request.getChildAges() : new ArrayList<>();

        if (totalNonAdults > 0) {
            for (int i = 0; i < explicitChildCount; i++) {
                if (i < customChildAges.size()) {
                    mappedChildAges.add(customChildAges.get(i));
                } else {
                    mappedChildAges.add(7);
                }
            }
            for (int i = 0; i < explicitInfantCount; i++) {
                mappedChildAges.add(1);
            }
        }

        // roomCount kadar oda oluştur (yoksa 1)
        int roomCount = request.getRoomCount() != null && request.getRoomCount() > 0
                ? request.getRoomCount() : 1;

        int baseAdultCount = request.getAdultCount() != null ? request.getAdultCount() : 1;

        List<TourVisioHotelSearchRequest.RoomCriteria> rooms = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) {
            rooms.add(TourVisioHotelSearchRequest.RoomCriteria.builder()
                    .adult(baseAdultCount)
                    .childAges(i == 0 ? mappedChildAges : new ArrayList<>())
                    .build());
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

        int explicitChildCount = criteria.getChildCount() != null ? criteria.getChildCount() : 0;
        int explicitInfantCount = criteria.getInfantCount() != null ? criteria.getInfantCount() : 0;
        List<Integer> customChildAges = criteria.getChildAges() != null ? criteria.getChildAges() : new ArrayList<>();
        List<Integer> customInfantAges = criteria.getInfantAges() != null ? criteria.getInfantAges() : new ArrayList<>();

        List<Integer> mappedChildAges = new ArrayList<>();
        int totalNonAdults = Math.max(explicitChildCount, customChildAges.size()) + Math.max(explicitInfantCount, customInfantAges.size());

        if (totalNonAdults > 0) {
            int childLoopCount = Math.max(explicitChildCount, customChildAges.size());
            for (int i = 0; i < childLoopCount; i++) {
                if (i < customChildAges.size()) {
                    mappedChildAges.add(customChildAges.get(i));
                } else {
                    mappedChildAges.add(7);
                }
            }
            int infantLoopCount = Math.max(explicitInfantCount, customInfantAges.size());
            for (int i = 0; i < infantLoopCount; i++) {
                if (i < customInfantAges.size()) {
                    mappedChildAges.add(customInfantAges.get(i));
                } else {
                    mappedChildAges.add(1);
                }
            }
        }

        // Oda kriterleri (varsayılan 1 oda)
        int roomCount = criteria.getRoomCount() != null && criteria.getRoomCount() > 0
                ? criteria.getRoomCount() : 1;

        int baseAdultCount = criteria.getAdultCount() != null ? criteria.getAdultCount() : 1;

        List<TourVisioHotelSearchRequest.RoomCriteria> rooms = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) {
            rooms.add(TourVisioHotelSearchRequest.RoomCriteria.builder()
                    .adult(baseAdultCount)
                    .childAges(i == 0 ? mappedChildAges : new ArrayList<>())
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
