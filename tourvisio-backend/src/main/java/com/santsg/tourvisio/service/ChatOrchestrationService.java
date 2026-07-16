package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.CriteriaMissingFieldsService;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.chat.SearchCriteriaExtractor;
import com.santsg.tourvisio.agent.ExtractionAgent;
import com.santsg.tourvisio.agent.ExtractionResult;
import com.santsg.tourvisio.agent.ResponseAgent;
import com.santsg.tourvisio.dto.ChatRequest;
import com.santsg.tourvisio.dto.ChatResponse;
import com.santsg.tourvisio.dto.ChatSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Chatbot orkestrasyonunu yöneten merkezi servis.
 */
@Service
public class ChatOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrationService.class);

    private final IntentDetectionService intentDetectionService;
    private final ChatSessionManager chatSessionManager;
    private final ChatSessionStore sessionStore;
    private final SearchCriteriaExtractor extractor;
    private final CriteriaMissingFieldsService missingFieldsService;
    private final ExtractionAgent extractionAgent;
    private final ResponseAgent responseAgent;
    private final HotelSearchService hotelSearchService;
    private final FlightSearchService flightSearchService;

    public ChatOrchestrationService(
            IntentDetectionService intentDetectionService,
            ChatSessionManager chatSessionManager,
            ChatSessionStore sessionStore,
            SearchCriteriaExtractor extractor,
            CriteriaMissingFieldsService missingFieldsService,
            ExtractionAgent extractionAgent,
            ResponseAgent responseAgent,
            HotelSearchService hotelSearchService,
            FlightSearchService flightSearchService) {

        this.intentDetectionService = intentDetectionService;
        this.chatSessionManager = chatSessionManager;
        this.sessionStore = sessionStore;
        this.extractor = extractor;
        this.missingFieldsService = missingFieldsService;
        this.extractionAgent = extractionAgent;
        this.responseAgent = responseAgent;
        this.hotelSearchService = hotelSearchService;
        this.flightSearchService = flightSearchService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    @org.springframework.transaction.annotation.Transactional
    public ChatResponse orchestrate(ChatRequest request) {
        return orchestrate(request, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public ChatResponse orchestrate(ChatRequest request, Long userId) {
        // 1. Session yönetimi
        String sessionId = resolveSessionId(request.getSessionId());

        ChatSessionManager.SessionState existingState = chatSessionManager.getSessionState(sessionId);
        if (existingState != null && existingState.getUserId() != null && !existingState.getUserId().equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Access denied to session: " + sessionId);
        }

        ChatSessionManager.SessionState sessionState = chatSessionManager.getOrCreateSession(sessionId, userId);

        log.debug("[Orchestration] sessionId={}", sessionId);

        // Record User Message
        String userMessage = request.getMessage();
        if (userMessage != null && !userMessage.isBlank()) {
            sessionState.getMessages()
                    .add(new ChatSessionManager.MessageHistoryItem("user", userMessage, java.time.Instant.now(), null));
            sessionState.setLastMessageTimestamp(java.time.Instant.now());
            if ("New Chat Session".equals(sessionState.getTitle())) {
                String title = userMessage;
                if (title.length() > 45) {
                    title = title.substring(0, 42) + "...";
                }
                sessionState.setTitle(title);
            }
        }

        ChatResponse response = doOrchestrate(request, sessionId, sessionState);

        // Record Bot Response
        if (response != null && response.getReply() != null) {
            sessionState.getMessages().add(
                    new ChatSessionManager.MessageHistoryItem("bot", response.getReply(), java.time.Instant.now(), response.getResults()));
            sessionState.setLastMessageTimestamp(java.time.Instant.now());
        }

        // Save session state to database
        chatSessionManager.saveSession(sessionState);

        return response;
    }

    private ChatResponse doOrchestrate(ChatRequest request, String sessionId,
            ChatSessionManager.SessionState sessionState) {
        // Retrieve search criteria
        SearchCriteria existingCriteria = sessionStore.getOrCreate(sessionId);

        // Update multi-language preferences from request if present
        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            existingCriteria.setCountry(request.getCountry());
        }
        if (request.getCurrencySymbol() != null && !request.getCurrencySymbol().isBlank()) {
            existingCriteria.setCurrency(request.getCurrencySymbol());
        }
        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            existingCriteria.setPreferredLanguage(request.getCountry());
        }
        sessionStore.save(sessionId, existingCriteria);

        // 2. Oturum sonlandırılmışsa erken çık
        if ("TERMINATED".equals(sessionState.getChatStatus())) {
            return ChatResponse.builder()
                    .reply(responseAgent.decline(existingCriteria, true))
                    .sessionId(sessionId)
                    .searchType("OUT_OF_SCOPE")
                    .missingFields(List.of())
                    .chatStatus("TERMINATED")
                    .build();
        }

        String userMessage = request.getMessage();

        // 2.5 AWAITING_CONFIRM mode check
        if ("AWAITING_CONFIRM".equals(sessionState.getMode()) && sessionState.getLastShownResults() != null) {
            Object matchedItem = matchSelectedItem(userMessage, sessionState.getLastShownResults());
            if (matchedItem != null) {
                // Selection recognized!
                sessionState.setMode("BOOKING");
                sessionState.setSelectedItem(matchedItem);
                
                String confirmReply = responseAgent.confirmSelection(matchedItem, existingCriteria);
                return ChatResponse.builder()
                        .reply(confirmReply)
                        .sessionId(sessionId)
                        .searchType(existingCriteria.getSearchType())
                        .missingFields(java.util.List.of())
                        .chatStatus("BOOKING")
                        .selectedItem(matchedItem)
                        .build();
            } else {
                // Not a match, reset back to GATHERING
                sessionState.setMode("GATHERING");
                sessionState.setLastShownResults(null);
            }
        }

        // 3. Aktif arama session'ı var mı?
        boolean hasActiveSearch = existingCriteria.getSearchType() != null;

        // 4. Intent & Kriter Çıkarma (Extraction)
        String intent = null;
        SearchCriteria incoming = null;
        ExtractionResult extractionResult = null;

        // Try extracting via AI Agent first
        try {
            String currentIntent = hasActiveSearch ? existingCriteria.getSearchType() : null;
            extractionResult = extractionAgent.extract(userMessage, currentIntent);
        } catch (Exception e) {
            log.warn("[Orchestration] ExtractionAgent failed or mocked, falling back to rule-based: {}",
                    e.getMessage());
        }

        if (extractionResult != null) {
            // Happy path: AI extracted intent and criteria
            intent = hasActiveSearch ? existingCriteria.getSearchType() : extractionResult.getIntent();
            incoming = extractionResult.getCriteria();
        } else {
            // Fallback path: Orchestrator-managed local rule-based pipeline
            if (hasActiveSearch) {
                intent = existingCriteria.getSearchType();
            } else {
                intent = intentDetectionService.detectIntent(userMessage);
            }
            incoming = extractor.extract(userMessage, intent);
        }

        // Handle OUT_OF_SCOPE and UNKNOWN immediately if this is a new search session
        if (!hasActiveSearch) {
            if ("OUT_OF_SCOPE".equals(intent)) {
                sessionState.incrementOutOfScopeCount();
                String chatStatus = sessionState.getChatStatus();
                return ChatResponse.builder()
                        .reply(responseAgent.decline(existingCriteria, "TERMINATED".equals(chatStatus)))
                        .sessionId(sessionId)
                        .searchType("OUT_OF_SCOPE")
                        .missingFields(List.of())
                        .chatStatus(chatStatus)
                        .build();
            }

            if ("UNKNOWN".equals(intent)) {
                return ChatResponse.builder()
                        .reply(responseAgent.clarify(existingCriteria))
                        .sessionId(sessionId)
                        .searchType("UNKNOWN")
                        .missingFields(List.of())
                        .chatStatus("ACTIVE")
                        .build();
            }
        }

        // Conversational adjustment based on lastRequestedField
        String lastField = sessionState.getLastRequestedField();
        if (lastField != null && userMessage != null && !userMessage.isBlank()) {
            adjustIncomingCriteria(incoming, lastField, userMessage);
            sessionState.setLastRequestedField(null);
        }

        // 6. Yeni kriterler önceki session kriterleri üzerine birleştir
        existingCriteria.mergeWith(incoming);
        sessionStore.save(sessionId, existingCriteria);

        log.debug("[Orchestration] Birleştirilmiş kriterler: {}", existingCriteria);

        // 7. Eksik alan kontrolü
        List<String> missingFields = missingFieldsService.getMissingFields(existingCriteria);

        if (!missingFields.isEmpty()) {
            sessionState.setLastRequestedField(missingFields.get(0));
            String replyText = responseAgent.askMissing(missingFields, existingCriteria);
            return ChatResponse.builder()
                    .reply(replyText)
                    .sessionId(sessionId)
                    .searchType(intent)
                    .missingFields(missingFields)
                    .chatStatus("ACTIVE")
                    .build();
        }

        // 8. Tüm bilgiler tamam → arama servisine yönlendir
        return readyToSearchResponse(sessionId, intent, existingCriteria);
    }

    private void adjustIncomingCriteria(SearchCriteria incoming, String lastField, String message) {
        if (incoming == null || lastField == null || message == null || message.isBlank()) {
            return;
        }

        switch (lastField) {
            case "konum veya otel adı":
                if (incoming.getLocationOrHotelName() == null) {
                    incoming.setLocationOrHotelName(extractor.parseLocation(message, false));
                }
                break;

            case "kalkış noktası":
                if (incoming.getDepartureLocation() == null) {
                    incoming.setDepartureLocation(extractor.parseLocation(message, true));
                }
                break;

            case "varış noktası":
                if (incoming.getArrivalLocation() == null) {
                    incoming.setArrivalLocation(extractor.parseLocation(message, true));
                }
                break;

            case "giriş tarihi":
                if (incoming.getCheckInDate() == null) {
                    java.time.LocalDate d = extractor.parseSingleDate(message);
                    if (d == null && incoming.getCheckOutDate() != null) {
                        d = incoming.getCheckOutDate();
                        incoming.setCheckOutDate(null);
                    }
                    incoming.setCheckInDate(d);
                }
                break;

            case "çıkış tarihi":
                if (incoming.getCheckOutDate() == null) {
                    java.time.LocalDate d = extractor.parseSingleDate(message);
                    if (d == null && incoming.getCheckInDate() != null) {
                        d = incoming.getCheckInDate();
                    }
                    incoming.setCheckOutDate(d);
                }
                incoming.setCheckInDate(null);
                break;

            case "gidiş tarihi":
                if (incoming.getDepartureDate() == null) {
                    java.time.LocalDate d = extractor.parseSingleDate(message);
                    if (d == null && incoming.getReturnDate() != null) {
                        d = incoming.getReturnDate();
                        incoming.setReturnDate(null);
                    }
                    incoming.setDepartureDate(d);
                }
                break;

            case "dönüş tarihi":
                if (incoming.getReturnDate() == null) {
                    java.time.LocalDate d = extractor.parseSingleDate(message);
                    if (d == null && incoming.getDepartureDate() != null) {
                        d = incoming.getDepartureDate();
                    }
                    incoming.setReturnDate(d);
                }
                incoming.setDepartureDate(null);
                break;

            case "yetişkin sayısı":
                if (incoming.getAdultCount() == null) {
                    incoming.setAdultCount(parseInteger(message));
                }
                break;

            case "yolcu sayısı":
                if (incoming.getPassengerCount() == null) {
                    incoming.setPassengerCount(parseInteger(message));
                }
                break;

            case "oda sayısı":
                if (incoming.getRoomCount() == null || incoming.getRoomCount() == 1) {
                    Integer rooms = parseInteger(message);
                    if (rooms != null) {
                        incoming.setRoomCount(rooms);
                    }
                }
                break;

            case "çocuk sayısı":
                if (incoming.getChildCount() == null || incoming.getChildCount() == 0) {
                    Integer children = parseInteger(message);
                    if (children != null) {
                        incoming.setChildCount(children);
                    }
                }
                break;

            case "çocuk yaşları":
                if (incoming.getChildAges() == null || incoming.getChildAges().isEmpty()) {
                    incoming.setChildAges(parseIntegerList(message));
                }
                break;

            case "para birimi":
                if (incoming.getCurrency() == null) {
                    incoming.setCurrency(extractor.parseCurrency(message));
                }
                break;

            case "tek yön / gidiş-dönüş":
                if (incoming.getTripType() == null) {
                    incoming.setTripType(extractor.parseTripType(message));
                }
                break;
        }
    }

    private Integer parseInteger(String message) {
        if (message == null)
            return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return null;
    }

    private List<Integer> parseIntegerList(String message) {
        List<Integer> list = new java.util.ArrayList<>();
        if (message == null)
            return list;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(message);
        while (matcher.find()) {
            list.add(Integer.parseInt(matcher.group()));
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tüm kriterler tamamlandığında ilgili arama servisini çağırır.
     */
    private ChatResponse readyToSearchResponse(String sessionId,
            String intent,
            SearchCriteria criteria) {

        ChatSearchResponse searchResponse;
        if ("HOTEL_SEARCH".equals(intent)) {
            searchResponse = hotelSearchService.searchFromCriteria(criteria);
        } else if ("FLIGHT_SEARCH".equals(intent)) {
            searchResponse = flightSearchService.searchFromCriteria(criteria);
        } else {
            searchResponse = ChatSearchResponse.builder()
                    .reply("Arama türü tanımlanamadı.")
                    .searchType(intent)
                    .success(false)
                    .results(List.of())
                    .build();
        }

        String finalReply = searchResponse.getReply();

        // AI ile arama sonuçlarını özetleme
        if (searchResponse.isSuccess() && searchResponse.getResults() != null
                && !searchResponse.getResults().isEmpty()) {
                
            // Set AWAITING_CONFIRM mode
            ChatSessionManager.SessionState sessionState = chatSessionManager.getSessionState(sessionId);
            if (sessionState != null) {
                sessionState.setMode("AWAITING_CONFIRM");
                sessionState.setLastShownResults(searchResponse.getResults());
            }

            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String resultsJson = mapper.writeValueAsString(
                        searchResponse.getResults().subList(0, Math.min(5, searchResponse.getResults().size())));

                finalReply = responseAgent.summarize(intent, resultsJson, searchResponse.getReply(), criteria);
            } catch (Exception e) {
                log.warn("[Orchestration] Arama sonuçları AI ile özetlenemedi, varsayılan cevaba dönülüyor: {}",
                        e.getMessage());
                finalReply = responseAgent.summarize(intent, "[]", searchResponse.getReply(), criteria);
            }
        } else {
            finalReply = responseAgent.summarize(intent, "[]", searchResponse.getReply(), criteria);
        }

        return ChatResponse.builder()
                .reply(finalReply)
                .sessionId(sessionId)
                .searchType(intent)
                .missingFields(List.of())
                .chatStatus("ACTIVE")
                .success(searchResponse.isSuccess())
                .results(searchResponse.getResults())
                .build();
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    private Object matchSelectedItem(String userMessage, java.util.List<?> lastResults) {
        if (userMessage == null || userMessage.isBlank() || lastResults == null) {
            return null;
        }
        
        String cleanUserMsg = userMessage.toLowerCase()
            .replace("hoteli", "")
            .replace("oteli", "")
            .replace("hotel", "")
            .replace("otel", "")
            .trim();
            
        for (Object item : lastResults) {
            String itemName = "";
            if (item instanceof com.santsg.tourvisio.dto.HotelSearchResponseItem) {
                itemName = ((com.santsg.tourvisio.dto.HotelSearchResponseItem) item).getName();
            } else if (item instanceof com.santsg.tourvisio.dto.FlightSearchResponseItem) {
                itemName = ((com.santsg.tourvisio.dto.FlightSearchResponseItem) item).getAirline();
            }
            
            if (itemName != null && !itemName.isBlank()) {
                String cleanItemName = itemName.toLowerCase();
                if (userMessage.toLowerCase().contains(cleanItemName) || cleanItemName.contains(cleanUserMsg)) {
                    return item;
                }
            }
        }
        return null;
    }
}
