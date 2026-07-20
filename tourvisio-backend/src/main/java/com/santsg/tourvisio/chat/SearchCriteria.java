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
    private Integer infantCount = 0;
    private List<Integer> infantAges = new ArrayList<>();
    private String nationality = "TR";
    private Integer roomCount = 1;

    /**
     * Yaşa göre bebek/çocuk/yetişkin sınıflandırması değiştiğinde (ör.
     * kullanıcı "2 bebek" dedi ama yaşları 2 ve 3 çıktı → biri gerçekte
     * çocuk) burada oluşan açıklama metni tutulur; tek seferlik bilgi
     * amaçlıdır, cevaba eklenip tüketilir.
     */
    private transient String reclassificationNote;

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
        // childAges dolu geldiğinde çocuk sayısı ondan türetilir (tutarlılık için).
        // childCount, sadece pozitif bir değer geldiğinde uygulanır — LLM tabanlı
        // çıkarım, çocuktan hiç bahsetmeyen mesajlarda da "childCount": 0 döndürebiliyor;
        // bunu uygulamak önceki turda öğrenilmiş gerçek çocuk sayısını sıfırlardı.
        if (incoming.getChildAges() != null && !incoming.getChildAges().isEmpty()) {
            this.childAges = incoming.getChildAges();
            this.childCount = incoming.getChildAges().size();
        } else if (incoming.getChildCount() != null && incoming.getChildCount() > 0) {
            this.childCount = incoming.getChildCount();
        }
        // Bebek — aynı mantık çocukla birebir aynı.
        if (incoming.getInfantAges() != null && !incoming.getInfantAges().isEmpty()) {
            this.infantAges = incoming.getInfantAges();
            this.infantCount = incoming.getInfantAges().size();
        } else if (incoming.getInfantCount() != null && incoming.getInfantCount() > 0) {
            this.infantCount = incoming.getInfantCount();
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

        reconcileAgeBuckets();
    }

    /**
     * Bebek (0-2 yaş), çocuk (3-12 yaş) ve yetişkin (12 yaş üstü) sınırlarına
     * göre, o ana kadar toplanmış TÜM yaşları (hem "çocuk" hem "bebek" olarak
     * bildirilmiş olsun fark etmez) gerçek yaşlarına göre yeniden dağıtır.
     *
     * <p>Kullanıcı "2 bebek" deyip yaşlarını "2 ve 3" olarak verirse, ya da
     * "2 çocuk" deyip yaşlarını "8 ve 13" olarak verirse, burada gerçek yaşa
     * göre doğru kovaya (bebek/çocuk/yetişkin) taşınır ve neden taşındığını
     * açıklayan bir not ({@link #reclassificationNote}) üretilir.</p>
     */
    private void reconcileAgeBuckets() {
        List<Integer> allAges = new ArrayList<>();
        if (this.infantAges != null) allAges.addAll(this.infantAges);
        if (this.childAges != null) allAges.addAll(this.childAges);
        if (allAges.isEmpty()) {
            return;
        }

        // Yaşı henüz bilinmeyen (sadece sayı söylenmiş, yaş sorusu hâlâ bekleniyor
        // olabilecek) bebek/çocuk sayısı — bunlar aşağıda yaşa göre yeniden
        // dağıtılan listelere dahil değildir, o yüzden sayı hesaplanırken korunmalı.
        int prevInfantAgesKnown = this.infantAges != null ? this.infantAges.size() : 0;
        int prevChildAgesKnown = this.childAges != null ? this.childAges.size() : 0;
        int infantsWithoutKnownAge = Math.max(0,
                (this.infantCount != null ? this.infantCount : 0) - prevInfantAgesKnown);
        int childrenWithoutKnownAge = Math.max(0,
                (this.childCount != null ? this.childCount : 0) - prevChildAgesKnown);

        List<Integer> newInfantAges = new ArrayList<>();
        List<Integer> newChildAges = new ArrayList<>();
        int movedToAdult = 0;
        for (Integer age : allAges) {
            if (age == null) continue;
            if (age <= 2) newInfantAges.add(age);
            else if (age <= 12) newChildAges.add(age);
            else movedToAdult++;
        }

        boolean changed = newInfantAges.size() != prevInfantAgesKnown
                || newChildAges.size() != prevChildAgesKnown
                || movedToAdult > 0;

        if (changed) {
            List<String> parts = new ArrayList<>();
            if (!newInfantAges.isEmpty()) parts.add(newInfantAges.size() + " bebek (0-2 yaş)");
            if (!newChildAges.isEmpty()) parts.add(newChildAges.size() + " çocuk (3-12 yaş)");
            if (movedToAdult > 0) parts.add(movedToAdult + " yetişkin (12 yaş üstü, yaşa göre yetişkin sayıldı)");
            this.reclassificationNote = "Belirttiğiniz yaşlara göre: " + String.join(", ", parts) + ".";
        }

        this.infantAges = newInfantAges;
        this.infantCount = newInfantAges.size() + infantsWithoutKnownAge;
        this.childAges = newChildAges;
        this.childCount = newChildAges.size() + childrenWithoutKnownAge;
        if (movedToAdult > 0) {
            this.adultCount = (this.adultCount != null ? this.adultCount : 0) + movedToAdult;
        }
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
        req.setInfantCount(infantCount);
        req.setRoomCount(roomCount);
        req.setNationality(nationality);
        return req;
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
