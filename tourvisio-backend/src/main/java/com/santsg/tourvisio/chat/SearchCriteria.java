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
// LLM bazen şemada olmayan fazladan alanlar ("hasChildren" gibi) üretebiliyor;
// bilinmeyen alan yüzünden TÜM (doğru) çıkarımın çöpe atılıp zayıf regex
// yedeğine düşülmesini engellemek için bilinmeyen alanları yok sayıyoruz.
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
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
    private Boolean flexibleDates = false;
    private Integer stayNights;
    private Boolean assumedGuestCount = false;
    private Integer adultCount;
    private Integer childCount = 0;
    private List<Integer> childAges = new ArrayList<>();
    private Integer infantCount = 0;
    private List<Integer> infantAges = new ArrayList<>();
    private Integer incrementalChildCount;
    private Integer incrementalInfantCount;
    private String nationality = "TR";
    private Integer roomCount = 1;

    private Double maxPrice;
    private Double minPrice;
    private Integer minStars;

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
    private Boolean assumedPassengerCount = false;
    /** ONE_WAY | ROUND_TRIP */
    private String tripType;
    private Boolean assumedTripType = false;

    // ──────────────────────────────────────────────────────────────────────────
    // Copy helper
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Bu kriterlerin bağımsız bir kopyasını döner — merge/validasyon denemesi
     * başarısız olursa oturumu bu kopyaya geri döndürebilmek (rollback) için
     * kullanılır; aksi hâlde reddedilen bir güncelleme bile kalıcı olarak
     * yazılmış olurdu (bkz. {@code ChatOrchestrationService}).
     */
    public SearchCriteria copy() {
        SearchCriteria c = new SearchCriteria();
        c.searchType = this.searchType;
        c.currency = this.currency;
        c.preferredLanguage = this.preferredLanguage;
        c.country = this.country;
        c.locationOrHotelName = this.locationOrHotelName;
        c.checkInDate = this.checkInDate;
        c.checkOutDate = this.checkOutDate;
        c.flexibleDates = this.flexibleDates;
        c.stayNights = this.stayNights;
        c.assumedGuestCount = this.assumedGuestCount;
        c.adultCount = this.adultCount;
        c.childCount = this.childCount;
        c.childAges = this.childAges != null ? new ArrayList<>(this.childAges) : new ArrayList<>();
        c.infantCount = this.infantCount;
        c.infantAges = this.infantAges != null ? new ArrayList<>(this.infantAges) : new ArrayList<>();
        c.nationality = this.nationality;
        c.roomCount = this.roomCount;
        c.departureLocation = this.departureLocation;
        c.arrivalLocation = this.arrivalLocation;
        c.departureDate = this.departureDate;
        c.returnDate = this.returnDate;
        c.passengerCount = this.passengerCount;
        c.assumedPassengerCount = this.assumedPassengerCount;
        c.tripType = this.tripType;
        c.assumedTripType = this.assumedTripType;
        c.maxPrice = this.maxPrice;
        c.minPrice = this.minPrice;
        c.minStars = this.minStars;
        return c;
    }


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

        // Eğer kullanıcı kesin bir giriş/çıkış tarihi belirttiyse, esnek tarih modu otomatik kapanır!
        if (incoming.getCheckInDate() != null || incoming.getCheckOutDate() != null || incoming.getDepartureDate() != null) {
            this.flexibleDates = false;
        } else if (incoming.getFlexibleDates() != null) {
            this.flexibleDates = incoming.getFlexibleDates();
        }

        if (incoming.getStayNights() != null)
            this.stayNights = incoming.getStayNights();
        if (incoming.getCheckInDate() != null)
            this.checkInDate = incoming.getCheckInDate();
        if (incoming.getCheckOutDate() != null)
            this.checkOutDate = incoming.getCheckOutDate();

        if (incoming.getAdultCount() != null) {
            this.adultCount = incoming.getAdultCount();
            this.assumedGuestCount = false; // Kullanıcı kendisi belirtti!
        }

        // childAges dolu geldiğinde çocuk sayısı ondan türetilir (tutarlılık için).
        // childCount pozitif bir değer geldiğinde her zaman uygulanır. Açık bir 0
        // ise de, SADECE bu mesaj gerçekten misafir sayısıyla ilgiliyse (aynı anda
        // adultCount de gelmişse) uygulanır — bu, yapay zekanın "vazgeçtim, sadece
        // 2 yetişkin olsun" gibi bir sıfırlama niyetini (bkz. ExtractionAgent
        // prompt'u) güvenilir şekilde iletebildiği tek durumdur. Bunun dışında
        // (misafirle hiç ilgisi olmayan bir mesajda modelin alışkanlıkla "childCount":
        // 0 döndürmesi ihtimaline karşı) 0 değeri yok sayılır ki önceki turda
        // öğrenilmiş gerçek çocuk sayısı yanlışlıkla sıfırlanmasın.
        // Artımlı çocuk ekleme (ör. "1 çocuk daha var", "1 tane daha çocuk ekle")
        if (incoming.getIncrementalChildCount() != null && incoming.getIncrementalChildCount() > 0) {
            int currentCount = this.childCount != null ? this.childCount : 0;
            this.childCount = currentCount + incoming.getIncrementalChildCount();
        } else if (incoming.getChildAges() != null && !incoming.getChildAges().isEmpty()) {
            if (this.childAges == null || this.childAges.isEmpty() || incoming.getChildAges().size() >= (this.childCount != null ? this.childCount : 0)) {
                this.childAges = new ArrayList<>(incoming.getChildAges());
            } else {
                List<Integer> mergedAges = new ArrayList<>(this.childAges);
                for (Integer age : incoming.getChildAges()) {
                    if (mergedAges.size() < (this.childCount != null ? this.childCount : 99)) {
                        mergedAges.add(age);
                    }
                }
                this.childAges = mergedAges;
            }
            if (this.childCount == null || this.childAges.size() > this.childCount) {
                this.childCount = this.childAges.size();
            }
        } else if (incoming.getChildCount() != null && incoming.getChildCount() > 0) {
            this.childCount = incoming.getChildCount();
        } else if (incoming.getChildCount() != null && incoming.getChildCount() == 0
                && incoming.getAdultCount() != null) {
            this.childCount = 0;
            this.childAges = new ArrayList<>();
        }

        // Artımlı bebek ekleme (ör. "1 bebek daha var", "1 tane daha bebek ekle")
        if (incoming.getIncrementalInfantCount() != null && incoming.getIncrementalInfantCount() > 0) {
            int currentCount = this.infantCount != null ? this.infantCount : 0;
            this.infantCount = currentCount + incoming.getIncrementalInfantCount();
        } else if (incoming.getInfantAges() != null && !incoming.getInfantAges().isEmpty()) {
            if (this.infantAges == null || this.infantAges.isEmpty() || incoming.getInfantAges().size() >= (this.infantCount != null ? this.infantCount : 0)) {
                this.infantAges = new ArrayList<>(incoming.getInfantAges());
            } else {
                List<Integer> mergedAges = new ArrayList<>(this.infantAges);
                for (Integer age : incoming.getInfantAges()) {
                    if (mergedAges.size() < (this.infantCount != null ? this.infantCount : 99)) {
                        mergedAges.add(age);
                    }
                }
                this.infantAges = mergedAges;
            }
            if (this.infantCount == null || this.infantAges.size() > this.infantCount) {
                this.infantCount = this.infantAges.size();
            }
        } else if (incoming.getInfantCount() != null && incoming.getInfantCount() > 0) {
            this.infantCount = incoming.getInfantCount();
        } else if (incoming.getInfantCount() != null && incoming.getInfantCount() == 0
                && incoming.getAdultCount() != null) {
            this.infantCount = 0;
            this.infantAges = new ArrayList<>();
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
        if (incoming.getReturnDate() != null) {
            this.returnDate = incoming.getReturnDate();
            // Kullanıcı dönüş tarihi belirttiğinde yolculuk tipi otomatik GİDİŞ-DÖNÜŞ olur!
            this.tripType = "ROUND_TRIP";
            this.assumedTripType = false;
        }
        if (incoming.getPassengerCount() != null) {
            this.passengerCount = incoming.getPassengerCount();
            this.assumedPassengerCount = false;
        }
        if (incoming.getTripType() != null) {
            this.tripType = incoming.getTripType();
            this.assumedTripType = false;
        }
        if (incoming.getMaxPrice() != null)
            this.maxPrice = incoming.getMaxPrice();
        if (incoming.getMinPrice() != null)
            this.minPrice = incoming.getMinPrice();
        if (incoming.getMinStars() != null)
            this.minStars = incoming.getMinStars();


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

        // Reconcile çalışmadan öNCE mülk değerlerini kaydet — yaş dağıtımının
        // doğru "eksik yaş" hesabı yapabilmesi için bunlara ihtiyaç var.
        int origInfantCount = (this.infantCount != null ? this.infantCount : 0);
        int origChildCount  = (this.childCount  != null ? this.childCount  : 0);
        int prevInfantAgesKnown = this.infantAges != null ? this.infantAges.size() : 0;
        int prevChildAgesKnown  = this.childAges  != null ? this.childAges.size()  : 0;
        // Toplam sağlanan yaş = childAges + infantAges (ekstrakör bazen ikisini de childAges'e yazar)
        int totalAgesProvided = prevInfantAgesKnown + prevChildAgesKnown;

        List<Integer> newInfantAges = new ArrayList<>();
        List<Integer> newChildAges  = new ArrayList<>();
        int movedToAdult = 0;
        for (Integer age : allAges) {
            if (age == null) continue;
            if (age <= 1)  newInfantAges.add(age);
            else if (age <= 12) newChildAges.add(age);
            else movedToAdult++;
        }

        boolean changed = newInfantAges.size() != prevInfantAgesKnown
                || newChildAges.size() != prevChildAgesKnown
                || movedToAdult > 0;

        if (changed) {
            List<String> parts = new ArrayList<>();
            if (!newInfantAges.isEmpty()) parts.add(newInfantAges.size() + " bebek (0-1 yaş)");
            if (!newChildAges.isEmpty())  parts.add(newChildAges.size()  + " çocuk (2-12 yaş)");
            if (movedToAdult > 0)        parts.add(movedToAdult + " yetişkin (12 yaş üstü, yaşa göre yetişkin sayıldı)");
            this.reclassificationNote = "Belirttiğiniz yaşlara göre: " + String.join(", ", parts) + ".";
        }

        // "Henüz yaşı bilinmeyen misafir" sayısı:
        // Toplam beklenen çocuk/bebek sayısından sağlanan yaş sayısı çıkarılır.
        // Eğer tüm misafirlerin yaşı sağlandıysa (sağlanan yaş sayısı >= beklenen toplam),
        // bilinmeyen misafir sayısı her iki kova için de kesin olarak 0'dır.
        int expectedTotalNonAdults = origChildCount + origInfantCount;
        int unknownInfants = 0;
        int unknownChildren = 0;

        if (allAges.size() < expectedTotalNonAdults) {
            int notYetProvided = expectedTotalNonAdults - allAges.size();
            unknownInfants  = Math.max(0, origInfantCount - newInfantAges.size());
            unknownChildren = Math.max(0, origChildCount  - newChildAges.size());
            int totalUnknowns = unknownInfants + unknownChildren;
            if (totalUnknowns > notYetProvided && totalUnknowns > 0) {
                unknownInfants  = unknownInfants  * notYetProvided / totalUnknowns;
                unknownChildren = notYetProvided  - unknownInfants;
            }
        }

        this.infantAges  = newInfantAges;
        this.infantCount = newInfantAges.size() + unknownInfants;
        this.childAges   = newChildAges;
        this.childCount  = newChildAges.size()  + unknownChildren;
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
                || checkOutDate == null || adultCount == null || currency == null
                || roomCount == null
                || (childCount != null && childCount > 0 && (childAges == null || childAges.isEmpty() || childAges.size() != childCount))
                || (infantCount != null && infantCount > 0 && (infantAges == null || infantAges.isEmpty() || infantAges.size() != infantCount))) {
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
                || tripType == null) {
            return null;
        }
        // Currency belirtilmemişse varsayılan TRY kullan
        String effectiveCurrency = (currency != null && !currency.isBlank()) ? currency : "TRY";
        FlightSearchRequest req = new FlightSearchRequest();
        req.setDepartureLocation(departureLocation);
        req.setArrivalLocation(arrivalLocation);
        req.setDepartureDate(departureDate);
        // passengerCount tarihsel olarak "yetişkin sayısı" gibi kullanılıyor; çocuk/bebek
        // ayrı yolcu tipleriyle (2=Child, 3=Infant) gönderilir. adultCount doluysa onu
        // tercih ediyoruz ki "2 yetişkin 1 çocuk" derken çocuk yetişkin koltuğu sayılmasın.
        req.setPassengerCount(adultCount != null && adultCount > 0 ? adultCount : passengerCount);
        req.setTripType(tripType);
        req.setCurrency(effectiveCurrency);
        // Set new fields
        req.setDepartureAirport(departureLocation);
        req.setArrivalAirport(arrivalLocation);
        req.setReturnDate(returnDate);
        req.setChildCount(childCount);
        req.setChildAges(childAges);
        req.setInfantCount(infantCount);
        req.setRoomCount(roomCount);
        return req;
    }
}
