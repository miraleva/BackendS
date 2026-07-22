package com.santsg.tourvisio.chat;

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
    private static final int CURRENT_YEAR = LocalDate.now().getYear();

    public SearchCriteriaExtractor() {
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
    private static final Pattern INFANT_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:bebek|infant|infants|baby|babies)");
    private static final Pattern PASSENGER_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:yolcu|kişi|kisi|passenger|passengers|person|people|kişilik|kisilik|yetişkin|yetiskin|adult|adults)");

    // ── Gece sayısı ───────────────────────────────────────────────────────────
    private static final Pattern NIGHT_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:gece|night)");

    // ── Tarih: "giriş tarihi 15 Temmuz", "15 temmuz girişli", "20 temmuz çıkış" ──────────
    private static final Pattern DATE_WITH_LABEL_PATTERN = Pattern.compile(
            "(?:(giriş|giris|checkin|başlangıç|baslangic|departure|gidiş|gidis|kalkış|kalkis|hareket|çıkış|cikis|checkout|bitiş|bitis|return|dönüş|donus)\\s+(?:tarihi\\s+)?)?"
                    + "(\\d{1,2})\\s+(" + String.join("|", MONTHS_BY_NAME.keySet()) + ")"
                    + "(?:\\s+\\d{4})?" // opsiyonel yıl
                    + "(?:\\s*(giriş|giris|checkin|başlangıç|baslangic|departure|gidiş|gidis|kalkış|kalkis|hareket|çıkış|cikis|checkout|bitiş|bitis|return|dönüş|donus))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ── İngilizce "ay gün" sırası: "August 1st", "August 1", "Aug 5th girişli" ──
    private static final Pattern MONTH_DAY_WITH_LABEL_PATTERN = Pattern.compile(
            "(?:(giriş|giris|checkin|başlangıç|baslangic|departure|gidiş|gidis|kalkış|kalkis|hareket|çıkış|cikis|checkout|bitiş|bitis|return|dönüş|donus)\\s+(?:tarihi\\s+)?)?"
                    + "(" + String.join("|", MONTHS_BY_NAME.keySet()) + ")\\s+(\\d{1,2})(?:st|nd|rd|th)?"
                    + "(?:\\s+\\d{4})?" // opsiyonel yıl
                    + "(?:\\s*(giriş|giris|checkin|başlangıç|baslangic|departure|gidiş|gidis|kalkış|kalkis|hareket|çıkış|cikis|checkout|bitiş|bitis|return|dönüş|donus))?",
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
    public SearchCriteria extract(String message, String intent, String awaitingField) {
        if (message == null || message.isBlank())
            return new SearchCriteria();

        // Türkçe locale'e uygun şekilde küçük harfe dönüştürüyoruz.
        String lower = message.toLowerCase(Locale.forLanguageTag("tr-TR"));
        SearchCriteria c = new SearchCriteria();
        c.setSearchType(intent);

        // --- NEW LOGIC FOR ITEM 5 ---
        // Mesaj sadece sayılardan oluşuyorsa ve "çocuk yaşları" veya "bebek yaşları"
        // soruluyorsa, bu sayılar yaş kabul edilir. Hangi listeye (childAges/infantAges)
        // konduğu önemli değil — SearchCriteria.reconcileAgeBuckets() gerçek yaşa göre
        // zaten doğru kovaya (bebek/çocuk/yetişkin) yeniden dağıtacak.
        if (awaitingField != null
                && (awaitingField.contains("çocuk yaş") || awaitingField.contains("bebek yaş"))
                && lower.matches("^[\\d\\s,.-]+$")) {
            List<Integer> ages = new java.util.ArrayList<>();
            Matcher m = Pattern.compile("\\d+").matcher(lower);
            while (m.find()) {
                ages.add(Integer.parseInt(m.group()));
            }
            if (!ages.isEmpty()) {
                c.setChildAges(ages);
                return c;
            }
        }
        // -----------------------------

        // ── Para birimi ───────────────────────────────────────────────────
        c.setCurrency(extractCurrency(lower));

        if ("HOTEL_SEARCH".equals(intent)) {
            extractHotelFields(lower, c, awaitingField);
        } else if ("FLIGHT_SEARCH".equals(intent)) {
            extractFlightFields(lower, c, awaitingField);
        }

        log.debug("[Extractor] intent={} extracted={}", intent, c);
        return c;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hotel extraction
    // ─────────────────────────────────────────────────────────────────────────

    private void extractHotelFields(String lower, SearchCriteria c, String awaitingField) {

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

        // Bebek
        Matcher im = INFANT_PATTERN.matcher(lower);
        if (im.find())
            c.setInfantCount(Integer.parseInt(im.group(1)));

        // Tarihler (giriş & çıkış)
        extractHotelDates(lower, c, awaitingField);
    }

    /**
     * "15 Temmuz girişli 5 gece" veya "15 temmuz giriş 20 temmuz çıkış"
     * gibi ifadelerden checkIn ve checkOut tarihlerini çıkarır.
     */
    private void extractHotelDates(String lower, SearchCriteria c, String awaitingField) {
        List<LocalDate> dates = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();

        Matcher m = DATE_WITH_LABEL_PATTERN.matcher(lower);
        while (m.find()) {
            LocalDate d = buildDate(
                    Integer.parseInt(m.group(2)),
                    m.group(3).toLowerCase(Locale.ROOT));
            if (d != null) {
                dates.add(d);
                String label = m.group(1) != null ? m.group(1) : m.group(4);
                labels.add(label);
            }
        }

        if (dates.isEmpty()) {
            // "August 1st" gibi İngilizce "ay gün" sırasını dene
            Matcher mdm = MONTH_DAY_WITH_LABEL_PATTERN.matcher(lower);
            while (mdm.find()) {
                LocalDate d = buildDate(
                        Integer.parseInt(mdm.group(3)),
                        mdm.group(2).toLowerCase(Locale.ROOT));
                if (d != null) {
                    dates.add(d);
                    String label = mdm.group(1) != null ? mdm.group(1) : mdm.group(4);
                    labels.add(label);
                }
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

        // --- NEW LOGIC FOR AWAITING_FIELD Context ---
        if (dates.size() == 1 && awaitingField != null) {
            String lowerAwaiting = awaitingField.toLowerCase(Locale.ROOT);
            if (lowerAwaiting.contains("giriş tarihi") || lowerAwaiting.contains("checkindate")) {
                c.setCheckInDate(dates.get(0));
                return;
            } else if (lowerAwaiting.contains("çıkış tarihi") || lowerAwaiting.contains("checkoutdate")) {
                c.setCheckOutDate(dates.get(0));
                return;
            }
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
                if (label != null && (label.contains("çıkış") || label.contains("cikis") || label.contains("checkout") || label.contains("bitiş") || label.contains("bitis") || label.contains("return") || label.contains("dönüş") || label.contains("donus"))) {
                    c.setCheckOutDate(d);
                } else if (label != null) {
                    c.setCheckInDate(d);
                } else {
                    if (c.getCheckInDate() == null && c.getCheckOutDate() != null) {
                         if (d.isBefore(c.getCheckOutDate())) {
                             c.setCheckInDate(d);
                         }
                    } else if (c.getCheckOutDate() == null && c.getCheckInDate() != null) {
                         if (d.isAfter(c.getCheckInDate())) {
                             c.setCheckOutDate(d);
                         }
                    }
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

    private void extractFlightFields(String lower, SearchCriteria c, String awaitingField) {

        // Yolcu sayısı
        Matcher pm = PASSENGER_PATTERN.matcher(lower);
        if (pm.find())
            c.setPassengerCount(Integer.parseInt(pm.group(1)));

        // Trip type
        if (lower.contains("tek yön") || lower.contains("tek yon")
                || lower.contains("tek-yön") || lower.contains("gidiş sadece")
                || lower.contains("one way") || lower.contains("one-way") || lower.contains("oneway")) {
            c.setTripType("ONE_WAY");
        } else if (lower.contains("gidiş dönüş") || lower.contains("gidis donus")
                || lower.contains("gidiş-dönüş") || lower.contains("gidis-donus")
                || lower.contains("gidiş ve dönüş")
                || lower.contains("round trip") || lower.contains("round-trip") || lower.contains("roundtrip")) {
            c.setTripType("ROUND_TRIP");
        }

        // Kalkış şehri ("İstanbul'dan" → Istanbul)
        Matcher depM = DEPARTURE_CITY_PATTERN.matcher(lower);
        while (depM.find()) {
            String candidate = depM.group(1);
            String matchedCity = findCityMatch(candidate, FLIGHT_CITIES);
            if (matchedCity != null) {
                c.setDepartureLocation(capitalize(matchedCity));
                break;
            }
        }

        // Varış şehri ("Antalya'ya" → Antalya)
        Matcher arrM = ARRIVAL_CITY_PATTERN.matcher(lower);
        while (arrM.find()) {
            String candidate = arrM.group(1);
            String matchedCity = findCityMatch(candidate, FLIGHT_CITIES);
            if (matchedCity != null
                    && !matchedCity.equalsIgnoreCase(c.getDepartureLocation())) {
                c.setArrivalLocation(capitalize(matchedCity));
                break;
            }
        }

        // Doğrudan şehir adı (suffix olmadan) — kalkış veya varış belirsizse atla
        if (c.getDepartureLocation() == null || c.getArrivalLocation() == null) {
            for (String city : FLIGHT_CITIES) {
                if (containsCity(lower, city)) {
                    if (c.getDepartureLocation() == null
                            && !city.equalsIgnoreCase(c.getArrivalLocation())) {
                        c.setDepartureLocation(capitalize(city));
                    } else if (c.getArrivalLocation() == null
                            && !city.equalsIgnoreCase(c.getDepartureLocation())) {
                        c.setArrivalLocation(capitalize(city));
                    }
                }
            }
        }

        extractFlightDates(lower, c, awaitingField);
    }

    private void extractFlightDates(String lower, SearchCriteria c, String awaitingField) {
        List<LocalDate> dates = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();

        Matcher m = DATE_WITH_LABEL_PATTERN.matcher(lower);
        while (m.find()) {
            LocalDate d = buildDate(
                    Integer.parseInt(m.group(2)),
                    m.group(3).toLowerCase(Locale.ROOT));
            if (d != null) {
                dates.add(d);
                String label = m.group(1) != null ? m.group(1) : m.group(4);
                labels.add(label);
            }
        }

        if (dates.isEmpty()) {
            Matcher mdm = MONTH_DAY_WITH_LABEL_PATTERN.matcher(lower);
            while (mdm.find()) {
                LocalDate d = buildDate(
                        Integer.parseInt(mdm.group(3)),
                        mdm.group(2).toLowerCase(Locale.ROOT));
                if (d != null) {
                    dates.add(d);
                    String label = mdm.group(1) != null ? mdm.group(1) : mdm.group(4);
                    labels.add(label);
                }
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

        // --- NEW LOGIC FOR AWAITING_FIELD Context ---
        if (dates.size() == 1 && awaitingField != null) {
            String lowerAwaiting = awaitingField.toLowerCase(Locale.ROOT);
            if (lowerAwaiting.contains("gidiş tarihi") || lowerAwaiting.contains("departuredate")) {
                c.setDepartureDate(dates.get(0));
                return;
            } else if (lowerAwaiting.contains("dönüş tarihi") || lowerAwaiting.contains("returndate")) {
                c.setReturnDate(dates.get(0));
                return;
            }
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
                if (label != null && (label.contains("çıkış") || label.contains("cikis") || label.contains("checkout") || label.contains("bitiş") || label.contains("bitis") || label.contains("dönüş") || label.contains("donus") || label.contains("return"))) {
                    c.setReturnDate(d);
                } else if (label != null) {
                    c.setDepartureDate(d);
                } else {
                    if (c.getDepartureDate() == null && c.getReturnDate() != null) {
                         if (d.isBefore(c.getReturnDate())) {
                             c.setDepartureDate(d);
                         }
                    } else if (c.getReturnDate() == null && c.getDepartureDate() != null) {
                         if (d.isAfter(c.getDepartureDate())) {
                             c.setReturnDate(d);
                         }
                    }
                }
            }
        } else {
            if (dates.size() >= 2) {
                LocalDate d1 = dates.get(0);
                LocalDate d2 = dates.get(1);
                if (d1.isAfter(d2)) {
                    c.setDepartureDate(d2);
                    c.setReturnDate(d1);
                } else {
                    c.setDepartureDate(d1);
                    c.setReturnDate(d2);
                }
            } else if (dates.size() == 1) {
                c.setDepartureDate(dates.get(0));
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
        return switch (m.group(1).toLowerCase(Locale.ROOT)) {
            case "tl", "try", "türk lirası", "turk lirasi", "lira" -> "TRY";
            case "eur", "euro" -> "EUR";
            case "usd", "dolar" -> "USD";
            case "gbp", "sterlin" -> "GBP";
            default -> m.group(1).toUpperCase();
        };
    }

    private LocalDate buildDate(int day, String monthTr) {
        Integer monthNum = MONTHS_BY_NAME.get(monthTr.toLowerCase(Locale.forLanguageTag("tr-TR")));
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
        List<LocalDate> dates = new java.util.ArrayList<>();
        Matcher m = DATE_WITH_LABEL_PATTERN.matcher(lower);
        while (m.find()) {
            LocalDate d = buildDate(
                    Integer.parseInt(m.group(2)),
                    m.group(3).toLowerCase(Locale.forLanguageTag("tr-TR")));
            if (d != null)
                dates.add(d);
        }
        if (dates.isEmpty()) {
            // "August 1st" gibi İngilizce "ay gün" sırasını dene
            Matcher mdm = MONTH_DAY_WITH_LABEL_PATTERN.matcher(lower);
            while (mdm.find()) {
                LocalDate d = buildDate(
                        Integer.parseInt(mdm.group(3)),
                        mdm.group(2).toLowerCase(Locale.forLanguageTag("tr-TR")));
                if (d != null)
                    dates.add(d);
            }
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

    private String normalizeForCityComparison(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.forLanguageTag("tr-TR"))
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ç', 'c');
    }

    private String findCityMatch(String candidate, List<String> cities) {
        if (candidate == null) return null;
        String normCandidate = normalizeForCityComparison(candidate);
        for (String city : cities) {
            if (normalizeForCityComparison(city).equals(normCandidate)) {
                return city;
            }
        }
        return null;
    }

    private boolean containsCity(String text, String city) {
        if (text == null || city == null) return false;
        String normText = normalizeForCityComparison(text);
        String normCity = normalizeForCityComparison(city);
        return normText.contains(normCity);
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank())
            return s;
        return s.substring(0, 1).toUpperCase(Locale.forLanguageTag("tr-TR"))
                + s.substring(1).toLowerCase(Locale.forLanguageTag("tr-TR"));
    }

    public LocalDate parseSingleDate(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase(Locale.forLanguageTag("tr-TR"));
        
        // 1. Try numeric date
        List<LocalDate> numericDates = extractNumericDates(lower);
        if (!numericDates.isEmpty()) {
            return numericDates.get(0);
        }
        
        // 2. Try word date
        Matcher m = DATE_WITH_LABEL_PATTERN.matcher(lower);
        if (m.find()) {
            return buildDate(Integer.parseInt(m.group(2)), m.group(3).toLowerCase(Locale.forLanguageTag("tr-TR")));
        }
        
        return null;
    }

    public String parseLocation(String text, boolean isFlight) {
        if (text == null || text.isBlank()) return null;
        List<String> cities = isFlight ? FLIGHT_CITIES : HOTEL_CITIES;
        for (String city : cities) {
            if (containsCity(text, city)) {
                return capitalize(city);
            }
        }
        // Fallback: strip punctuation and capitalize
        String cleaned = text.replaceAll("[.,!?']", "").trim();
        if (cleaned.length() > 0) {
            return capitalize(cleaned);
        }
        return null;
    }

    public String parseCurrency(String text) {
        if (text == null || text.isBlank()) return null;
        return extractCurrency(text.toLowerCase(Locale.forLanguageTag("tr-TR")));
    }

    public String parseTripType(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase(Locale.forLanguageTag("tr-TR"));
        if (lower.contains("tek") || lower.contains("one")) {
            return "ONE_WAY";
        }
        if (lower.contains("dönüş") || lower.contains("donus") || lower.contains("round") || lower.contains("gidiş") || lower.contains("gidis")) {
            return "ROUND_TRIP";
        }
        return null;
    }
}
