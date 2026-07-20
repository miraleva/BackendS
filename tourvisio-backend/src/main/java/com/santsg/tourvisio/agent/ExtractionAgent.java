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
            currentCountsContext = String.format(
                    "%nCurrent already-known traveler counts for this search: adults=%s, children=%s, infants=%s. "
                            + "If the user's message describes an INCREMENTAL change relative to these (e.g. \"one more baby is coming\"/\"1 tane daha bebek gelicek\", "
                            + "\"add a child\"/\"bir çocuk daha ekleyelim\", \"my wife is coming too\"), compute and output the NEW ABSOLUTE total for that field "
                            + "(current value + the change), not just the delta. If the message states an absolute total instead (e.g. \"we'll be 3 adults\"/\"3 yetişkin olacağız\"), "
                            + "output that absolute number directly.",
                    existingCriteria.getAdultCount(), existingCriteria.getChildCount(), existingCriteria.getInfantCount());
        }

        String schemaDescription = """
                {
                  "intent": "Determine the user's intent. Must be one of: HOTEL_SEARCH, FLIGHT_SEARCH, UNKNOWN, OUT_OF_SCOPE",
                  "criteria": {
                    // For HOTEL_SEARCH:
                    "locationOrHotelName": "city or hotel name (e.g. Antalya)",
                    "checkInDate": "check-in date in YYYY-MM-DD format, ONLY if the user's message contains an actual date reference (a specific date, weekday, or relative expression like 'tomorrow'/'yarın'/'next week'). Today's date is %s, used only to resolve relative/partial dates. Handle multiple formats robustly (e.g. '13.6.26' -> '2026-06-13', '13/04/2027', '12-08-2026'). If only month/day (e.g. 15 July, haziran 13) is specified, resolve to the nearest future occurrence using today's date. NEVER output today's date as checkInDate just because no date was mentioned — leave it null/omitted instead.",
                    "checkOutDate": "check-out date in YYYY-MM-DD format. If night count is given, calculate check-out by adding it to check-in. Same 'only if explicitly mentioned' rule as checkInDate applies.",
                    "adultCount": integer,
                    "childCount": "integer — number of travelers aged 3 to 12 (inclusive). If the user says 'child'/'çocuk' but later gives an age of 0-2, that person belongs in infantCount instead, not here.",
                    "childAges": "array of integers, ages 3-12, one per child",
                    "infantCount": "integer — number of travelers aged 0 to 2 (inclusive), i.e. infants/babies ('bebek'). If the user says 'infant'/'bebek' but later gives an age of 3-12, that person belongs in childCount instead, not here.",
                    "infantAges": "array of integers, ages 0-2, one per infant",
                    "currency": currency (TRY, EUR, USD, GBP),
                    "roomCount": integer,
                    "nationality": nationality code (e.g. TR)

                    // For FLIGHT_SEARCH:
                    "departureLocation": "departure location (e.g. Istanbul)",
                    "arrivalLocation": "arrival location (e.g. Antalya)",
                    "departureDate": "departure date in YYYY-MM-DD format. Same 'only if explicitly mentioned' rule as checkInDate applies.",
                    "returnDate": "return date in YYYY-MM-DD format. Same 'only if explicitly mentioned' rule as checkOutDate applies.",
                    "passengerCount": integer,
                    "tripType": "ONE_WAY" or "ROUND_TRIP",
                    "currency": currency (TRY, EUR, USD, GBP)
                  }
                }
                """.formatted(todayStr);

        String awaitingFieldContext = "";
        if (awaitingField != null && !awaitingField.trim().isEmpty()) {
            awaitingFieldContext = String.format("\nThe assistant just asked the user for: [%s]. Interpret the user's reply primarily as an answer to THIS field, not as new unrelated criteria (e.g. dates), unless the message clearly indicates a topic change.", awaitingField);
        }

        // "çocuk yaşları" ve "bebek yaşları" AYNI ANDA soruluyorsa (ikisi de eksikse),
        // tek bir alan yok — bare "2 6" gibi bir cevapta hangi sayının hangi kategoriye
        // ait olduğunu tek bir alana yazma talimatıyla modele bildiremeyiz; bu durumda
        // model her iki listeye de AYNI sayıları KOPYALIYORDU (çocuk+bebek sayısını
        // ikiye katlayan bir hataya yol açıyordu). Bu özel durumda talimatı değiştirip
        // yaşları gerçek değerlerine göre ayırmasını istiyoruz.
        boolean bothChildAndInfantAgesAwaited = awaitingField != null
                && awaitingField.contains("çocuk yaş") && awaitingField.contains("bebek yaş");
        String ageFieldInstruction;
        if (bothChildAndInfantAgesAwaited) {
            ageFieldInstruction = """
                    IMPORTANT — infant/child age fields: the assistant just asked for BOTH "çocuk yaşları" AND
                    "bebek yaşları" together, so there is no single field to dump all ages into. Split the ages
                    the user gives between childAges and infantAges by each age's ACTUAL VALUE: 0-2 -> infantAges,
                    3-12 -> childAges. Each given age must appear in EXACTLY ONE of the two lists — never put the
                    same age (or the full set of ages) into both lists.""";
        } else {
            ageFieldInstruction = """
                    IMPORTANT — infant/child/adult age fields: when the user gives ages in direct response to a
                    question about a specific field (e.g. the assistant asked for "bebek yaşları"/"çocuk yaşları"),
                    put ALL the ages the user gives into THAT SAME field (infantAges or childAges) exactly as given,
                    even if some ages numerically look like they belong to a different bracket (e.g. answering
                    "bebek yaşları" with "2 and 3" -> infantAges: [2, 3]). Do NOT re-split or reclassify the ages
                    yourself — a downstream system automatically reconciles ages into the correct infant (0-2) /
                    child (3-12) / adult (13+) bucket afterward, and needs to see the user's original, unmodified
                    categorization to explain the correction to the user. Only use actual age to decide the field
                    when there is no specific awaited field context (e.g. a fresh, unprompted message like "2 kişi,
                    yaşları 2 ve 8").""";
        }

        String prompt = """
                Extract travel criteria and identify user intent from the message below.
                Return the output strictly as a single JSON object matching the schema. Do not add any markdown blocks (like ```json), notes, or extra text.
                If some criteria fields are not found in the message, omit them or set them to null.

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
}
