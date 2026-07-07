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
 * <p>Hem otel hem uçak alanlarını tek sınıfta taşır; hangi alanın
 * dolu olduğu {@code searchType} ile belirlenir.</p>
 *
 * <p>Şimdilik bellek içinde tutulur ({@link ChatSessionStore}).
 * İlerde {@code ChatSession} JPA entity'siyle database'e taşınabilir.</p>
 */
@Data
@NoArgsConstructor
public class SearchCriteria {

    // ── Ortak ────────────────────────────────────────────────────────────────
    /** HOTEL_SEARCH | FLIGHT_SEARCH */
    private String searchType;

    /** TL, EUR, USD … */
    private String currency;

    // ── Otel ─────────────────────────────────────────────────────────────────
    private String locationOrHotelName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer adultCount;
    private Integer childCount;
    private List<Integer> childAges = new ArrayList<>();
    private String nationality;
    private Integer roomCount;

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
     * Yeni gelen kriterlerdeki {@code null} olmayan alanları {@code this} üzerine yazar.
     * Bu sayede kullanıcının ikinci mesajı birinci mesajdaki bilgileri ezmez,
     * sadece eksik alanları tamamlar.
     */
    public void mergeWith(SearchCriteria incoming) {
        if (incoming == null) return;

        if (incoming.getSearchType()          != null) this.searchType          = incoming.getSearchType();
        if (incoming.getCurrency()             != null) this.currency             = incoming.getCurrency();

        // Otel
        if (incoming.getLocationOrHotelName() != null) this.locationOrHotelName = incoming.getLocationOrHotelName();
        if (incoming.getCheckInDate()         != null) this.checkInDate         = incoming.getCheckInDate();
        if (incoming.getCheckOutDate()        != null) this.checkOutDate        = incoming.getCheckOutDate();
        if (incoming.getAdultCount()          != null) this.adultCount          = incoming.getAdultCount();
        if (incoming.getChildCount()          != null) this.childCount          = incoming.getChildCount();
        if (!incoming.getChildAges().isEmpty())        this.childAges           = incoming.getChildAges();
        if (incoming.getNationality()         != null) this.nationality         = incoming.getNationality();
        if (incoming.getRoomCount()           != null) this.roomCount           = incoming.getRoomCount();

        // Uçak
        if (incoming.getDepartureLocation()   != null) this.departureLocation   = incoming.getDepartureLocation();
        if (incoming.getArrivalLocation()     != null) this.arrivalLocation     = incoming.getArrivalLocation();
        if (incoming.getDepartureDate()       != null) this.departureDate       = incoming.getDepartureDate();
        if (incoming.getReturnDate()          != null) this.returnDate          = incoming.getReturnDate();
        if (incoming.getPassengerCount()      != null) this.passengerCount      = incoming.getPassengerCount();
        if (incoming.getTripType()            != null) this.tripType            = incoming.getTripType();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DTO builders  (daha sonra search service çağrısı için)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Tüm otel alanları doluysa {@link HotelSearchRequest} döner; aksi hâlde {@code null}.
     * TODO: HotelSearchService çağrısına geçildiğinde buradaki null kontrolü kaldırılabilir.
     */
    public HotelSearchRequest toHotelSearchRequest() {
        if (locationOrHotelName == null || checkInDate == null
                || checkOutDate == null || adultCount == null || currency == null) {
            return null;
        }
        HotelSearchRequest req = new HotelSearchRequest();
        req.setLocation(locationOrHotelName);
        req.setCheckInDate(checkInDate);
        req.setCheckOutDate(checkOutDate);
        req.setAdultsCount(adultCount);
        req.setCurrency(currency);
        return req;
    }

    /**
     * Tüm uçak alanları doluysa {@link FlightSearchRequest} döner; aksi hâlde {@code null}.
     * TODO: FlightSearchService çağrısına geçildiğinde buradaki null kontrolü kaldırılabilir.
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
        return req;
    }
}
