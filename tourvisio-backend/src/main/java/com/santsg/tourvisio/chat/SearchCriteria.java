package com.santsg.tourvisio.chat;

import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.FlightSearchRequest;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bir oturum boyunca biriktirilen arama kriterleri.
 *
 * <p>
 * Hem otel hem uçak alanlarını tek sınıfta taşır; hangi alanın
 * dolu olduğu {@code searchType} ile belirlenir.
 * </p>
 *
 * <p>
 * Şimdilik bellek içinde tutulur ({@link ChatSessionStore}).
 * İleride {@code ChatSession} JPA entity'siyle database'e taşınabilir.
 * </p>
 */
@Data
@NoArgsConstructor
public class SearchCriteria {

    // ── Ortak ────────────────────────────────────────────────────────────────
    /** HOTEL_SEARCH | FLIGHT_SEARCH */
    private String searchType;

    /** TL, EUR, USD … */
    private String currency;

    private String preferredLanguage;
    private String country;

    // ── Otel ─────────────────────────────────────────────────────────────────
    private String locationOrHotelName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer adultCount;
    private Integer childCount = 0;
    private List<Integer> childAges = new ArrayList<>();
    private String nationality = "TR";
    private Integer roomCount = 1;

    // ── Uçak ─────────────────────────────────────────────────────────────────
    private String departureLocation;
    private String arrivalLocation;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private Integer passengerCount;
    /** ONE_WAY | ROUND_TRIP */
    private String tripType;

    // ──────────────────────────────────────────────────────────────────────────
    // Merge helper
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Yeni gelen kriterlerdeki {@code null} olmayan alanları {@code this} üzerine
     * yazar.
     * Bu sayede kullanıcının ikinci mesajı birinci mesajdaki bilgileri ezmez,
     * sadece eksik alanları tamamlar.
     */
    public void mergeWith(SearchCriteria incoming) {
        if (incoming == null)
            return;

        if (incoming.getSearchType() != null)
            this.searchType = incoming.getSearchType();
        if (incoming.getCurrency() != null)
            this.currency = incoming.getCurrency();
        if (incoming.getPreferredLanguage() != null)
            this.preferredLanguage = incoming.getPreferredLanguage();
        if (incoming.getCountry() != null)
            this.country = incoming.getCountry();

        // Otel
        if (incoming.getLocationOrHotelName() != null)
            this.locationOrHotelName = incoming.getLocationOrHotelName();
        if (incoming.getCheckInDate() != null)
            this.checkInDate = incoming.getCheckInDate();
        if (incoming.getCheckOutDate() != null)
            this.checkOutDate = incoming.getCheckOutDate();
        if (incoming.getAdultCount() != null)
            this.adultCount = incoming.getAdultCount();
        if (incoming.getChildCount() != null) {
            this.childCount = incoming.getChildCount();
        }
        if (incoming.getChildAges() != null && !incoming.getChildAges().isEmpty()) {
            this.childAges = incoming.getChildAges();
        }
        if (incoming.getNationality() != null)
            this.nationality = incoming.getNationality();
        if (incoming.getRoomCount() != null)
            this.roomCount = incoming.getRoomCount();

        // Uçak
        if (incoming.getDepartureLocation() != null)
            this.departureLocation = incoming.getDepartureLocation();
        if (incoming.getArrivalLocation() != null)
            this.arrivalLocation = incoming.getArrivalLocation();
        if (incoming.getDepartureDate() != null)
            this.departureDate = incoming.getDepartureDate();
        if (incoming.getReturnDate() != null)
            this.returnDate = incoming.getReturnDate();
        if (incoming.getPassengerCount() != null)
            this.passengerCount = incoming.getPassengerCount();
        if (incoming.getTripType() != null)
            this.tripType = incoming.getTripType();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DTO builders (daha sonra search service çağrısı için)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Tüm otel alanları doluysa {@link HotelSearchRequest} döner; aksi hâlde
     * {@code null}.
     */
    public HotelSearchRequest toHotelSearchRequest() {
        if (locationOrHotelName == null || checkInDate == null
                || checkOutDate == null || adultCount == null || currency == null) {
            return null;
        }
        HotelSearchRequest req = new HotelSearchRequest();
        req.setLocationOrHotelName(locationOrHotelName);
        req.setCheckInDate(checkInDate);
        req.setCheckOutDate(checkOutDate);
        req.setAdultCount(adultCount);
        req.setChildCount(childCount);
        req.setCurrency(currency);
        req.setChildAges(childAges);
        req.setRoomCount(roomCount);
        req.setNationality(nationality);
        return req;
    }

    /**
     * Backend 1'in yeni detaylı HotelSearchRequest DTO'sunu oluşturur.
     */
    public com.santsg.tourvisio.dto.hotel.HotelSearchRequest toHotelSearchRequestDto() {
        if (locationOrHotelName == null || checkInDate == null
                || checkOutDate == null || adultCount == null || currency == null) {
            return null;
        }
        return com.santsg.tourvisio.dto.hotel.HotelSearchRequest.builder()
                .locationOrHotelName(locationOrHotelName)
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .adultCount(adultCount)
                .childCount(childCount != null ? childCount : 0)
                .childAges(childAges != null ? childAges : new ArrayList<>())
                .nationality(nationality != null ? nationality : "TR")
                .currency(currency)
                .roomCount(roomCount != null ? roomCount : 1)
                .build();
    }

    /**
     * Tüm uçak alanları doluysa {@link FlightSearchRequest} döner; aksi hâlde
     * {@code null}.
     */
    public FlightSearchRequest toFlightSearchRequest() {
        if (departureLocation == null || arrivalLocation == null
                || departureDate == null || passengerCount == null
                || tripType == null || currency == null) {
            return null;
        }
        FlightSearchRequest req = new FlightSearchRequest();
        req.setDepartureLocation(departureLocation);
        req.setArrivalLocation(arrivalLocation);
        req.setDepartureDate(departureDate);
        req.setPassengerCount(passengerCount);
        req.setTripType(tripType);
        req.setCurrency(currency);
        // Set new fields
        req.setDepartureAirport(departureLocation);
        req.setArrivalAirport(arrivalLocation);
        req.setReturnDate(returnDate);
        return req;
    }
}
