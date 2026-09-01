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
            Map.entry("şubat", 2), Map.entry("subat", 2), Map.entry("february", 2),
            Map.entry("mart", 3), Map.entry("march", 3),
            Map.entry("nisan", 4), Map.entry("april", 4),
            Map.entry("mayıs", 5), Map.entry("mayis", 5), Map.entry("may", 5),
            Map.entry("haziran", 6), Map.entry("june", 6),
            Map.entry("temmuz", 7), Map.entry("july", 7),
            Map.entry("ağustos", 8), Map.entry("agustos", 8), Map.entry("august", 8),
            Map.entry("eylül", 9), Map.entry("eylul", 9), Map.entry("september", 9),
            Map.entry("ekim", 10), Map.entry("october", 10),
            Map.entry("kasım", 11), Map.entry("kasim", 11), Map.entry("november", 11),
            Map.entry("aralık", 12), Map.entry("aralik", 12), Map.entry("december", 12));

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

    public static final Pattern NUMERIC_DATE_PATTERN = Pattern.compile(
            "\\b(?:"
          + "(\\d{4})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\\d|3[01])"
          + "|"
          + "(0?[1-9]|[12]\\d|3[01])[-/.](0?[1-9]|1[0-2])[-/.](?:\\d{4}|\\d{2})"
          + ")\\b");

    // ── Sayı + kişi ifadeleri ─────────────────────────────────────────────────
    // Eksi işareti de yakalanır (ör. "-3 yetişkin") ki SearchCriteriaValidator
    // negatif sayıyı görüp kullanıcıyı uyarabilsin — önceden "\d+" işareti atlayıp
    // "-3"ü sessizce "3"e çeviriyordu. "(?<!\d)" ile "3-4 kişi" gibi bir aralık
    // ifadesindeki tireyi eksi işareti sanmıyoruz (önünde başka bir rakam varsa
    // eksi işareti almıyoruz).
    private static final Pattern ADULT_PATTERN = Pattern.compile(
            "((?<!\\d)-?\\d+)\\s*(?:tane\\s*|adet\\s*)?(?:yetişkin|yetiskin|adult|adults|kişi|kisi)|(?:yetişkin|yetiskin|adult|adults)\\s*(?:sayısı\\s*)?((?<!\\d)-?\\d+)\\s*(?:tane\\s*|adet\\s*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHILD_PATTERN = Pattern.compile(
            "((?<!\\d)-?\\d+)\\s*(?:tane\\s*|adet\\s*)?(?:çocuk|cocuk|child|children|kids)|(?:çocuk|cocuk|child|children|kids)\\s*(?:sayısı\\s*)?((?<!\\d)-?\\d+)\\s*(?:tane|adet)|(?:çocuk|cocuk|child|children|kids)\\s*sayısı\\s*:?\\s*((?<!\\d)-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INFANT_PATTERN = Pattern.compile(
            "((?<!\\d)-?\\d+)\\s*(?:tane\\s*|adet\\s*)?(?:bebek|infant|infants|baby|babies)|(?:bebek|infant|infants|baby|babies)\\s*(?:sayısı\\s*)?((?<!\\d)-?\\d+)\\s*(?:tane|adet)|(?:bebek|infant|infants|baby|babies)\\s*sayısı\\s*:?\\s*((?<!\\d)-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INCREMENTAL_CHILD_PATTERN = Pattern.compile(
            "(?:(?:(\\d{1,2})|bir|1|\\+1)\\s*(?:tane\\s*|adet\\s*)?)?(?:de\\s*|da\\s*)?(?:çocuk|cocuk|child|children|kid|kids|çocuğumuz|cocugumuz)\\s*(?:de\\s*|da\\s*)?(?:daha|ekle|eklensin|ekleyelim|ilave|dahil|(?:te|de)?\\s*olacak)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INCREMENTAL_INFANT_PATTERN = Pattern.compile(
            "(?:(?:(\\d{1,2})|bir|1|\\+1)\\s*(?:tane\\s*|adet\\s*)?)?(?:de\\s*|da\\s*)?(?:bebek|infant|infants|baby|babies|bebeğimiz|bebegimiz)\\s*(?:de\\s*|da\\s*)?(?:daha|ekle|eklensin|ekleyelim|ilave|dahil|(?:te|de)?\\s*olacak)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOVE_INFANT_PATTERN = Pattern.compile(
            "\\b(?:0|zero|no)\\s+(?:bebek|bebekler|infant|infants|baby|babies|bebeği|bebegi|bebeğim|bebegim)\\b|\\b(?:bebek|bebekler|bebekleri|bebeklerimiz|infant|infants|baby|babies|bebeği|bebegi|bebeğim|bebegim)\\s+(?:olmayacak|olmasın|olmasin|iptal|iptal\\s+olacak|iptal\\s+et|çıkar|cikar|sil|kaldır|kaldir|istemiyorum|istemiyoruz|yok|yoktur|vazgeç|vazgec|düş|dus)\\b|\\b(?:iptal|iptal\\s+olacak|iptal\\s+et|çıkar|cikar|sil|kaldır|kaldir|düş|dus)\\s+(?:bebek|bebekler|bebekleri|infant|baby|bebeği|bebegi)\\b|\\b(?:bebeksiz|bebeksuz|nobaby)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOVE_CHILD_PATTERN = Pattern.compile(
            "\\b(?:0|zero|no)\\s+(?:çocuk|çocuklar|cocuk|cocuklar|child|children|kid|kids|çocuğu|cocugu|çocuğum|cocugum)\\b|\\b(?:çocuk|çocuklar|çocukları|çocuklarımız|cocuk|cocuklar|child|children|kid|kids|çocuğu|cocugu|çocuğum|cocugum)\\s+(?:olmayacak|olmasın|olmasin|iptal|iptal\\s+olacak|iptal\\s+et|çıkar|cikar|sil|kaldır|kaldir|istemiyorum|istemiyoruz|yok|yoktur|vazgeç|vazgec|düş|dus)\\b|\\b(?:iptal|iptal\\s+olacak|iptal\\s+et|çıkar|cikar|sil|kaldır|kaldir|düş|dus)\\s+(?:çocuk|çocuklar|çocukları|cocuk|cocuklar|child|çocuğu|cocugu)\\b|\\b(?:çocuksuz|cocuksuz|nochildren|nokids)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOM_PATTERN = Pattern.compile(
            "((?<!\\d)-?\\d+)\\s*(?:tane\\s*|adet\\s*)?(?:oda|room|rooms)|(?:oda|room|rooms)\\s*(?:sayısı\\s*)?((?<!\\d)-?\\d+)\\s*(?:tane\\s*|adet\\s*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSENGER_PATTERN = Pattern.compile(
            "((?<!\\d)-?\\d+)\\s*(?:tane\\s*|adet\\s*)?(?:yolcu|kişi|kisi|passenger|passengers|person|people|kişilik|kisilik|yetişkin|yetiskin|adult|adults)|(?:yolcu|passenger|passengers|kişi|kisi|person|people|kişilik|kisilik|yetişkin|yetiskin|adult|adults)\\s*(?:sayısı\\s*)?((?<!\\d)-?\\d+)\\s*(?:tane\\s*|adet\\s*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTH_AGE_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:aylık|aylik|ay)\\s*(?:bebek|infant|baby|çocuk|cocuk|child)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_AGE_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:yaşında|yaşinda|yaş|yas)\\s*(?:çocuk|cocuk|child|bebek|infant|baby)?", Pattern.CASE_INSENSITIVE);

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
            "(?<![a-zA-Zçğıöşüİı])([a-zA-Zçğıöşüİı]+)(?:'?(?:dan|den|tan|ten))(?![a-zA-Zçğıöşüİı])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ── Varış: "Antalya'ya", "Antalya ya" ────────────────────────────────────
    private static final Pattern ARRIVAL_CITY_PATTERN = Pattern.compile(
            "(?<![a-zA-Zçğıöşüİı])([a-zA-Zçğıöşüİı]+)(?:'?(?:ya|ye|a|e))(?![a-zA-Zçğıöşüİı])",
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
        String normalizedQuery = normalizeForCityComparison(lower);
        for (String city : HOTEL_CITIES) {
            String normalizedCity = normalizeForCityComparison(city);
            if (normalizedQuery.contains(normalizedCity)) {
                c.setLocationOrHotelName(capitalize(city));
                break;
            }
        }

        // Oda
        Integer rmCount = extractMatchedGroup(ROOM_PATTERN.matcher(lower));
        if (rmCount != null) c.setRoomCount(rmCount);

        // Yetişkin
        Integer amCount = extractMatchedGroup(ADULT_PATTERN.matcher(lower));
        if (amCount != null) c.setAdultCount(amCount);

        // 1. Önce açık çocuk kaldırma / iptal / 0 çocuk kontrolü
        if (REMOVE_CHILD_PATTERN.matcher(lower).find()) {
            c.setExplicitChildRemoval(true);
            c.setChildCount(0);
            c.setChildAges(new ArrayList<>());
        } else {
            // Önce artımlı Çocuk Ekleme ("1 çocuk daha var", "1 tane de çocuk olacak", "çocuk ekle")
            Matcher incChildMatcher = INCREMENTAL_CHILD_PATTERN.matcher(lower);
            if (incChildMatcher.find() && !lower.contains("olmayacak") && !lower.contains("olmasın") && !lower.contains("iptal") && !lower.contains("sil") && !lower.contains("çıkar")) {
                String g1 = incChildMatcher.groupCount() >= 1 ? incChildMatcher.group(1) : null;
                int add = 1;
                if (g1 != null && !g1.isBlank()) add = Integer.parseInt(g1);
                c.setIncrementalChildCount(add);
            } else {
                // Açık sayısal çocuk kontrolü ("2 çocuk", "3 çocuk")
                Integer cmCount = extractMatchedGroup(CHILD_PATTERN.matcher(lower));
                if (cmCount != null) {
                    c.setChildCount(cmCount);
                } else if ((lower.contains("çocuk") || lower.contains("cocuk") || lower.contains("child") || lower.contains("kid"))
                           && !lower.contains("0 çocuk") && !lower.contains("çocuk yok") && !lower.contains("çocuk olmasın") && !lower.contains("çocuksuz") && !lower.contains("olmayacak") && !lower.contains("iptal") && !lower.contains("sil") && !lower.contains("çıkar")) {
                    c.setIncrementalChildCount(1);
                }
            }
        }

        // 2. Önce açık bebek kaldırma / iptal / 0 bebek kontrolü
        if (REMOVE_INFANT_PATTERN.matcher(lower).find()) {
            c.setExplicitInfantRemoval(true);
            c.setInfantCount(0);
            c.setInfantAges(new ArrayList<>());
            c.setInfantAgesInMonths(new ArrayList<>());
        } else {
            // Önce artımlı Bebek Ekleme ("1 bebek daha var", "1 tane de bebek olacak", "bebek ekle")
            Matcher incInfantMatcher = INCREMENTAL_INFANT_PATTERN.matcher(lower);
            if (incInfantMatcher.find() && !lower.contains("olmayacak") && !lower.contains("olmasın") && !lower.contains("iptal") && !lower.contains("sil") && !lower.contains("çıkar")) {
                String g1 = incInfantMatcher.groupCount() >= 1 ? incInfantMatcher.group(1) : null;
                int add = 1;
                if (g1 != null && !g1.isBlank()) add = Integer.parseInt(g1);
                c.setIncrementalInfantCount(add);
            } else {
                // Açık sayısal bebek kontrolü ("2 bebek", "1 bebek")
                Integer imCount = extractMatchedGroup(INFANT_PATTERN.matcher(lower));
                if (imCount != null && imCount <= 3) {
                    c.setInfantCount(imCount);
                } else if ((lower.contains("bebek") || lower.contains("infant") || lower.contains("baby") || lower.contains("bebeğ"))
                           && !lower.contains("0 bebek") && !lower.contains("bebek yok") && !lower.contains("bebek olmasın") && !lower.contains("bebeksiz") && !lower.contains("olmayacak") && !lower.contains("iptal") && !lower.contains("sil") && !lower.contains("çıkar")) {
                    c.setIncrementalInfantCount(1);
                }
            }
        }

        // Bebek/Çocuk Yaş ve Ay Detayları ("14 aylık", "5 yaşında")
        extractAgeAndMonthDetails(lower, c);

        // Tarihler (giriş & çıkış)
        extractHotelDates(lower, c, awaitingField);

        // Gece sayısı ve çıkış tarihi hesaplama
        Matcher nm = NIGHT_PATTERN.matcher(lower);
        if (nm.find()) {
            int nights = Integer.parseInt(nm.group(1));
            if (c.getCheckInDate() != null) {
                c.setCheckOutDate(c.getCheckInDate().plusDays(nights));
            }
        }
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
    private void extractFlightFields(String lower, SearchCriteria c, String awaitingField) {

        // Yetişkin
        Integer amCount = extractMatchedGroup(ADULT_PATTERN.matcher(lower));
        if (amCount != null) c.setAdultCount(amCount);

        // 1. Önce açık sayısal çocuk kontrolü ("2 çocuk", "3 çocuk")
        Integer cmCount = extractMatchedGroup(CHILD_PATTERN.matcher(lower));
        if (cmCount != null) {
            c.setChildCount(cmCount);
        } else {
            // Artımlı Çocuk Ekleme ("1 çocuk daha var", "1 tane de çocuk olacak", "çocuk ekle")
            Matcher incChildMatcher = INCREMENTAL_CHILD_PATTERN.matcher(lower);
            if (incChildMatcher.find()) {
                String g1 = incChildMatcher.groupCount() >= 1 ? incChildMatcher.group(1) : null;
                int add = 1;
                if (g1 != null && !g1.isBlank()) add = Integer.parseInt(g1);
                c.setIncrementalChildCount(add);
            } else if (lower.contains("çocuk") || lower.contains("cocuk") || lower.contains("child") || lower.contains("kid")) {
                if (!lower.contains("0 çocuk") && !lower.contains("çocuk yok") && !lower.contains("çocuk olmasın") && !lower.contains("çocuksuz")) {
                    c.setIncrementalChildCount(1);
                }
            }
        }

        // 2. Önce açık sayısal bebek kontrolü ("2 bebek", "1 bebek")
        Integer imCount = extractMatchedGroup(INFANT_PATTERN.matcher(lower));
        if (imCount != null) {
            c.setInfantCount(imCount);
        } else {
            // Artımlı Bebek Ekleme ("1 tane de bebek olacak", "bebek ekle")
            Matcher incInfantMatcher = INCREMENTAL_INFANT_PATTERN.matcher(lower);
            if (incInfantMatcher.find()) {
                String g1 = incInfantMatcher.groupCount() >= 1 ? incInfantMatcher.group(1) : null;
                int add = 1;
                if (g1 != null && !g1.isBlank()) add = Integer.parseInt(g1);
                c.setIncrementalInfantCount(add);
            } else if (lower.contains("bebek") || lower.contains("infant") || lower.contains("baby") || lower.contains("bebeğ")) {
                if (!lower.contains("0 bebek") && !lower.contains("bebek yok") && !lower.contains("bebek olmasın") && !lower.contains("bebeksiz")) {
                    c.setIncrementalInfantCount(1);
                }
            }
        }

        // Genel yolcu sayısı (sadece özel yetişkin/çocuk/bebek belirtilmediyse)
        if (amCount == null && c.getChildCount() == null && c.getInfantCount() == null
                && c.getIncrementalChildCount() == null && c.getIncrementalInfantCount() == null) {
            Matcher pm = PASSENGER_PATTERN.matcher(lower);
            if (pm.find()) {
                int parsedCount = Integer.parseInt(pm.group(1));
                c.setPassengerCount(parsedCount);
                c.setAdultCount(parsedCount);
            }
        }

        // Bebek/Çocuk Yaş ve Ay Detayları ("14 aylık", "5 yaşında")
        extractAgeAndMonthDetails(lower, c);

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

        // Kalkış ve varış şehirleri çıkarımı (suffix kontrolleri)
        String normalizedFlightQuery = normalizeForCityComparison(lower);
        for (String city : FLIGHT_CITIES) {
            String normalizedCity = normalizeForCityComparison(city);
            int idx = normalizedFlightQuery.indexOf(normalizedCity);
            if (idx != -1) {
                String suffix = normalizedFlightQuery.substring(idx + normalizedCity.length()).trim();
                if (suffix.startsWith("'")) {
                    suffix = suffix.substring(1).trim();
                }
                if (suffix.startsWith("dan") || suffix.startsWith("den") || suffix.startsWith("tan") || suffix.startsWith("ten")) {
                    c.setDepartureLocation(capitalize(city));
                } else if (suffix.startsWith("ya") || suffix.startsWith("ye") || suffix.startsWith("a") || suffix.startsWith("e")) {
                    c.setArrivalLocation(capitalize(city));
                }
            }
        }

        // Doğrudan şehir adı (suffix olmadan) — kalkış veya varış belirsizse atla
        if (c.getDepartureLocation() == null || c.getArrivalLocation() == null) {
            for (String city : FLIGHT_CITIES) {
                String normalizedCity = normalizeForCityComparison(city);
                if (normalizedFlightQuery.contains(normalizedCity)) {
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

    private void extractAgeAndMonthDetails(String lower, SearchCriteria c) {
        if (lower == null || lower.isBlank()) return;

        String temp = lower;

        // 1. "14 aylık", "6 aylık" gibi ay ifadelerini ayıkla ve metinden temizle
        Matcher mm = MONTH_AGE_PATTERN.matcher(temp);
        StringBuffer sbMonth = new StringBuffer();
        while (mm.find()) {
            try {
                int months = Integer.parseInt(mm.group(1));
                int ageYears = months / 12; // 14 aylık -> 1 yaş, 6 aylık -> 0 yaş
                if (months < 24) {
                    if (c.getInfantAges() == null) c.setInfantAges(new ArrayList<>());
                    c.getInfantAges().add(ageYears);
                    if (c.getInfantAgesInMonths() == null) c.setInfantAgesInMonths(new ArrayList<>());
                    c.getInfantAgesInMonths().add(months);
                } else {
                    if (c.getChildAges() == null) c.setChildAges(new ArrayList<>());
                    c.getChildAges().add(ageYears);
                }
            } catch (Exception ignored) {}
            mm.appendReplacement(sbMonth, "");
        }
        mm.appendTail(sbMonth);
        temp = sbMonth.toString();

        // 2. "5 yaşında", "3 yaş" gibi yaş ifadelerini ayıkla ve metinden temizle
        Matcher ym = YEAR_AGE_PATTERN.matcher(temp);
        StringBuffer sbYear = new StringBuffer();
        while (ym.find()) {
            try {
                int age = Integer.parseInt(ym.group(1));
                if (age <= 1) {
                    if (c.getInfantAges() == null) c.setInfantAges(new ArrayList<>());
                    c.getInfantAges().add(age);
                    if (c.getInfantAgesInMonths() == null) c.setInfantAgesInMonths(new ArrayList<>());
                    c.getInfantAgesInMonths().add(age == 1 ? 12 : 6);
                } else if (age <= 12) {
                    if (c.getChildAges() == null) c.setChildAges(new ArrayList<>());
                    c.getChildAges().add(age);
                }
            } catch (Exception ignored) {}
            ym.appendReplacement(sbYear, "");
        }
        ym.appendTail(sbYear);
        temp = sbYear.toString();

        // 3. Fallback: Eğer çocuk veya bebek sayısı belirtilmişse, ama girilen yaş listesi eksik kalmışsa,
        // metindeki diğer sayıları bulup yaş olarak eklemeye çalışıyoruz (ör. "2 çocuk, biri 5 diğeri 7 yaşında").
        temp = CHILD_PATTERN.matcher(temp).replaceAll("");
        temp = INFANT_PATTERN.matcher(temp).replaceAll("");
        temp = ADULT_PATTERN.matcher(temp).replaceAll("");
        temp = PASSENGER_PATTERN.matcher(temp).replaceAll("");
        temp = ROOM_PATTERN.matcher(temp).replaceAll("");
        temp = NIGHT_PATTERN.matcher(temp).replaceAll("");

        if (Pattern.compile("\\b(?:yaş|yaşında|yaşinda|yas|aylık|aylik)\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
            Matcher numMatcher = Pattern.compile("\\d+").matcher(temp);
            while (numMatcher.find()) {
                try {
                    int age = Integer.parseInt(numMatcher.group());
                    if (age <= 1) {
                        if (c.getInfantAges() == null) c.setInfantAges(new ArrayList<>());
                        c.getInfantAges().add(age);
                        if (c.getInfantAgesInMonths() == null) c.setInfantAgesInMonths(new ArrayList<>());
                        c.getInfantAgesInMonths().add(age == 1 ? 12 : 6);
                    } else if (age <= 12) {
                        if (c.getChildAges() == null) c.setChildAges(new ArrayList<>());
                        c.getChildAges().add(age);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Post-processing:
        // Eğer childCount önceden set edilmişse ve childAges listesinden büyükse,
        // ve childAges tek bir elemana sahipse (ör. "2 çocuk, ikisi de 5 yaşında"),
        // bu tek yaşı childCount adet olacak şekilde dolduruyoruz.
        if (c.getChildCount() != null && c.getChildCount() > 0) {
            if (c.getChildAges() != null && c.getChildAges().size() == 1 && c.getChildAges().size() < c.getChildCount()) {
                int singleAge = c.getChildAges().get(0);
                while (c.getChildAges().size() < c.getChildCount()) {
                    c.getChildAges().add(singleAge);
                }
            }
        } else {
            // Eğer childCount set edilmemişse, girilen yaş sayısı kadar çocuk olduğunu varsayıyoruz
            if (c.getChildAges() != null && !c.getChildAges().isEmpty()) {
                c.setChildCount(c.getChildAges().size());
            }
        }

        // Aynı işlemi bebekler için de yapıyoruz
        if (c.getInfantCount() != null && c.getInfantCount() > 0) {
            if (c.getInfantAges() != null && c.getInfantAges().size() == 1 && c.getInfantAges().size() < c.getInfantCount()) {
                int singleAge = c.getInfantAges().get(0);
                while (c.getInfantAges().size() < c.getInfantCount()) {
                    c.getInfantAges().add(singleAge);
                }
            }
            // Align infantAgesInMonths size with infantAges list size
            if (c.getInfantAgesInMonths() == null) {
                c.setInfantAgesInMonths(new ArrayList<>());
            }
            if (c.getInfantAges() != null) {
                if (c.getInfantAgesInMonths().size() == 1 && c.getInfantAgesInMonths().size() < c.getInfantCount()) {
                    int singleMonths = c.getInfantAgesInMonths().get(0);
                    while (c.getInfantAgesInMonths().size() < c.getInfantCount()) {
                        c.getInfantAgesInMonths().add(singleMonths);
                    }
                }
                while (c.getInfantAgesInMonths().size() < c.getInfantAges().size()) {
                    int ageInYears = c.getInfantAges().get(c.getInfantAgesInMonths().size());
                    c.getInfantAgesInMonths().add(ageInYears == 1 ? 12 : 6);
                }
            }
        } else {
            if (c.getInfantAges() != null && !c.getInfantAges().isEmpty()) {
                c.setInfantCount(c.getInfantAges().size());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Integer extractMatchedGroup(Matcher m) {
        if (m.find()) {
            String g1 = m.group(1);
            if (g1 != null && !g1.isBlank()) return Integer.parseInt(g1);
            String g2 = m.group(2);
            if (g2 != null && !g2.isBlank()) return Integer.parseInt(g2);
            if (m.groupCount() >= 3) {
                String g3 = m.group(3);
                if (g3 != null && !g3.isBlank()) return Integer.parseInt(g3);
            }
        }
        return null;
    }

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
        if (text == null || text.isBlank()) return dates;
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
                    String matchedStr = m.group();
                    int lastSepIndex = Math.max(matchedStr.lastIndexOf('.'), Math.max(matchedStr.lastIndexOf('/'), matchedStr.lastIndexOf('-')));
                    int year = Integer.parseInt(matchedStr.substring(lastSepIndex + 1));
                    if (year < 100) {
                        year += 2000;
                    }
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
        String normalized = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT)
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
        String lower = text.toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).trim();
        if (isGeneralPoi(lower)) {
            return null;
        }
        List<String> cities = isFlight ? FLIGHT_CITIES : HOTEL_CITIES;
        for (String city : cities) {
            if (containsCity(text, city)) {
                return capitalize(city);
            }
        }
        // Fallback: strip punctuation and capitalize
        String cleaned = text.replaceAll("[.,!?']", "").trim();
        if (cleaned.length() > 0) {
            if (isGeneralPoi(cleaned.toLowerCase(java.util.Locale.forLanguageTag("tr-TR")))) {
                return null;
            }
            return capitalize(cleaned);
        }
        return null;
    }

    private boolean isGeneralPoi(String text) {
        if (text == null) return false;
        List<String> pois = List.of(
            "lunapark", "plaj", "havalimanı", "havalimani", "havaalanı", "havaalani",
            "otogar", "müze", "muze", "merkez", "beach", "museum", "airport",
            "theme park", "themepark", "aquapark", "su park"
        );
        for (String poi : pois) {
            if (text.contains(poi)) {
                return true;
            }
        }
        return false;
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
