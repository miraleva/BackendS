package com.santsg.tourvisio.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.client.AIFallbackChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ExtractionAgent {

    private static final Logger log = LoggerFactory.getLogger(ExtractionAgent.class);

    /** Gemini Lite → OpenRouter (ücretsiz) fallback zinciri. Bkz. {@code AIProviderConfig}. */
    private final AIFallbackChain geminiExtractionClient;
    private final ObjectMapper objectMapper;

    public ExtractionAgent(@Qualifier("extractionAiChain") AIFallbackChain geminiExtractionClient) {
        this.geminiExtractionClient = geminiExtractionClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Extracts intent and criteria from the user message.
     *
     * @param message          The user message.
     * @param currentIntent    The current search type if in an active session, otherwise null.
     * @param existingCriteria Oturumda o ana kadar toplanmış kriterler (varsa); "1 tane daha bebek
     *                         geliyor" gibi artımlı/bağlama bağlı mesajları doğru yorumlamak için
     *                         mevcut yolcu sayılarını modele gösterir. Aktif arama yoksa null.
     * @return The extraction result.
     * @throws RuntimeException on API failure, mock responses, or JSON parsing failures.
     */
    public ExtractionResult extract(String message, String currentIntent, String awaitingField,
            SearchCriteria existingCriteria) {
        return extract(message, currentIntent, awaitingField, existingCriteria, false);
    }

    /**
     * @param lastSearchHadNoResults Son arama sonuçsuz kaldıysa true — modelin, kullanıcının
     *                               şimdi daha basit/kısıtlı bir kriterle tekrar denediğini
     *                               (ör. bebek/çocuk olmadan sadece yetişkin sayısı vererek)
     *                               daha güvenilir şekilde anlayabilmesi için bağlam verir.
     */
    public ExtractionResult extract(String message, String currentIntent, String awaitingField,
            SearchCriteria existingCriteria, boolean lastSearchHadNoResults) {
        if (message == null || message.trim().isEmpty()) {
            SearchCriteria emptyCriteria = new SearchCriteria();
            return new ExtractionResult("UNKNOWN", emptyCriteria);
        }

        String todayStr = LocalDate.now().toString();
        String activeIntentContext = (currentIntent != null)
                ? String.format("The current active search intent is %s. Keep this intent unless the user explicitly switches the travel context.", currentIntent)
                : "No active search intent exists yet. Determine it from the message.";

        String currentCountsContext = "";
        if (existingCriteria != null && currentIntent != null) {
            boolean adultCountSpecificallyAwaited = awaitingField != null
                    && awaitingField.contains("yetişkin sayısı");
            boolean childrenAlreadyResolved = existingCriteria.getChildCount() == null
                    || existingCriteria.getChildCount() == 0
                    || !existingCriteria.getChildAges().isEmpty();
            boolean infantsAlreadyResolved = existingCriteria.getInfantCount() == null
                    || existingCriteria.getInfantCount() == 0
                    || !existingCriteria.getInfantAges().isEmpty();

            currentCountsContext = String.format(
                    "%nCurrent already-known traveler counts for this search: adults=%s, children=%s, infants=%s.%s\n"
                            + "Use real judgment (like an experienced human travel agent would) to decide what the user's message "
                            + "means for these counts — don't rely on spotting specific keywords or phrases, reason about intent:\n"
                            + "- If the message clearly ADDS TO or ADJUSTS one category while leaving the rest of the party implicitly "
                            + "unchanged (e.g. \"one more baby is coming\", \"add a child\", \"make it 4 adults instead\", \"my wife is coming too\"), "
                            + "compute the new absolute total for that field only, and leave the other fields (children/infants if not the one "
                            + "being adjusted) omitted so they keep their current value.\n"
                            + "- If the message reads as the user proposing a NEW, SELF-CONTAINED party size — a plain restatement of just the "
                            + "adult count with no mention of children/infants at all (e.g. answering \"how many adults?\" with a bare number, "
                            + "\"2 yetişkin var mı\", \"is there anything for 2 adults\", \"just 2 people\", \"never mind, 2 adults\", especially "
                            + "right after a search came back with no results and the user seems to be retrying with a simpler/smaller party) — "
                            + "this signals the user no longer wants the previously-known children/infants included. In that case, explicitly output "
                            + "childCount: 0 and infantCount: 0 (and empty childAges/infantAges arrays) in your JSON — do NOT omit them.%s\n"
                            + "- If the message has nothing to do with party size (e.g. it's about dates, location, or an unrelated topic), omit "
                            + "childCount/infantCount/childAges/infantAges entirely as usual — do not default them to 0.\n"
                            + "When genuinely ambiguous, prefer keeping children/infants as they are (omit) rather than dropping them, UNLESS the "
                            + "no-results-retry context below suggests the user is deliberately simplifying their search.",
                    existingCriteria.getAdultCount(), existingCriteria.getChildCount(), existingCriteria.getInfantCount(),
                    lastSearchHadNoResults
                            ? " IMPORTANT CONTEXT: the user's last search with these exact counts returned NO RESULTS, and they are now sending "
                              + "a new message — this makes it more likely they are retrying with a deliberately reduced/simpler party size."
                            : "",
                    (adultCountSpecificallyAwaited && childrenAlreadyResolved && infantsAlreadyResolved)
                            ? " EXCEPTION: right now the assistant specifically asked ONLY for \"yetişkin sayısı\" (adult count) as a direct "
                              + "follow-up question, and children/infants for this search are already fully resolved (their ages are known, or "
                              + "there are none). In this exact situation, treat a plain adult-count answer (e.g. \"2 yetişkin\") as simply "
                              + "answering that specific question — do NOT reset childCount/infantCount to 0 just because they weren't repeated."
                            : "");
        }

        String schemaDescription = """
                {
                  "intent": "Determine the user's intent. Must be one of: HOTEL_SEARCH, FLIGHT_SEARCH, UNKNOWN, OUT_OF_SCOPE",
                  "criteria": {
                    // For HOTEL_SEARCH:
                    "locationOrHotelName": "city or hotel name (e.g. Antalya). CRITICAL: If the user did not specify a specific city/province/district/country name (e.g. Antalya, Belek, Paris, Istanbul), DO NOT fill this field; leave it null or return an empty string. General POI/amenity names (such as lunapark, plaj, havalimanı, otogar, müze, merkez, beach, theme park, airport, etc.) are NOT city or location names and must NOT be put into this field.",
                    "checkInDate": "check-in date in YYYY-MM-DD format, ONLY if the user's message contains an actual date reference (a specific date, weekday, or relative expression like 'tomorrow'/'yarın'/'next week'). Today's date is %s, used only to resolve relative/partial dates. Handle multiple formats robustly (e.g. '13.6.26' -> '2026-06-13', '13/04/2027', '12-08-2026'). If only month/day (e.g. 15 July, haziran 13) is specified, resolve to the nearest future occurrence using today's date. NEVER output today's date as checkInDate just because no date was mentioned — leave it null/omitted instead.",
                    "checkOutDate": "check-out date in YYYY-MM-DD format. If night count is given, calculate check-out by adding it to check-in. Same 'only if explicitly mentioned' rule as checkInDate applies.",
                    "adultCount": "integer. If the user writes an explicit negative number (e.g. '-3 yetişkin', '-2 adults'), output the negative value AS-IS (e.g. -3) — do NOT silently convert it to its positive/absolute value. A downstream system rejects negative counts and warns the user; it needs to see the real negative number to do that.",
                    "childCount": "integer — number of travelers aged 3 to 12 (inclusive). If the user says 'child'/'çocuk' but later gives an age of 0-2, that person belongs in infantCount instead, not here. Same negative-number preservation rule as adultCount applies.",
                    "childAges": "array of integers, ages 3-12, one per child",
                    "infantCount": "integer — number of travelers aged 0 to 2 (inclusive), i.e. infants/babies ('bebek'). If the user says 'infant'/'bebek' but later gives an age of 3-12, that person belongs in childCount instead, not here. Same negative-number preservation rule as adultCount applies.",
                    "infantAges": "array of integers, ages 0-2, one per infant",
                    "currency": "currency (TRY, EUR, USD, GBP)",
                    "roomCount": "integer. Same negative-number preservation rule as adultCount applies.",
                    "nationality": "nationality code (e.g. TR)",
                    "maxPrice": "double. Extract the maximum price/budget limit if the user specifies one (e.g. '8 bin liradan düşükleri göster' -> 8000.0, 'maksimum 10000 TL' -> 10000.0)",
                    "minPrice": "double. Extract the minimum price if specified (e.g. '5000 TL üzeri' -> 5000.0)",
                    "minStars": "integer. Extract the minimum star rating or specified stars (e.g. 'sadece 5 yıldızlılar' -> 5, '4 yıldız ve üzeri' -> 4)",

                    // For FLIGHT_SEARCH:
                    "departureLocation": "departure location (e.g. Istanbul). CRITICAL: Do NOT fill this with general POI/amenity names (such as havalimanı, otogar, etc.) if no specific city/airport is mentioned.",
                    "arrivalLocation": "arrival location (e.g. Antalya). CRITICAL: Do NOT fill this with general POI/amenity names (such as havalimanı, otogar, etc.) if no specific city/airport is mentioned.",
                    "departureDate": "departure date in YYYY-MM-DD format. Same 'only if explicitly mentioned' rule as checkInDate applies.",
                    "returnDate": "return date in YYYY-MM-DD format. Same 'only if explicitly mentioned' rule as checkOutDate applies.",
                    "passengerCount": "integer. Same negative-number preservation rule as adultCount applies.",
                    "tripType": "ONE_WAY" or "ROUND_TRIP",
                    "currency": currency (TRY, EUR, USD, GBP)
                  }
                }
                """.formatted(todayStr);

        String awaitingFieldContext = "";
        if (awaitingField != null && !awaitingField.trim().isEmpty()) {
            boolean multipleFieldsAwaited = awaitingField.contains(",");
            awaitingFieldContext = String.format(
                    "\nThe assistant just asked the user for: [%s]. Interpret the user's reply primarily as an answer to %s, not as new unrelated criteria (e.g. dates), unless the message clearly indicates a topic change.",
                    awaitingField,
                    multipleFieldsAwaited ? "THESE fields together" : "THIS field");
        }

        // Eksik alanlar tek cümlede birlikte sorulabiliyor (örn. "yetişkin sayısı, çocuk
        // yaşları" veya "çocuk yaşları, bebek yaşları"), ama oturum bunu TEK bir düz string
        // olarak tutuyor — modelin hangi sayının hangi alana ait olduğunu akıl yürüterek
        // (zaten bilinen sayılar, alan tipi, verilen değer adedi gibi ipuçlarıyla) kendi
        // kendine ayırması gerekiyor; sabit kelime/kombinasyon listesi tutmuyoruz.
        String ageFieldInstruction = """
                IMPORTANT — splitting a reply across multiple awaited fields: when the field(s) named above
                include more than one item (comma-separated), a short reply like "5 6" must be split sensibly
                between them using real-world judgment and the current-counts context below — don't blindly dump
                every value into just one field:
                - A count field (adultCount, childCount, infantCount, passengerCount, roomCount) normally takes
                  exactly ONE number.
                - An ages field (childAges, infantAges) normally takes as many numbers as the already-known count
                  for that category — e.g. if childCount is already 2, two of the given numbers most plausibly go
                  into childAges as ages, not into an unrelated count field.
                - When an ages field and a count field are awaited together and the number of values given matches
                  the already-known count for the ages field, treat those values as the ages field's answer —
                  do NOT also copy them into the count field. If that leaves no value for the count field, leave
                  it unset/omitted rather than reusing one of the ages values for it. This takes priority over any
                  other general guidance about bare numbers answering a count-field question — that other guidance
                  applies only when the count field was the ONLY field awaited, not when it was awaited together
                  with an ages field.
                - When childAges and infantAges are awaited together, split the given ages between them by each
                  age's ACTUAL VALUE (0-2 -> infantAges, 3-12 -> childAges); each age must appear in exactly one
                  of the two lists, never both.
                - When only ONE field was awaited, put all the values the user gives into that single field
                  exactly as given, even if some values numerically look like they belong to a different bracket
                  (e.g. answering "bebek yaşları" with "2 and 3" -> infantAges: [2, 3]). Do NOT re-split or
                  reclassify them yourself in that single-field case — a downstream system automatically
                  reconciles ages into the correct infant (0-2) / child (3-12) / adult (13+) bucket afterward, and
                  needs to see the user's original, unmodified reply to explain the correction to the user.
                  Only use actual age value to decide the field when there is no specific awaited field context
                  at all (e.g. a fresh, unprompted message like "2 kişi, yaşları 2 ve 8").""";

        String prompt = """
                Extract travel criteria and identify user intent from the message below.
                Return the output strictly as a single JSON object matching the schema. Do not add any markdown blocks (like ```json), notes, or extra text.
                If some criteria fields are not found in the message, omit them or set them to null.

                CRITICAL LOKASYON / DESTİNASYON KURALI (LOCATION EXTRACTION RULE):
                - Eğer kullanıcı spesifik bir şehir/il/ilçe/ülke adı (örn: Antalya, Belek, Paris, İstanbul) belirtmediyse, 'locationOrHotelName', 'departureLocation' veya 'arrivalLocation' alanlarını KESİNLİKLE doldurma (null bırak veya boş string dön).
                - Genel mekan/ilgi noktası (POI) isimleri (örn: "lunapark", "havalimanı", "müze", "plaj", "merkez", "otogar", "istasyon", "sahil", vb.) şehir veya lokasyon adı DEĞİLDİR. Bu tür genel ifadeler kesinlikle 'locationOrHotelName', 'departureLocation' veya 'arrivalLocation' alanlarına yazılmamalıdır. Bunlar 'nearby_poi' veya 'search_keywords' parametresi olarak değerlendirilmelidir.

                LANGUAGE AND CHARACTER PRESERVATION HINT:
                The user message is in Turkish. It is critical to preserve Turkish characters ('ş', 'ğ', 'ı', 'ö', 'ü', 'ç', 'Ş', 'Ğ', 'İ', 'I', 'Ö', 'Ü', 'Ç') with high precision in all extracted place names, hotel names, cities, or any criteria fields (e.g., "Eskişehir", "Muğla", "Beşiktaş", "Şişli", "Göcek"). Do not remove, simplify, normalize, or distort these characters in the JSON output.

                IMPORTANT: Only extract a date field (checkInDate, checkOutDate, departureDate, returnDate) if the
                user's message actually contains a date, weekday, or relative time expression. Vague requests like
                "what's the nearest available date", "en yakın tarih ne var", "suggest a date", or "hangi tarihler
                uygun" are QUESTIONS about availability, not date values — do NOT fill in today's date or any other
                date for these; leave the date fields null/omitted and set intent to UNKNOWN if nothing else in the
                message provides new search criteria.

                %s

                IMPORTANT — mid-search guest count adjustments: if there is an active search intent (see Active
                Intent Context below), treat any mention of a change in the number of travelers — even without
                explicit hotel/flight/location keywords (e.g. "1 tane daha bebek gelicek", "bir çocuk daha
                ekleyelim", "eşim de gelecek", "aslında 3 kişi olacağız") — as an update to that active search's
                guest counts, NOT as an unrelated/out-of-scope/unknown message. In that case set intent to the
                active intent and fill only the specific guest-count field(s) the message concerns (adultCount/
                childCount/childAges/infantCount/infantAges), leaving every other field null/omitted.%s

                Today's Date: %s
                Active Intent Context: %s%s
                User Message: "%s"

                Expected JSON Schema:
                %s

                Response (JSON only):"""
                .formatted(ageFieldInstruction, currentCountsContext, todayStr, activeIntentContext, awaitingFieldContext, message, schemaDescription);

        String response = geminiExtractionClient.complete(prompt);

        if (response == null || response.trim().isEmpty()) {
            throw new RuntimeException("Received empty response from Gemini Lite client.");
        }
        if (response.trim().startsWith("[MOCK]")) {
            throw new RuntimeException("Gemini Lite API is running in MOCK mode: " + response);
        }
        if (response.contains("Gemini service could not be reached")) {
            throw new RuntimeException("Failed to connect to Gemini Lite service: " + response);
        }

        try {
            String jsonText = response.trim();
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.substring(jsonText.indexOf("\n") + 1);
            }
            if (jsonText.endsWith("```")) {
                jsonText = jsonText.substring(0, jsonText.lastIndexOf("```"));
            }
            jsonText = jsonText.trim();

            ExtractionResult result = objectMapper.readValue(jsonText, ExtractionResult.class);
            if (result == null || result.getIntent() == null) {
                throw new RuntimeException("Parsed extraction result is null or missing intent.");
            }
            if (result.getCriteria() != null) {
                // Safeguard: nullify general POIs extracted as locations
                String loc = result.getCriteria().getLocationOrHotelName();
                if (loc != null && isGeneralPoi(loc.toLowerCase(java.util.Locale.forLanguageTag("tr-TR")))) {
                    result.getCriteria().setLocationOrHotelName(null);
                }
                String dep = result.getCriteria().getDepartureLocation();
                if (dep != null && isGeneralPoi(dep.toLowerCase(java.util.Locale.forLanguageTag("tr-TR")))) {
                    result.getCriteria().setDepartureLocation(null);
                }
                String arr = result.getCriteria().getArrivalLocation();
                if (arr != null && isGeneralPoi(arr.toLowerCase(java.util.Locale.forLanguageTag("tr-TR")))) {
                    result.getCriteria().setArrivalLocation(null);
                }

                result.getCriteria().setSearchType(result.getIntent());
                log.info("[ExtractionAgent] Parsed message: \"{}\"", message);
                log.info("[ExtractionAgent] -> checkInDate: {}", result.getCriteria().getCheckInDate());
                log.info("[ExtractionAgent] -> checkOutDate: {}", result.getCriteria().getCheckOutDate());
                log.info("[ExtractionAgent] -> childAges: {}", result.getCriteria().getChildAges());
            }
            log.debug("[ExtractionAgent] Extraction successful: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[ExtractionAgent] Failed to parse JSON response: {}", response, e);
            throw new RuntimeException("JSON parsing failure in ExtractionAgent: " + e.getMessage(), e);
        }
    }

    private boolean isGeneralPoi(String text) {
        if (text == null) return false;
        java.util.List<String> pois = java.util.List.of(
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
}
