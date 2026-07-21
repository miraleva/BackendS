package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.CriteriaMissingFieldsService;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.chat.SearchCriteriaExtractor;
import com.santsg.tourvisio.chat.SearchCriteriaValidator;
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
import java.util.Locale;
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
    private final SearchCriteriaValidator criteriaValidator;
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
            SearchCriteriaValidator criteriaValidator,
            ExtractionAgent extractionAgent,
            ResponseAgent responseAgent,
            HotelSearchService hotelSearchService,
            FlightSearchService flightSearchService) {

        this.intentDetectionService = intentDetectionService;
        this.chatSessionManager = chatSessionManager;
        this.sessionStore = sessionStore;
        this.extractor = extractor;
        this.missingFieldsService = missingFieldsService;
        this.criteriaValidator = criteriaValidator;
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
        // Dil tercihi: önce bu mesajın gerçek dilini algılamayı dene (kullanıcı
        // sohbet ortasında dil değiştirebilir). Net bir sinyal yoksa (ör. "2",
        // bir tarih, ya da ilk mesaj boşsa) hesabın ülke ayarını varsayılan olarak kullan.
        String detectedLanguage = detectLanguageFromMessage(request.getMessage());
        if (detectedLanguage != null) {
            existingCriteria.setPreferredLanguage(detectedLanguage);
        } else if (existingCriteria.getPreferredLanguage() == null
                && request.getCountry() != null && !request.getCountry().isBlank()) {
            existingCriteria.setPreferredLanguage(request.getCountry());
        }
        sessionStore.save(sessionId, existingCriteria);

        String userMessage = request.getMessage();

        // 2. Oturum sonlandırılmışsa erken çık
        if ("TERMINATED".equals(sessionState.getChatStatus())) {
            return ChatResponse.builder()
                    .reply(responseAgent.decline(existingCriteria, true, userMessage))
                    .sessionId(sessionId)
                    .searchType("OUT_OF_SCOPE")
                    .missingFields(List.of())
                    .chatStatus("TERMINATED")
                    .build();
        }

        // 2.5 AWAITING_CONFIRM mode check
        if ("AWAITING_CONFIRM".equals(sessionState.getMode()) && sessionState.getLastShownResults() != null) {
            Object matchedItem = matchSelectedItem(userMessage, sessionState.getLastShownResults());
            if (matchedItem != null) {
                // Selection recognized!
                sessionState.setMode("BOOKING");
                sessionState.setSelectedItem(matchedItem);
                
                String confirmReply = responseAgent.confirmSelection(matchedItem, existingCriteria, userMessage);
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

        // 3.5 Pagination (More Results) Check
        if ("AWAITING_CONFIRM".equals(sessionState.getMode()) && 
            sessionState.getAllSearchResults() != null && !sessionState.getAllSearchResults().isEmpty()) {
            
            String lowerMsg = userMessage.toLowerCase(Locale.ROOT);
            boolean isMoreRequest = lowerMsg.contains("başka seçenek") || lowerMsg.contains("başka otel") || lowerMsg.contains("başka uçuş")
                    || lowerMsg.contains("başka var mı") || lowerMsg.contains("diğer seçenek") || lowerMsg.contains("diğerlerini")
                    || lowerMsg.contains("daha fazla") || lowerMsg.contains("show more") || lowerMsg.contains("more results") 
                    || lowerMsg.contains("other options") || lowerMsg.contains("more options");
                    
            if (isMoreRequest) {
                return paginateResults(sessionId, sessionState, existingCriteria, userMessage);
            }
        }

        // Try extracting via AI Agent first
        try {
            String currentIntent = hasActiveSearch ? existingCriteria.getSearchType() : null;
            extractionResult = extractionAgent.extract(userMessage, currentIntent, sessionState.getLastRequestedField(),
                    hasActiveSearch ? existingCriteria : null);
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
            incoming = extractor.extract(userMessage, intent, sessionState.getLastRequestedField());
        }

        // Handle OUT_OF_SCOPE and UNKNOWN immediately if this is a new search session
        if (!hasActiveSearch) {
            if ("OUT_OF_SCOPE".equals(intent)) {
                sessionState.incrementOutOfScopeCount();
                String chatStatus = sessionState.getChatStatus();
                return ChatResponse.builder()
                        .reply(responseAgent.decline(existingCriteria, "TERMINATED".equals(chatStatus), userMessage))
                        .sessionId(sessionId)
                        .searchType("OUT_OF_SCOPE")
                        .missingFields(List.of())
                        .chatStatus(chatStatus)
                        .build();
            }

            if ("UNKNOWN".equals(intent)) {
                log.info("[Orchestration] UNKNOWN intent. sessionId: {}, messagesSize: {}", sessionId, (sessionState != null ? sessionState.getMessages().size() : "null"));
                if (sessionState != null && sessionState.getMessages().size() <= 1) {
                    return ChatResponse.builder()
                            .reply(responseAgent.welcome(userMessage))
                            .sessionId(sessionId)
                            .searchType("UNKNOWN")
                            .missingFields(List.of())
                            .chatStatus("ACTIVE")
                            .build();
                }
                return ChatResponse.builder()
                        .reply(responseAgent.clarify(existingCriteria, userMessage))
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
        // Bebek/çocuk/yetişkin yaş yeniden-sınıflandırma notu varsa bir kez tüketilir
        // (aşağıdaki cevaplardan hangisi dönerse ona eklenir), tekrar gösterilmemesi
        // için criteria üzerinden temizlenir.
        String reclassificationNote = existingCriteria.getReclassificationNote();
        existingCriteria.setReclassificationNote(null);
        sessionStore.save(sessionId, existingCriteria);

        log.debug("[Orchestration] Birleştirilmiş kriterler: {}", existingCriteria);

        // 7. Validate criteria constraints (Date rules, Adult counts, etc.)
        SearchCriteriaValidator.ValidationResult validation = criteriaValidator.validate(existingCriteria);
        if (!validation.isValid()) {
            String errorType = validation.getErrorType();
            String replyText = "";
            if ("DATE_PAST".equals(errorType) || "DATE_MISMATCH".equals(errorType) || "DATE_TOO_FAR".equals(errorType)) {
                replyText = responseAgent.invalidDateRange(errorType, existingCriteria, userMessage);
            } else if ("NO_ADULTS".equals(errorType)) {
                replyText = responseAgent.noAdults(existingCriteria, userMessage);
            } else if ("NEGATIVE_COUNT".equals(errorType) || "TOO_MANY_GUESTS".equals(errorType)
                    || "TOO_MANY_PASSENGERS".equals(errorType) || "TOO_MANY_ROOMS".equals(errorType)) {
                replyText = responseAgent.invalidGuestCount(errorType, existingCriteria);
            }
            
            if (!replyText.isEmpty()) {
                return ChatResponse.builder()
                        .reply(replyText)
                        .sessionId(sessionId)
                        .searchType(intent)
                        .missingFields(List.of())
                        .chatStatus("ACTIVE")
                        .success(false)
                        .build();
            }
        }

        // 8. Eksik alan kontrolü
        List<String> missingFields = missingFieldsService.getMissingFields(existingCriteria);

        if (!missingFields.isEmpty()) {
            sessionState.setLastRequestedField(String.join(", ", missingFields));
            String replyText = responseAgent.askMissing(missingFields, existingCriteria, userMessage);
            replyText = prependNote(reclassificationNote, replyText);
            return ChatResponse.builder()
                    .reply(replyText)
                    .sessionId(sessionId)
                    .searchType(intent)
                    .missingFields(missingFields)
                    .chatStatus("ACTIVE")
                    .criteria(com.santsg.tourvisio.dto.ChatCriteriaSummary.from(existingCriteria))
                    .build();
        }

        // 8.5 Kullanıcı yeni bir kriter vermeden (ör. "en yakın tarih ne var") sadece
        // yakın tarih önerisi istiyor ve son arama zaten sonuçsuz kaldıysa, aynı
        // (başarısız olduğu zaten bilinen) tarihi tekrar aramadan doğrudan yakın
        // tarihlere bakıyoruz — bir gereksiz arama isteği daha az, daha hızlı cevap.
        if (sessionState.isLastSearchHadNoResults() && hasNoNewSearchCriteria(incoming)) {
            ChatSearchResponse nearbyResponse = null;
            if ("HOTEL_SEARCH".equals(intent)) {
                nearbyResponse = hotelSearchService.suggestNearbyDatesOnly(existingCriteria);
            } else if ("FLIGHT_SEARCH".equals(intent)) {
                nearbyResponse = flightSearchService.suggestNearbyDatesOnly(existingCriteria);
            }
            if (nearbyResponse != null) {
                return ChatResponse.builder()
                        .reply(prependNote(reclassificationNote, nearbyResponse.getReply()))
                        .sessionId(sessionId)
                        .searchType(intent)
                        .missingFields(List.of())
                        .chatStatus("ACTIVE")
                        .success(false)
                        .results(List.of())
                        .criteria(com.santsg.tourvisio.dto.ChatCriteriaSummary.from(existingCriteria))
                        .build();
            }
        }

        // 9. Tüm bilgiler tamam → arama servisine yönlendir
        return readyToSearchResponse(sessionId, intent, existingCriteria, userMessage, reclassificationNote);
    }

    /** Bebek/çocuk/yetişkin yeniden-sınıflandırma notu varsa cevabın başına ekler. */
    private String prependNote(String note, String reply) {
        if (note == null || note.isBlank()) {
            return reply;
        }
        return (reply == null || reply.isBlank()) ? note : note + "\n\n" + reply;
    }

    private void adjustIncomingCriteria(SearchCriteria incoming, String lastField, String message) {
        if (incoming == null || lastField == null || message == null || message.isBlank()) {
            return;
        }

        // "giriş tarihi, çıkış tarihi" gibi birden fazla tarih alanı aynı anda
        // soruluyorken, extractor.extract() (etiket-farkında, "giriş"/"çıkış"
        // gibi kelimeleri tanır) bu mesajdan zaten BİR tarihi doğru alana
        // atamış olabilir (örn. "giriş 28 temmuz" → checkInDate). Aşağıdaki
        // etiketsiz (bare) "parseSingleDate" yedek mantığı bunu bilmeden aynı
        // tarihi diğer alana da (çıkış) atayıp, üstüne doğru atanmış olanı
        // sıfırlayabiliyordu. Bu yüzden, etiketli çıkarım bu mesajdan zaten
        // bir tarih bulduysa, etiketsiz yedek mantığı hiç çalıştırmıyoruz.
        boolean hotelDateAlreadyResolvedByLabel = incoming.getCheckInDate() != null || incoming.getCheckOutDate() != null;
        boolean flightDateAlreadyResolvedByLabel = incoming.getDepartureDate() != null || incoming.getReturnDate() != null;

        String[] fields = lastField.split(",\\s*");
        for (String field : fields) {
            switch (field) {
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
                    if (incoming.getCheckInDate() == null && !hotelDateAlreadyResolvedByLabel) {
                        incoming.setCheckInDate(extractor.parseSingleDate(message));
                    }
                    break;

                case "çıkış tarihi":
                    if (incoming.getCheckOutDate() == null && !hotelDateAlreadyResolvedByLabel) {
                        incoming.setCheckOutDate(extractor.parseSingleDate(message));
                    }
                    break;

                case "gidiş tarihi":
                    if (incoming.getDepartureDate() == null && !flightDateAlreadyResolvedByLabel) {
                        incoming.setDepartureDate(extractor.parseSingleDate(message));
                    }
                    break;

                case "dönüş tarihi":
                    if (incoming.getReturnDate() == null && !flightDateAlreadyResolvedByLabel) {
                        incoming.setReturnDate(extractor.parseSingleDate(message));
                    }
                    break;

                case "yetişkin sayısı":
                    if (incoming.getAdultCount() == null) {
                        incoming.setAdultCount(parseCountWithLabel(message, ADULT_COUNT_LABEL_PATTERN));
                    }
                    break;

                case "yolcu sayısı":
                    if (incoming.getPassengerCount() == null) {
                        incoming.setPassengerCount(parseCountWithLabel(message, PASSENGER_COUNT_LABEL_PATTERN));
                    }
                    break;

                case "oda sayısı":
                    if (incoming.getRoomCount() == null || incoming.getRoomCount() == 1) {
                        Integer rooms = parseCountWithLabel(message, ROOM_COUNT_LABEL_PATTERN);
                        if (rooms != null) {
                            incoming.setRoomCount(rooms);
                        }
                    }
                    break;

                case "çocuk sayısı":
                    if (incoming.getChildCount() == null || incoming.getChildCount() == 0) {
                        Integer children = parseCountWithLabel(message, CHILD_COUNT_LABEL_PATTERN);
                        if (children != null) {
                            incoming.setChildCount(children);
                        }
                    }
                    break;

                case "çocuk yaşları":
                    if (incoming.getChildAges() == null || incoming.getChildAges().isEmpty()) {
                        incoming.setChildAges(parseChildAges(message));
                    }
                    break;

                case "bebek sayısı":
                    if (incoming.getInfantCount() == null || incoming.getInfantCount() == 0) {
                        Integer infants = parseCountWithLabel(message, INFANT_COUNT_LABEL_PATTERN);
                        if (infants != null) {
                            incoming.setInfantCount(infants);
                        }
                    }
                    break;

                case "bebek yaşları":
                    // Hangi listeye (infantAges/childAges) yazıldığı önemli değil —
                    // SearchCriteria.reconcileAgeBuckets() gerçek yaşa göre zaten
                    // doğru kovaya taşıyacak.
                    if (incoming.getInfantAges() == null || incoming.getInfantAges().isEmpty()) {
                        incoming.setInfantAges(parseChildAges(message));
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

    /**
     * Yetişkin/yolcu/oda/çocuk sayısı gibi alanlar başka bir alanla (ör. tarih)
     * aynı mesajda birlikte sorulduğunda, mesajdaki İLK sayıyı almak yanlış
     * sonuç verir (örn. "28 temmuz, 1 yetişkin, tek yön" → tarihteki "28"
     * yolcu sayısı sanılırdı, oysa gerçek sayı "1"dir, "yetişkin" kelimesinin
     * hemen önünde). Bu yüzden önce ilgili anahtar kelimenin hemen önündeki
     * sayıyı arar; bulamazsa ve mesaj tamamen sayılardan/ayraçlardan oluşuyorsa
     * (kullanıcı sadece "3" yazdıysa) o sayıyı kullanır.
     */
    private static final java.util.regex.Pattern ADULT_COUNT_LABEL_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,3})\\s*(?:yetişkin|yetiskin|adult|adults)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern PASSENGER_COUNT_LABEL_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,3})\\s*(?:yolcu|passenger|passengers|kişi|kisi|person|people|kişilik|kisilik|yetişkin|yetiskin|adult|adults)",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern ROOM_COUNT_LABEL_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,2})\\s*(?:oda|room|rooms)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern CHILD_COUNT_LABEL_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,2})\\s*(?:çocuk|cocuk|child|children|kids)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern INFANT_COUNT_LABEL_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,2})\\s*(?:bebek|infant|infants|baby|babies)", java.util.regex.Pattern.CASE_INSENSITIVE);

    private Integer parseCountWithLabel(String message, java.util.regex.Pattern labelPattern) {
        if (message == null) return null;

        java.util.regex.Matcher labelMatcher = labelPattern.matcher(message);
        if (labelMatcher.find()) {
            return Integer.parseInt(labelMatcher.group(1));
        }

        // Anahtar kelime bulunamadı; mesaj sadece sayılardan/ayraçlardan
        // oluşuyorsa (örn. kullanıcı doğrudan "3" yazdıysa) o sayıyı kullan.
        if (message.trim().matches("^[\\d\\s,.-]+$")) {
            return parseInteger(message);
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

    /**
     * Çocuk yaşları için "çıkış tarihi, çocuk yaşları" gibi birden fazla alanın
     * aynı mesajda birlikte sorulduğu durumlarda, mesajdaki HER sayıyı yaş
     * sanmak yanlış sonuç verir (örn. "3 ağustos, 5 yaşında" → tarih içindeki
     * "3" de yaş sanılıp [3, 5] çıkarılırdı, oysa tek çocuk yaşı 5'tir).
     * Bu yüzden önce sadece "yaş/yaşında/years old" gibi bir yaş belirtecinin
     * hemen öncesindeki sayı(ları) arar; hiç bulamazsa ve mesaj tamamen
     * sayılardan oluşuyorsa (kullanıcı sadece "5, 8" gibi yazdıysa) tüm
     * sayıları yaş kabul eder.
     */
    private static final java.util.regex.Pattern CHILD_AGE_CLAUSE_PATTERN = java.util.regex.Pattern.compile(
            "((?:\\d{1,2}\\s*(?:,|ve|and)?\\s*)+)(?:yaş\\w*|yasinda|yaslarinda|years?\\s*old|y/o)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private List<Integer> parseChildAges(String message) {
        List<Integer> ages = new java.util.ArrayList<>();
        if (message == null) return ages;

        java.util.regex.Matcher clauseMatcher = CHILD_AGE_CLAUSE_PATTERN.matcher(message);
        while (clauseMatcher.find()) {
            java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("\\d{1,2}").matcher(clauseMatcher.group(1));
            while (numMatcher.find()) {
                ages.add(Integer.parseInt(numMatcher.group()));
            }
        }
        if (!ages.isEmpty()) {
            return ages;
        }

        // Yaş belirteci bulunamadı; mesaj sadece sayılardan/ayraçlardan oluşuyorsa
        // (örn. kullanıcı doğrudan "5" ya da "5, 8" yazdıysa) tüm sayıları yaş kabul et.
        if (message.trim().matches("^[\\d\\s,.-]+$")) {
            return parseIntegerList(message);
        }
        return ages;
    }

    private static final java.util.Set<String> TURKISH_WORDS = java.util.Set.of(
            "otel", "otelde", "uçak", "ucak", "uçuş", "ucus", "istiyorum", "arıyorum", "ariyorum",
            "gidiş", "gidis", "dönüş", "donus", "yetişkin", "yetiskin", "çocuk", "cocuk",
            "rezervasyon", "merhaba", "selam", "lütfen", "lutfen", "tarih", "gece", "kişi", "kisi",
            "için", "icin", "istiyoruz", "gün", "gun", "var", "yok", "evet", "hayır", "hayir");

    private static final java.util.Set<String> ENGLISH_WORDS = java.util.Set.of(
            "hotel", "flight", "fly", "want", "looking", "for", "from", "please", "need", "book",
            "reservation", "adults", "children", "date", "hello", "hi", "the", "and", "night",
            "nights", "trip", "travel", "search", "yes", "no", "return", "departure",
            // Genel yapısal kelimeler — kısa/bozuk İngilizce cümlelerin (ör. "i searching
            // otel in antalya") içindeki tek bir yabancı ödünç kelime ("otel") yüzünden
            // yanlışlıkla o dile (Türkçe) sınıflandırılmasını önlemek için eklendi.
            "i", "in", "is", "am", "are", "to", "of", "my", "on", "at", "a", "an", "do", "does",
            "can", "will", "would", "have", "has", "with", "this", "that", "me", "you", "we", "us",
            "it", "searching", "find", "finding", "room", "rooms", "guest", "guests",
            "people", "person", "going", "like", "about", "help", "some", "any");

    private static final java.util.Set<String> GERMAN_WORDS = java.util.Set.of(
            "hallo", "guten", "ich", "möchte", "bitte", "danke", "hotel", "flug", "buchen",
            "erwachsene", "kinder", "ja", "nein", "für");

    private static final java.util.Set<String> RUSSIAN_WORDS = java.util.Set.of(
            "привет", "здравствуйте", "хочу", "пожалуйста", "отель", "билет", "рейс",
            "взрослых", "детей", "да", "нет", "для");

    /**
     * Kullanıcının bu mesajda hangi dili kullandığını basit bir sezgisel yöntemle
     * tahmin eder (Gemini/OpenAI anahtarı yoksa AI tabanlı tespit mümkün değil).
     * Net bir sinyal bulunamazsa null döner (çağıran taraf önceki tercihi korur).
     */
    private String detectLanguageFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.matches(".*[а-яА-ЯёЁ].*")) {
            return "Russian";
        }

        boolean hasUnambiguousTurkishChars = lower.chars().anyMatch(c -> "çğş".indexOf(c) >= 0);
        if (hasUnambiguousTurkishChars) {
            return "Turkish";
        }

        boolean hasUnambiguousGermanChars = lower.chars().anyMatch(c -> "äß".indexOf(c) >= 0);
        if (hasUnambiguousGermanChars) {
            return "German";
        }
        
        // ö and ü are shared between Turkish and German. We don't eagerly return here to avoid false positives.

        String[] tokens = lower.split("[^a-zçğıöşüäßа-яё0-9]+");
        int turkishHits = 0;
        int englishHits = 0;
        int germanHits = 0;
        int russianHits = 0;
        
        for (String token : tokens) {
            if (TURKISH_WORDS.contains(token)) turkishHits++;
            if (ENGLISH_WORDS.contains(token)) englishHits++;
            if (GERMAN_WORDS.contains(token)) germanHits++;
            if (RUSSIAN_WORDS.contains(token)) russianHits++;
        }

        boolean hasLoneDotlessI = englishHits == 0 && germanHits == 0 && lower.chars().anyMatch(c -> c == 'ı');
        if (hasLoneDotlessI) {
            turkishHits++;
        }
        
        boolean hasSharedUmlauts = lower.chars().anyMatch(c -> c == 'ö' || c == 'ü');
        if (hasSharedUmlauts) {
            if (germanHits > turkishHits) germanHits++;
            else turkishHits++;
        }

        int maxHits = Math.max(Math.max(turkishHits, englishHits), Math.max(germanHits, russianHits));
        
        if (maxHits == 0) return null;
        
        if (maxHits == turkishHits) return "Turkish";
        if (maxHits == germanHits) return "German";
        if (maxHits == russianHits) return "Russian";
        if (maxHits == englishHits) return "English";
        
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ChatResponse paginateResults(String sessionId, ChatSessionManager.SessionState sessionState,
            SearchCriteria criteria, String userMessage) {

        List<?> allResults = sessionState.getAllSearchResults();
        int totalSize = allResults.size();
        int newOffset = sessionState.getResultOffset() + 10;
        String intent = criteria != null ? criteria.getSearchType() : "UNKNOWN";

        if (newOffset >= totalSize) {
            String reply = responseAgent.noMoreResults(criteria, userMessage);
            return ChatResponse.builder()
                    .reply(reply)
                    .sessionId(sessionId)
                    .searchType(intent)
                    .missingFields(List.of())
                    .chatStatus("ACTIVE")
                    .success(false)
                    .results(sessionState.getLastShownResults())
                    .build();
        }

        // Slice new batch
        List<?> slicedResults = allResults.subList(newOffset, Math.min(newOffset + 10, totalSize));
        sessionState.setResultOffset(newOffset);
        sessionState.setLastShownResults(slicedResults);

        // Sonuçlar zaten kart olarak gösteriliyor — ayrıca metin özeti yazdırmıyoruz.
        String finalReply = "";

        return ChatResponse.builder()
                .reply(finalReply)
                .sessionId(sessionId)
                .searchType(intent)
                .missingFields(List.of())
                .chatStatus("ACTIVE")
                .success(true)
                .results(slicedResults)
                .build();
    }

    /**
     * Tüm kriterler tamamlandığında ilgili arama servisini çağırır.
     */
    private ChatResponse readyToSearchResponse(String sessionId,
            String intent,
            SearchCriteria criteria,
            String userMessage,
            String reclassificationNote) {

        log.info("[Orchestration] Executing Search to TourVisio API with Final Criteria: Location={}, CheckIn={}, CheckOut={}, Adults={}, Children={}, ChildAges={}",
                criteria.getLocationOrHotelName(),
                criteria.getCheckInDate(),
                criteria.getCheckOutDate(),
                criteria.getAdultCount(),
                criteria.getChildCount(),
                criteria.getChildAges());

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
        ChatSessionManager.SessionState sessionState = chatSessionManager.getSessionState(sessionId);

        // AI ile arama sonuçlarını özetleme
        if (searchResponse.isSuccess() && searchResponse.getResults() != null
                && !searchResponse.getResults().isEmpty()) {

            List<?> fullResults = searchResponse.getResults();
            List<?> slicedResults = fullResults;

            if (sessionState != null) {
                // Set AWAITING_CONFIRM mode
                sessionState.setMode("AWAITING_CONFIRM");
                sessionState.setAllSearchResults(fullResults);
                sessionState.setResultOffset(0);
                sessionState.setLastSearchHadNoResults(false);

                int totalSize = fullResults.size();
                slicedResults = fullResults.subList(0, Math.min(10, totalSize));
                sessionState.setLastShownResults(slicedResults);
            } else {
                int totalSize = fullResults.size();
                slicedResults = fullResults.subList(0, Math.min(10, totalSize));
            }

            // Set sliced results onto the response
            searchResponse.setResults((List)slicedResults);

            // Sonuçlar zaten kart olarak gösteriliyor — ayrıca metin özeti yazdırmıyoruz
            // (AI çağrısı da atlanmış oluyor: daha hızlı yanıt, daha az kota kullanımı).
            finalReply = "";
        } else {
            if (sessionState != null) {
                sessionState.setLastSearchHadNoResults(true);
            }
            finalReply = responseAgent.noResultsFound(criteria, userMessage, searchResponse.getSuggestedDates());
        }

        finalReply = prependNote(reclassificationNote, finalReply);

        return ChatResponse.builder()
                .reply(finalReply)
                .sessionId(sessionId)
                .searchType(intent)
                .missingFields(List.of())
                .chatStatus("ACTIVE")
                .success(searchResponse.isSuccess())
                .results(searchResponse.getResults())
                .criteria(com.santsg.tourvisio.dto.ChatCriteriaSummary.from(criteria))
                .build();
    }

    /**
     * Bu mesajdan (adjustIncomingCriteria sonrası) hiçbir yeni arama bilgisi
     * çıkarılmadı mı? "en yakın tarih ne var" gibi salt soru niteliğindeki
     * mesajlarda true döner — bu durumda üst katman aynı aramayı tekrarlamak
     * yerine doğrudan yakın tarih önerisine geçebilir.
     */
    private boolean hasNoNewSearchCriteria(SearchCriteria incoming) {
        if (incoming == null) return true;
        return incoming.getLocationOrHotelName() == null
                && incoming.getCheckInDate() == null
                && incoming.getCheckOutDate() == null
                && incoming.getAdultCount() == null
                && (incoming.getChildCount() == null || incoming.getChildCount() == 0)
                && (incoming.getChildAges() == null || incoming.getChildAges().isEmpty())
                && incoming.getDepartureLocation() == null
                && incoming.getArrivalLocation() == null
                && incoming.getDepartureDate() == null
                && incoming.getReturnDate() == null
                && incoming.getPassengerCount() == null
                && incoming.getTripType() == null
                && incoming.getRoomCount() == null;
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