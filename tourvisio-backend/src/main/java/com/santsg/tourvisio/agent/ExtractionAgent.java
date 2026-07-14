package com.santsg.tourvisio.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.client.GeminiExtractionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ExtractionAgent {

    private static final Logger log = LoggerFactory.getLogger(ExtractionAgent.class);

    private final GeminiExtractionClient geminiExtractionClient;
    private final ObjectMapper objectMapper;

    public ExtractionAgent(GeminiExtractionClient geminiExtractionClient) {
        this.geminiExtractionClient = geminiExtractionClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Extracts intent and criteria from the user message.
     *
     * @param message       The user message.
     * @param currentIntent The current search type if in an active session, otherwise null.
     * @return The extraction result.
     * @throws RuntimeException on API failure, mock responses, or JSON parsing failures.
     */
    public ExtractionResult extract(String message, String currentIntent) {
        if (message == null || message.trim().isEmpty()) {
            SearchCriteria emptyCriteria = new SearchCriteria();
            return new ExtractionResult("UNKNOWN", emptyCriteria);
        }

        String todayStr = LocalDate.now().toString();
        String activeIntentContext = (currentIntent != null)
                ? String.format("The current active search intent is %s. Keep this intent unless the user explicitly switches the travel context.", currentIntent)
                : "No active search intent exists yet. Determine it from the message.";

        String schemaDescription = """
                {
                  "intent": "Determine the user's intent. Must be one of: HOTEL_SEARCH, FLIGHT_SEARCH, UNKNOWN, OUT_OF_SCOPE",
                  "criteria": {
                    // For HOTEL_SEARCH:
                    "locationOrHotelName": "city or hotel name (e.g. Antalya)",
                    "checkInDate": "check-in date in YYYY-MM-DD format. Today's date is %s. If only month/day (e.g. 15 July) is specified, use the correct year based on today's date.",
                    "checkOutDate": "check-out date in YYYY-MM-DD format. If night count is given, calculate check-out by adding it to check-in.",
                    "adultCount": integer,
                    "childCount": integer,
                    "childAges": array of integers,
                    "currency": currency (TRY, EUR, USD, GBP),
                    "roomCount": integer,
                    "nationality": nationality code (e.g. TR)

                    // For FLIGHT_SEARCH:
                    "departureLocation": "departure location (e.g. Istanbul)",
                    "arrivalLocation": "arrival location (e.g. Antalya)",
                    "departureDate": "departure date in YYYY-MM-DD format.",
                    "returnDate": "return date in YYYY-MM-DD format.",
                    "passengerCount": integer,
                    "tripType": "ONE_WAY" or "ROUND_TRIP",
                    "currency": currency (TRY, EUR, USD, GBP)
                  }
                }
                """.formatted(todayStr);

        String prompt = """
                Extract travel criteria and identify user intent from the message below.
                Return the output strictly as a single JSON object matching the schema. Do not add any markdown blocks (like ```json), notes, or extra text.
                If some criteria fields are not found in the message, omit them or set them to null.

                Today's Date: %s
                Active Intent Context: %s
                User Message: "%s"

                Expected JSON Schema:
                %s

                Response (JSON only):"""
                .formatted(todayStr, activeIntentContext, message, schemaDescription);

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
            }
            log.debug("[ExtractionAgent] Extraction successful: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[ExtractionAgent] Failed to parse JSON response: {}", response, e);
            throw new RuntimeException("JSON parsing failure in ExtractionAgent: " + e.getMessage(), e);
        }
    }
}
