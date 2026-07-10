package com.santsg.tourvisio.chat;

import com.santsg.tourvisio.client.AIProviderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kullanıcı mesajından {@link SearchCriteria} alanlarını çıkaran servis.
 *
 * <p>
 * Kural tabanlı (rule-based) bir parse yaklaşımı kullanır.
 * Türkçe doğal dil ifadelerini tanır:
 * <ul>
 * <li>Şehir adları</li>
 * <li>"15 Temmuz", "20 temmuz çıkış", "5 gece" gibi tarih ifadeleri</li>
 * <li>"2 yetişkin", "1 çocuk" gibi kişi sayıları</li>
 * <li>TL, EUR, USD gibi para birimleri</li>
 * <li>Kalkış/varış noktaları, tek yön/gidiş-dönüş bilgisi</li>
 * </ul>
 * </p>
 *
 * <p>
 * <strong>Genişletme:</strong> İleride bu sınıfı bir NLP/LLM katmanıyla
 * değiştirmek için arayüz çıkarılabilir.
 * </p>
 */
@Service
public class SearchCriteriaExtractor {

    private static final Logger log = LoggerFactory.getLogger(SearchCriteriaExtractor.class);
    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final int CURRENT_YEAR = LocalDate.now().getYear();

    private final AIProviderClient aiProviderClient;
    private final ObjectMapper objectMapper;

    public SearchCriteriaExtractor(AIProviderClient aiProviderClient) {
        this.aiProviderClient = aiProviderClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── Ay adları ──────────────────────────────────────────────────────────
    private static final Map<String, Integer> MONTHS_BY_NAME = Map.ofEntries(
            Map.entry("ocak", 1), Map.entry("january", 1),
            Map.entry("şubat", 2), Map.entry("february", 2),
            Map.entry("mart", 3), Map.entry("march", 3),
            Map.entry("nisan", 4), Map.entry("april", 4),
            Map.entry("mayıs", 5), Map.entry("may", 5),
            Map.entry("haziran", 6), Map.entry("june", 6),
            Map.entry("temmuz", 7), Map.entry("july", 7),
            Map.entry("ağustos", 8), Map.entry("august", 8),
            Map.entry("eylül", 9), Map.entry("september", 9),
            Map.entry("ekim", 10), Map.entry("october", 10),
            Map.entry("kasım", 11), Map.entry("november", 11),
            Map.entry("aralık", 12), Map.entry("december", 12));

    // ── Şehirler ──────────────────────────────────────────────────────────────
    private static final List<String> HOTEL_CITIES = List.of(
            "antalya", "istanbul", "izmir", "ankara", "bodrum", "marmaris",
            "fethiye", "alanya", "kapadokya", "bursa", "trabzon", "erzurum",
            "kemer", "side", "belek", "paris", "londra", "roma", "barselona",
            "berlin", "amsterdam", "dubai", "new york", "prag", "viyana");

    private static final List<String> FLIGHT_CITIES = List.of(
            "istanbul", "ankara", "izmir", "antalya", "bursa", "trabzon",
            "erzurum", "kayseri", "adana", "diyarbakır", "gaziantep", "konya",
            "paris", "londra", "berlin", "amsterdam", "roma", "barselona",
            "dubai", "new york", "prag", "viyana", "münih", "zurich");

    // ── Para birimi ───────────────────────────────────────────────────────────
    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
            "\\b(tl|try|türk lirası|turk lirasi|lira|eur|euro|usd|dolar|gbp|sterlin)\\b");

    private static final Pattern NUMERIC_DATE_PATTERN = Pattern.compile(
            "\\b(?:(\\d{4})[-/.](0[1-9]|1[0-2])[-/.](0[1-9]|[12]\\d|3[01])|(0[1-9]|[12]\\d|3[01])[-/.](0[1-9]|1[0-2])[-/.](\\d{4}))\\b");

    // ── Sayı + kişi ifadeleri ─────────────────────────────────────────────────
    private static final Pattern ADULT_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:yetişkin|yetiskin|adult|kişi|kisi)");
    private static final Pattern CHILD_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:çocuk|cocuk|child|kids)");
    private static final Pattern PASSENGER_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:yolcu|kişi|kisi|passenger|kişilik|kisilik)");

    // ── Gece sayısı ───────────────────────────────────────────────────────────
    private static final Pattern NIGHT_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:gece|night)");

    // ── Tarih: "15 Temmuz", "15 temmuz girişli", "20 temmuz çıkış" ──────────
    private static final Pattern DATE_WITH_LABEL_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s+(" + String.join("|", MONTHS_BY_NAME.keySet()) + ")"
                    + "(?:\\s+\\d{4})?" // opsiyonel yıl
                    + "(?:\\s*(giriş|giris|checkin|başlangıç|baslangic"
                    + "|çıkış|cikis|checkout|bitiş|bitis))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ── Gidiş tarihi için "X tarihinde", "X'de git" ──────────────────────────
    private static final Pattern DEPARTURE_DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s+(" + String.join("|", MONTHS_BY_NAME.keySet()) + ")"
                    + "(?:\\s+\\d{4})?"
                    + "(?:\\s*(?:gidiş|gidis|kalkış|kalkis|hareket))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ── Kalkış: "İstanbul'dan", "İstanbul dan" ───────────────────────────────
    private static final Pattern DEPARTURE_CITY_PATTERN = Pattern.compile(
            "\\b(\\w+)(?:'?(?:dan|den|tan|ten))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ── Varış: "Antalya'ya", "Antalya ya" ────────────────────────────────────
    private static final Pattern ARRIVAL_CITY_PATTERN = Pattern.compile(
            "\\b(\\w+)(?:'?(?:ya|ye|a|e))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tek bir kullanıcı mesajından çıkarılabilen alanları {@link SearchCriteria}
     * olarak döner. Çıkarılamayan alanlar {@code null} kalır (merge için).
     *
     * @param message Ham kullanıcı mesajı
     * @param intent  IntentDetectionService'in ürettiği intent (HOTEL_SEARCH /
     *                FLIGHT_SEARCH)
     */
    public SearchCriteria extract(String message, String intent) {
        if (message == null || message.isBlank())
            return new SearchCriteria();

        // AI ile parametre çıkarma (API key tanımlıysa)
        try {
            String schemaDescription = "HOTEL_SEARCH".equals(intent)
                    ? """
                            {
                              "locationOrHotelName": "şehir veya otel adı (ör. Antalya)",
                              "checkInDate": "giriş tarihi YYYY-MM-DD formatında (ör. 2026-07-15). Bugünün tarihi 2026-07-08'dir. Eğer mesajda sadece gün/ay varsa (ör. 15 Temmuz) yılı 2026 olarak al.",
                              "checkOutDate": "çıkış tarihi YYYY-MM-DD formatında. Eğer sadece gece sayısı verilmişse (ör. 5 gece), giriş tarihine bu sayıyı ekleyerek hesapla.",
                              "adultCount": yetişkin sayısı tamsayı,
                              "childCount": çocuk sayısı tamsayı,
                              "childAges": çocuk yaşları dizisi (tamsayılar),
                              "currency": para birimi (TRY, EUR, USD, GBP),
                              "roomCount": oda sayısı tamsayı,
                              "nationality": milliyet kodu (ör. TR)
                            }
                            """
                    : """
                            {
                              "departureLocation": "kalkış yeri (ör. İstanbul)",
                              "arrivalLocation": "varış yeri (ör. Antalya)",
                              "departureDate": "gidiş tarihi YYYY-MM-DD formatında (ör. 2026-07-20). Bugünün tarihi 2026-07-08'dir. Eğer mesajda sadece gün/ay varsa yılı 2026 olarak al.",
                              "returnDate": "dönüş tarihi YYYY-MM-DD formatında.",
                              "passengerCount": yolcu sayısı tamsayı,
                              "tripType": "ONE_WAY" veya "ROUND_TRIP",
                              "currency": para birimi (TRY, EUR, USD, GBP)
                            }
                            """;

            String prompt = """
                    Kullanıcının şu mesajından seyahat kriterlerini çıkar ve SADECE saf bir JSON objesi olarak dön. Başka hiçbir açıklama, markdown bloğu (```json gibi) veya metin ekleme.
                    Sadece bulabildiğin alanları doldur, bulamadıklarını JSON'da hiç gösterme veya null bırak.

                    Kullanıcı Mesajı: "%s"
                    Arama Türü: %s
                    Beklenen JSON Şeması:
                    %s

                    Yanıt (Sadece JSON):"""
                    .formatted(message, intent, schemaDescription);

            String response = aiProviderClient.complete(prompt);
            if (response != null && !response.trim().startsWith("[MOCK]")) {
                // Markdown ```json bloğu varsa temizle
                String jsonText = response.trim();
                if (jsonText.startsWith("```")) {
                    jsonText = jsonText.substring(jsonText.indexOf("\n") + 1);
                }
                if (jsonText.endsWith("```")) {
                    jsonText = jsonText.substring(0, jsonText.lastIndexOf("```"));
                }
                jsonText = jsonText.trim();

                SearchCriteria criteria = objectMapper.readValue(jsonText, SearchCriteria.class);
                if (criteria != null) {
                    criteria.setSearchType(intent);
                    log.debug("[Extractor] AI parametre çıkarma başarılı: {}", criteria);
                    return criteria;
                }
            }
        } catch (Exception e) {
            log.warn("[Extractor] AI parametre çıkarma başarısız, rule-based sisteme geçiliyor: {}", e.getMessage());
        }

        String lower = message.toLowerCase(TR);
        SearchCriteria c = new SearchCriteria();
        c.setSearchType(intent);

        // ── Para birimi ───────────────────────────────────────────────────
        c.setCurrency(extractCurrency(lower));

        if ("HOTEL_SEARCH".equals(intent)) {
            extractHotelFields(lower, c);
        } else if ("FLIGHT_SEARCH".equals(intent)) {
            extractFlightFields(lower, c);
        }

        log.debug("[Extractor] intent={} extracted={}", intent, c);
        return c;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hotel extraction
    // ─────────────────────────────────────────────────────────────────────────

    private void extractHotelFields(String lower, SearchCriteria c) {

        // Lokasyon
        for (String city : HOTEL_CITIES) {
            if (lower.contains(city)) {
                c.setLocationOrHotelName(capitalize(city));
                break;
            }
        }

        // Yetişkin
        Matcher am = ADULT_PATTERN.matcher(lower);
        if (am.find())
            c.setAdultCount(Integer.parseInt(am.group(1)));

        // Çocuk
        Matcher cm = CHILD_PATTERN.matcher(lower);
        if (cm.find())
            c.setChildCount(Integer.parseInt(cm.group(1)));

        // Tarihler (giriş & çıkış)
        extractHotelDates(lower, c);
    }

    /**
     * "15 Temmuz girişli 5 gece" veya "15 temmuz giriş 20 temmuz çıkış"
     * gibi ifadelerden checkIn ve checkOut tarihlerini çıkarır.
     */
    private void extractHotelDates(String lower, SearchCriteria c) {
        List<LocalDate> dates = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();

        Matcher m = DATE_WITH_LABEL_PATTERN.matcher(lower);
        while (m.find()) {
            LocalDate d = buildDate(
                    Integer.parseInt(m.group(1)),
                    m.group(2).toLowerCase(TR));
            if (d != null) {
                dates.add(d);
                labels.add(m.group(3));
            }
        }

        if (dates.isEmpty()) {
            List<LocalDate> numericDates = extractNumericDates(lower);
            if (!numericDates.isEmpty()) {
                dates.addAll(numericDates);
                for (int i = 0; i < numericDates.size(); i++) {
                    labels.add(null);
                }
            }
        }

        if (dates.isEmpty()) {
            return;
        }

        boolean hasExplicitLabel = false;
        for (String label : labels) {
            if (label != null && !label.isBlank()) {
                hasExplicitLabel = true;
                break;
            }
        }

        if (hasExplicitLabel) {
            for (int i = 0; i < dates.size(); i++) {
                LocalDate d = dates.get(i);
                String label = labels.get(i);
                if (label != null && (label.contains("çıkış") || label.contains("cikis") || label.contains("checkout") || label.contains("bitiş") || label.contains("bitis"))) {
                    c.setCheckOutDate(d);
                } else {
                    c.setCheckInDate(d);
                }
            }
        } else {
            if (dates.size() >= 2) {
                LocalDate d1 = dates.get(0);
                LocalDate d2 = dates.get(1);
                if (d1.isAfter(d2)) {
                    c.setCheckInDate(d2);
                    c.setCheckOutDate(d1);
                } else {
                    c.setCheckInDate(d1);
                    c.setCheckOutDate(d2);
                }
            } else if (dates.size() == 1) {
                c.setCheckInDate(dates.get(0));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Flight extraction
    // ─────────────────────────────────────────────────────────────────────────

    private void extractFlightFields(String lower, SearchCriteria c) {

        // Yolcu sayısı
        Matcher pm = PASSENGER_PATTERN.matcher(lower);
        if (pm.find())
            c.setPassengerCount(Integer.parseInt(pm.group(1)));

        // Trip type
        if (lower.contains("tek yön") || lower.contains("tek yon")
                || lower.contains("tek-yön") || lower.contains("gidiş sadece")) {
            c.setTripType("ONE_WAY");
        } else if (lower.contains("gidiş dönüş") || lower.contains("gidis donus")
                || lower.contains("gidiş-dönüş") || lower.contains("gidis-donus")
                || lower.contains("gidiş ve dönüş")) {
            c.setTripType("ROUND_TRIP");
        }

        // Kalkış şehri ("İstanbul'dan" → Istanbul)
        Matcher depM = DEPARTURE_CITY_PATTERN.matcher(lower);
        while (depM.find()) {
            String candidate = depM.group(1);
            if (FLIGHT_CITIES.contains(candidate.toLowerCase(TR))) {
                c.setDepartureLocation(capitalize(candidate));
                break;
            }
        }

        // Varış şehri ("Antalya'ya" → Antalya)
        Matcher arrM = ARRIVAL_CITY_PATTERN.matcher(lower);
        while (arrM.find()) {
            String candidate = arrM.group(1);
            String cLower = candidate.toLowerCase(TR);
            if (FLIGHT_CITIES.contains(cLower)
                    && !cLower.equals(
                            c.getDepartureLocation() != null
                                    ? c.getDepartureLocation().toLowerCase(TR)
                                    : "")) {
                c.setArrivalLocation(capitalize(candidate));
                break;
            }
        }

        // Doğrudan şehir adı (suffix olmadan) — kalkış veya varış belirsizse atla
        if (c.getDepartureLocation() == null || c.getArrivalLocation() == null) {
            for (String city : FLIGHT_CITIES) {
                if (lower.contains(city)) {
                    if (c.getDepartureLocation() == null) {
                        c.setDepartureLocation(capitalize(city));
                    } else if (c.getArrivalLocation() == null
                            && !city.equalsIgnoreCase(c.getDepartureLocation())) {
                        c.setArrivalLocation(capitalize(city));
                    }
                }
            }
        }

        
        List<LocalDate> flightDates = extractNumericDates(lower);
        if (flightDates.isEmpty()) {
            Matcher datM = DEPARTURE_DATE_PATTERN.matcher(lower);
            if (datM.find()) {
                LocalDate d = buildDate(Integer.parseInt(datM.group(1)), datM.group(2).toLowerCase(TR));
                if (d != null)
                    flightDates.add(d);
            }
            if ("ROUND_TRIP".equals(c.getTripType())) {
                List<LocalDate> labelDates = extractAllDates(lower);
                if (labelDates.size() >= 2) {
                    flightDates = labelDates;
                }
            }
        }

        if (!flightDates.isEmpty()) {
            if ("ROUND_TRIP".equals(c.getTripType()) && flightDates.size() >= 2) {
                LocalDate d1 = flightDates.get(0);
                LocalDate d2 = flightDates.get(1);
                if (d1.isAfter(d2)) {
                    c.setDepartureDate(d2);
                    c.setReturnDate(d1);
                } else {
                    c.setDepartureDate(d1);
                    c.setReturnDate(d2);
                }
            } else {
                c.setDepartureDate(flightDates.get(0));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String extractCurrency(String lower) {
        Matcher m = CURRENCY_PATTERN.matcher(lower);
        if (!m.find())
            return null;
        return switch (m.group(1).toLowerCase(TR)) {
            case "tl", "try", "türk lirası", "turk lirasi", "lira" -> "TRY";
            case "eur", "euro" -> "EUR";
            case "usd", "dolar" -> "USD";
            case "gbp", "sterlin" -> "GBP";
            default -> m.group(1).toUpperCase();
        };
    }

    private LocalDate buildDate(int day, String monthTr) {
        Integer monthNum = MONTHS_BY_NAME.get(monthTr.toLowerCase(TR));
        if (monthNum == null)
            return null;
        try {
            return LocalDate.of(CURRENT_YEAR, Month.of(monthNum), day);
        } catch (Exception e) {
            log.warn("[Extractor] Geçersiz tarih: {} {}", day, monthTr);
            return null;
        }
    }

    private List<LocalDate> extractAllDates(String lower) {
        Matcher m = DATE_WITH_LABEL_PATTERN.matcher(lower);
        List<LocalDate> dates = new java.util.ArrayList<>();
        while (m.find()) {
            LocalDate d = buildDate(
                    Integer.parseInt(m.group(1)),
                    m.group(2).toLowerCase(TR));
            if (d != null)
                dates.add(d);
        }
        return dates;
    }

    private List<LocalDate> extractNumericDates(String text) {
        List<LocalDate> dates = new java.util.ArrayList<>();
        Matcher m = NUMERIC_DATE_PATTERN.matcher(text);
        while (m.find()) {
            try {
                if (m.group(1) != null) {
                    int year = Integer.parseInt(m.group(1));
                    int month = Integer.parseInt(m.group(2));
                    int day = Integer.parseInt(m.group(3));
                    dates.add(LocalDate.of(year, month, day));
                } else if (m.group(4) != null) {
                    int day = Integer.parseInt(m.group(4));
                    int month = Integer.parseInt(m.group(5));
                    int year = Integer.parseInt(m.group(6));
                    dates.add(LocalDate.of(year, month, day));
                }
            } catch (Exception e) {
                // Ignore invalid date combinations
            }
        }
        return dates;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank())
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(TR);
    }
}
