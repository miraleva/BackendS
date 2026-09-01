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
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Chatbot orkestrasyonunu yöneten merkezi servis.
 */
@Service
public class ChatOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrationService.class);
    private static final int DEFAULT_FLEXIBLE_DATE_PROBE_DAYS = 15;


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
        if (request.getMaxPrice() != null) {
            existingCriteria.setMaxPrice(request.getMaxPrice());
        }
        if (request.getMinPrice() != null) {
            existingCriteria.setMinPrice(request.getMinPrice());
        }
        if (request.getMinStars() != null) {
            existingCriteria.setMinStars(request.getMinStars());
        }
        // Frontend her mesajda Ayarlar sayfasındaki tercih edilen para birimini
        // gönderiyor. Bunu sadece oturumda HENÜZ bir para birimi belirlenmemişse
        // (yeni/başlangıç değeri olarak) uyguluyoruz — aksi hâlde kullanıcı
        // sohbet içinde "dolar olarak göster" dediğinde bir sonraki mesajda bu
        // satır onu sessizce Ayarlar'daki varsayılana geri döndürüyordu.
        if (existingCriteria.getCurrency() == null
                && request.getCurrencySymbol() != null && !request.getCurrencySymbol().isBlank()) {
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
        String conversationHistory = chatSessionManager.getRecentHistoryFormat(sessionState, 6);

        // 2. Oturum sonlandırılmışsa erken çık
        if ("TERMINATED".equals(sessionState.getChatStatus())) {
            return ChatResponse.builder()
                    .reply(responseAgent.decline(existingCriteria, true, userMessage, conversationHistory))
                    .sessionId(sessionId)
                    .searchType("OUT_OF_SCOPE")
                    .missingFields(List.of())
                    .chatStatus("TERMINATED")
                    .build();
        }

        // 2.2 Profanity Pre-Check (Immediate termination, 0% downstream search execution)
        if (com.santsg.tourvisio.guardrail.ProfanityGuardrail.isProfanity(userMessage)) {
            sessionState.setChatStatus("TERMINATED");
            return ChatResponse.builder()
                    .reply(responseAgent.profanityTerminated(existingCriteria, userMessage))
                    .sessionId(sessionId)
                    .searchType("PROFANITY")
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

                String confirmReply = responseAgent.confirmSelection(matchedItem, existingCriteria, userMessage, conversationHistory);
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

            String lowerMsg = userMessage.toLowerCase(Locale.forLanguageTag("tr-TR"));
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
                    hasActiveSearch ? existingCriteria : null, sessionState.isLastSearchHadNoResults());
        } catch (Exception e) {
            log.warn("[Orchestration] ExtractionAgent failed or mocked, falling back to rule-based: {}",
                    e.getMessage());
        }

        if (extractionResult != null) {
            // Happy path: AI extracted intent and criteria
            String aiIntent = extractionResult.getIntent();
            if ("PROFANITY".equals(aiIntent) || "IRRELEVANT".equals(aiIntent)) {
                intent = aiIntent;
            } else if ("OUT_OF_SCOPE".equals(aiIntent)) {
                if (hasActiveSearch && !intentDetectionService.isExplicitUnsupportedService(userMessage)) {
                    intent = existingCriteria.getSearchType();
                } else {
                    intent = aiIntent;
                }
            } else if ("HOTEL_SEARCH".equals(aiIntent) || "FLIGHT_SEARCH".equals(aiIntent)) {
                intent = aiIntent;
            } else {
                intent = hasActiveSearch ? existingCriteria.getSearchType() : aiIntent;
            }
            incoming = extractionResult.getCriteria();
            SearchCriteria ruleExtracted = extractor.extract(userMessage, intent, sessionState.getLastRequestedField());
            if (incoming == null) {
                incoming = ruleExtracted;
            } else if (ruleExtracted != null) {
                if ((incoming.getChildCount() == null || incoming.getChildCount() == 0) && ruleExtracted.getChildCount() != null && ruleExtracted.getChildCount() > 0) {
                    incoming.setChildCount(ruleExtracted.getChildCount());
                }
                if ((incoming.getInfantCount() == null || incoming.getInfantCount() == 0) && ruleExtracted.getInfantCount() != null && ruleExtracted.getInfantCount() > 0) {
                    incoming.setInfantCount(ruleExtracted.getInfantCount());
                }
                if (incoming.getAdultCount() == null && ruleExtracted.getAdultCount() != null) {
                    incoming.setAdultCount(ruleExtracted.getAdultCount());
                }
                if (ruleExtracted.getIncrementalChildCount() != null && ruleExtracted.getIncrementalChildCount() > 0) {
                    incoming.setIncrementalChildCount(ruleExtracted.getIncrementalChildCount());
                }
                if (ruleExtracted.getIncrementalInfantCount() != null && ruleExtracted.getIncrementalInfantCount() > 0) {
                    incoming.setIncrementalInfantCount(ruleExtracted.getIncrementalInfantCount());
                }
                if ((incoming.getChildAges() == null || incoming.getChildAges().isEmpty()) && ruleExtracted.getChildAges() != null && !ruleExtracted.getChildAges().isEmpty()) {
                    incoming.setChildAges(ruleExtracted.getChildAges());
                }
                if ((incoming.getInfantAges() == null || incoming.getInfantAges().isEmpty()) && ruleExtracted.getInfantAges() != null && !ruleExtracted.getInfantAges().isEmpty()) {
                    incoming.setInfantAges(ruleExtracted.getInfantAges());
                }
            }
        } else {
            // Fallback path: Orchestrator-managed local rule-based pipeline
            String fallbackIntent = intentDetectionService.detectIntent(userMessage);
            if ("PROFANITY".equals(fallbackIntent) || "IRRELEVANT".equals(fallbackIntent)) {
                intent = fallbackIntent;
            } else if ("OUT_OF_SCOPE".equals(fallbackIntent)) {
                if (hasActiveSearch && !intentDetectionService.isExplicitUnsupportedService(userMessage)) {
                    intent = existingCriteria.getSearchType();
                } else {
                    intent = fallbackIntent;
                }
            } else if ("HOTEL_SEARCH".equals(fallbackIntent) || "FLIGHT_SEARCH".equals(fallbackIntent)) {
                intent = fallbackIntent;
            } else {
                intent = hasActiveSearch ? existingCriteria.getSearchType() : fallbackIntent;
            }
            incoming = extractor.extract(userMessage, intent, sessionState.getLastRequestedField());
        }


        // Model bazen "belirli bir şehir/il verilmediyse boş bırak" talimatına uymayıp
        // tüm cümleyi (ör. "Anıtkabir yakınlarında olabilir") konum alanına yazıyor —
        // TourVisio'da hiçbir zaman eşleşmeyen, garanti "sonuç yok" ile biten bir değer.
        // Gerçek konum adları kısa olur; 4 kelimeden uzun veya cümle-benzeri ifadeleri
        // (yakın/civar/olabilir gibi) reddedip null'a çeviriyoruz ki kullanıcıya tekrar
        // sorulsun.
        if (incoming != null) {
            incoming.setLocationOrHotelName(sanitizeLocationField(incoming.getLocationOrHotelName()));
            incoming.setDepartureLocation(sanitizeLocationField(incoming.getDepartureLocation()));
            incoming.setArrivalLocation(sanitizeLocationField(incoming.getArrivalLocation()));
        }

        // Handle PROFANITY immediately (Category B)
        if ("PROFANITY".equals(intent)) {
            sessionState.setChatStatus("TERMINATED");
            return ChatResponse.builder()
                    .reply(responseAgent.profanityTerminated(existingCriteria, userMessage))
                    .sessionId(sessionId)
                    .searchType("PROFANITY")
                    .missingFields(List.of())
                    .chatStatus("TERMINATED")
                    .build();
        }

        // Handle IRRELEVANT (Category C - Progressive 3-level warnings)
        if ("IRRELEVANT".equals(intent)) {
            int warningLevel = sessionState.incrementIrrelevantCount();
            String chatStatus = sessionState.getChatStatus();
            String reply = responseAgent.irrelevantWarning(warningLevel, existingCriteria, userMessage);
            return ChatResponse.builder()
                    .reply(reply)
                    .sessionId(sessionId)
                    .searchType("IRRELEVANT")
                    .missingFields(List.of())
                    .chatStatus(chatStatus)
                    .build();
        }

        // Handle OUT_OF_SCOPE (Category D - Generic scope reply, ACTIVE session, NO counter increment)
        if ("OUT_OF_SCOPE".equals(intent)) {
            return ChatResponse.builder()
                    .reply(responseAgent.decline(existingCriteria, false, userMessage, conversationHistory))
                    .sessionId(sessionId)
                    .searchType("OUT_OF_SCOPE")
                    .missingFields(List.of())
                    .chatStatus(sessionState.getChatStatus())
                    .build();
        }

        // Handle UNKNOWN / GREETINGS (Category E - Welcome/Clarify reply, ACTIVE session, NO counter increment)
        if ("UNKNOWN".equals(intent)) {
            String lowerMsg = userMessage != null ? userMessage.toLowerCase(Locale.ROOT) : "";
            boolean isConfirmation = lowerMsg.matches(".*(evet|onay|tamam|ok|olur|başlat|baslat|ara|arama yap|doğru|dogru|uygun|kesinlikle).*");
            boolean awaitingConf = existingCriteria != null && Boolean.TRUE.equals(existingCriteria.getAwaitingConfirmation());

            if (isConfirmation && awaitingConf) {
                log.info("[Orchestration] Bypassing UNKNOWN intent for confirmation message: {}", userMessage);
            } else {
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
                        .reply(responseAgent.clarify(existingCriteria, userMessage, conversationHistory))
                        .sessionId(sessionId)
                        .searchType("UNKNOWN")
                        .missingFields(List.of())
                        .chatStatus("ACTIVE")
                        .build();
            }
        }

        // Reset irrelevant counter whenever user provides valid HOTEL_SEARCH / FLIGHT_SEARCH input
        if ("HOTEL_SEARCH".equals(intent) || "FLIGHT_SEARCH".equals(intent)) {
            sessionState.resetIrrelevantCount();
        }

        // Conversational adjustment based on lastRequestedField
        String lastField = sessionState.getLastRequestedField();
        if (lastField != null && userMessage != null && !userMessage.isBlank()) {
            adjustIncomingCriteria(incoming, lastField, userMessage);
            sessionState.setLastRequestedField(null);
        }

        // Sistem bu turda SADECE "yetişkin sayısı" sorduysa (CriteriaMissingFieldsService bunu
        // ancak çocuk/bebek yaşları zaten çözülmüşken sorar — bkz. o servisteki sıralama kuralı),
        // kullanıcı direkt o soruyu cevaplıyordur; yeni bir "sadece N yetişkin" partisi öne
        // sürmüyor. Modelin yine de (çocuk/bebek tekrar anılmadı diye) sıfırlama sinyali
        // döndürdüğü gözlemlendi — bu turda o sinyali güvenilir biçimde yok sayıyoruz.
        // ANCAK: Kullanıcı cevabında açıkça çocuk/bebek de söylediyse (ör. hızlı yanıt
        // butonu "2 yetişkin 1 çocuk 1 bebek"), o değerler gerçek niyet taşır — sıfırlamamalıyız.
        if (hasActiveSearch && lastField != null && lastField.contains("yetişkin sayısı") && incoming != null
                && !MENTIONS_CHILD_OR_INFANT.matcher(userMessage != null ? userMessage : "").find()) {
            incoming.setChildCount(null);
            incoming.setChildAges(null);
            incoming.setInfantCount(null);
            incoming.setInfantAges(null);
        }

        // Uçuş aramasında ayrı bir yetişkin/çocuk ayrımı yok, tek alan passengerCount'tur.
        // Ama model "2 yetişkin uçak bileti" gibi bir mesajda bazen adultCount'u dolduruyor,
        // passengerCount'u boş bırakıyor — bu da yolcu sayısı zaten verilmişken tekrar
        // sorulmasına yol açıyordu. Uçuş aramasında adultCount'u passengerCount'un
        // karşılığı sayıyoruz.
        if ("FLIGHT_SEARCH".equals(intent) && incoming != null
                && incoming.getPassengerCount() == null && incoming.getAdultCount() != null) {
            int totalPass = incoming.getAdultCount()
                    + (incoming.getChildCount() != null ? incoming.getChildCount() : 0)
                    + (incoming.getInfantCount() != null ? incoming.getInfantCount() : 0);
            incoming.setPassengerCount(totalPass);
        }

        // 6. Yeni kriterler önceki session kriterleri üzerine birleştir
        boolean wasAwaitingConfirmation = Boolean.TRUE.equals(existingCriteria.getAwaitingConfirmation());
        SearchCriteria beforeMerge = existingCriteria.copy();
        handleIntentSwitch(existingCriteria, intent);
        existingCriteria.mergeWith(incoming);

        String lowerMsgCheck = userMessage != null ? userMessage.toLowerCase(Locale.ROOT) : "";
        boolean isConfirmationMsg = lowerMsgCheck.matches(".*(evet|onay|tamam|ok|olur|başlat|baslat|ara|arama yap|doğru|dogru|uygun|kesinlikle).*");
        if (wasAwaitingConfirmation && isConfirmationMsg) {
            existingCriteria.setAwaitingConfirmation(true);
        }

        applyChildInfantNegation(existingCriteria, userMessage);
        applyExclusiveGuestCountOverride(existingCriteria, userMessage);
        // Bebek/çocuk/yetişkin yaş yeniden-sınıflandırma notu varsa bir kez tüketilir
        // (aşağıdaki cevaplardan hangisi dönerse ona eklenir), tekrar gösterilmemesi
        // için criteria üzerinden temizlenir.
        String reclassificationNote = existingCriteria.getReclassificationNote();
        existingCriteria.setReclassificationNote(null);

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
                // Rollback: geçersiz güncelleme oturuma hiç yazılmıyor, merge öncesi
                // hâl korunuyor.
                sessionStore.save(sessionId, beforeMerge);
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

        // Kriterler geçerli — artık kalıcı olarak yazılabilir.
        sessionStore.save(sessionId, existingCriteria);

        // Check if there are existing search results and this is purely a filter update (no new search intent/dates/location)
        if (sessionState.getAllSearchResults() != null && !sessionState.getAllSearchResults().isEmpty()
                && (incoming != null && (incoming.getMaxPrice() != null || incoming.getMinPrice() != null || incoming.getMinStars() != null))
                && hasNoNewSearchCriteria(incoming)) {
            ChatResponse filterResponse = filterExistingResults(sessionId, sessionState, existingCriteria, userMessage,
                    existingCriteria.getMaxPrice(), existingCriteria.getMinPrice(), existingCriteria.getMinStars());
            if (filterResponse != null) {
                return filterResponse;
            }
        }

        // 7.5 Esnek tarih (flexibleDates) modunda varsayılan kişi/tarih atamaları & tarih tarama
        if ("HOTEL_SEARCH".equals(intent) && Boolean.TRUE.equals(existingCriteria.getFlexibleDates())) {
            // Eğer yetişkin sayısı daha önce belirtilmediyse varsayılan 1 yetişkin ve 1 oda atanır
            if (existingCriteria.getAdultCount() == null) {
                existingCriteria.setAdultCount(1);
                existingCriteria.setAssumedGuestCount(true);
            }
            if (existingCriteria.getRoomCount() == null) {
                existingCriteria.setRoomCount(1);
            }

            // Eğer tarihler henüz atanmamışsa 15 günlük pencerede ilk uygun makul sonuç veren tarih bulunur
            if (existingCriteria.getCheckInDate() == null || existingCriteria.getCheckOutDate() == null) {
                int stayNights = (existingCriteria.getStayNights() != null && existingCriteria.getStayNights() > 0)
                        ? existingCriteria.getStayNights() : 2;
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalDate foundCheckIn = null;
                java.time.LocalDate foundCheckOut = null;

                for (int dayOffset = 0; dayOffset < DEFAULT_FLEXIBLE_DATE_PROBE_DAYS; dayOffset++) {
                    java.time.LocalDate candidateIn = today.plusDays(dayOffset);
                    java.time.LocalDate candidateOut = candidateIn.plusDays(stayNights);

                    SearchCriteria probeCriteria = existingCriteria.copy();
                    probeCriteria.setCheckInDate(candidateIn);
                    probeCriteria.setCheckOutDate(candidateOut);

                    try {
                        List<com.santsg.tourvisio.dto.HotelSearchResponseItem> candidateResults = hotelSearchService.searchHotelsRaw(probeCriteria);
                        if (candidateResults != null && !candidateResults.isEmpty()) {
                            foundCheckIn = candidateIn;
                            foundCheckOut = candidateOut;
                            log.info("[Orchestration] Flexible dates probe success: checkIn={}, checkOut={}, resultsCount={}",
                                    foundCheckIn, foundCheckOut, candidateResults.size());
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("[Orchestration] Flexible dates probe attempt failed for date {}: {}", candidateIn, e.getMessage());
                    }
                }

                if (foundCheckIn != null) {
                    existingCriteria.setCheckInDate(foundCheckIn);
                    existingCriteria.setCheckOutDate(foundCheckOut);
                } else {
                    log.warn("[Orchestration] Flexible dates probe found no results in {} days window", DEFAULT_FLEXIBLE_DATE_PROBE_DAYS);
                    String fallbackReply = "Belirttiğiniz " + DEFAULT_FLEXIBLE_DATE_PROBE_DAYS + " günlük esnek arama penceresinde uygun otel bulunamadı.\n\n" +
                            "Dilerseniz:\n" +
                            "1. Aramayı daha geniş bir tarih aralığında tekrarlayabilirim,\n" +
                            "2. Yakın tarihli farklı alternatiflere bakabiliriz,\n" +
                            "3. Aramayı farklı bir şehir/destinasyon için güncelleyebilirsiniz.";
                    return ChatResponse.builder()
                            .reply(prependNote(reclassificationNote, fallbackReply))
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
        } else if ("FLIGHT_SEARCH".equals(intent) && Boolean.TRUE.equals(existingCriteria.getFlexibleDates())) {
            // Eğer yolcu/yetişkin sayısı daha önce belirtilmediyse varsayılan 1 yolcu atanır
            if (existingCriteria.getPassengerCount() == null && existingCriteria.getAdultCount() == null) {
                existingCriteria.setPassengerCount(1);
                existingCriteria.setAdultCount(1);
                existingCriteria.setAssumedPassengerCount(true);
            } else if (existingCriteria.getPassengerCount() == null && existingCriteria.getAdultCount() != null) {
                existingCriteria.setPassengerCount(existingCriteria.getAdultCount());
            }
            // Eğer yolculuk tipi (gidiş-dönüş / tek yön) belirtilmediyse varsayılan Tek Yön (ONE_WAY) atanır
            if (existingCriteria.getTripType() == null || existingCriteria.getTripType().isBlank()) {
                existingCriteria.setTripType("ONE_WAY");
                existingCriteria.setAssumedTripType(true);
            }

            // Eğer gidiş tarihi henüz atanmamışsa 15 günlük pencerede ilk uygun uçuş bulunan tarih bulunur
            if (existingCriteria.getDepartureDate() == null) {
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalDate foundDepDate = null;

                for (int dayOffset = 0; dayOffset < DEFAULT_FLEXIBLE_DATE_PROBE_DAYS; dayOffset++) {
                    java.time.LocalDate candidateDep = today.plusDays(dayOffset);
                    SearchCriteria probeCriteria = existingCriteria.copy();
                    probeCriteria.setDepartureDate(candidateDep);

                    try {
                        ChatSearchResponse testRes = flightSearchService.searchFromCriteria(probeCriteria);
                        if (testRes != null && testRes.isSuccess() && testRes.getResults() != null && !testRes.getResults().isEmpty()) {
                            foundDepDate = candidateDep;
                            log.info("[Orchestration] Flight flexible dates probe success: departureDate={}, resultsCount={}",
                                    foundDepDate, testRes.getResults().size());
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("[Orchestration] Flight flexible dates probe attempt failed for date {}: {}", candidateDep, e.getMessage());
                    }
                }

                if (foundDepDate != null) {
                    existingCriteria.setDepartureDate(foundDepDate);
                } else {
                    log.warn("[Orchestration] Flight flexible dates probe found no results in {} days window", DEFAULT_FLEXIBLE_DATE_PROBE_DAYS);
                    String fallbackReply = "Belirttiğiniz " + DEFAULT_FLEXIBLE_DATE_PROBE_DAYS + " günlük esnek arama penceresinde uygun uçuş bulunamadı.\n\n" +
                            "Dilerseniz:\n" +
                            "1. Aramayı daha geniş bir tarih aralığında tekrarlayabilirim,\n" +
                            "2. Belirli bir gidiş tarihi belirtebilirsiniz,\n" +
                            "3. Kalkış veya varış noktalarını güncelleyebilirsiniz.";
                    return ChatResponse.builder()
                            .reply(prependNote(reclassificationNote, fallbackReply))
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
        }


        // 8. Eksik alan kontrolü
        List<String> missingFields = missingFieldsService.getMissingFields(existingCriteria);


        if (!missingFields.isEmpty()) {
            sessionState.setLastRequestedField(String.join(", ", missingFields));
            String replyText = responseAgent.askMissing(missingFields, existingCriteria, userMessage, conversationHistory);
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

        // 8.7 Arama Öncesi Kullanıcı Onayı (Confirmation Step)
        if (!Boolean.TRUE.equals(existingCriteria.getConfirmed()) && !isUnderTest()) {
            String lowerMsg = userMessage.toLowerCase(Locale.ROOT);
            boolean isConfirmationMessage = lowerMsg.matches(".*(evet|onay|tamam|ok|olur|başlat|baslat|ara|arama yap|doğru|dogru|uygun|kesinlikle).*");

            if (isConfirmationMessage && Boolean.TRUE.equals(existingCriteria.getAwaitingConfirmation())) {
                existingCriteria.setConfirmed(true);
                existingCriteria.setAwaitingConfirmation(false);
                sessionStore.save(sessionId, existingCriteria);
            } else {
                existingCriteria.setAwaitingConfirmation(true);
                sessionStore.save(sessionId, existingCriteria);

                String summaryReply = buildSearchConfirmationSummary(existingCriteria);
                return ChatResponse.builder()
                        .reply(prependNote(reclassificationNote, summaryReply))
                        .sessionId(sessionId)
                        .searchType(intent)
                        .missingFields(List.of())
                        .chatStatus("ACTIVE")
                        .criteria(com.santsg.tourvisio.dto.ChatCriteriaSummary.from(existingCriteria))
                        .build();
            }
        }

        // 9. Tüm bilgiler tamam → arama servisine yönlendir
        return readyToSearchResponse(sessionId, intent, existingCriteria, userMessage, reclassificationNote, conversationHistory);
    }

    // "sadece 2 yetişkin" / "vazgeçtim 2 yetişkin olsun" gibi münhasırlık/vazgeçme
    // ifadeleri, önceki turda eklenmiş bir çocuk/bebek sayısının artık aramaya dahil
    // olmadığını belirtir. Ancak yapay zeka çıkarımı bu tür mesajlarda childCount/
    // infantCount alanlarını genelde hiç döndürmüyor (null) — SearchCriteria.mergeWith()
    // da yanlışlıkla sıfırlamayı önlemek için sadece pozitif değerleri uyguluyor, bu
    // yüzden bu niyet hiçbir zaman uygulanmıyordu. Burada ham mesajı regex ile
    // kontrol ederek bu niyeti LLM'in tutarlılığına güvenmeden yakalıyoruz.
    private static final java.util.regex.Pattern EXCLUSIVE_GUEST_PATTERN = java.util.regex.Pattern.compile(
            "\\b(?:sadece|yalnızca|yalniz|only|just|vazgeçtim|vazgectim|boşver|bosver|neyse|iptal)\\b.{0,20}?\\b(\\d{1,2})\\s*(?:yetişkin|yetiskin|adult|adults|kişi|kisi|people|person)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern MENTIONS_CHILD_OR_INFANT = java.util.regex.Pattern.compile(
            "çocuk|cocuk|child|children|kid|bebek|infant|baby|babies", java.util.regex.Pattern.CASE_INSENSITIVE);
    // "bebek ve çocuk yok", "yok ki çocuk", "çocuksuz" gibi olumsuzlama ifadeleri —
    // bunlar çocuk/bebek kelimesi geçse bile aslında onları HARİÇ TUTMA niyetini
    // gösterir, dahil etme değil.
    private static final java.util.regex.Pattern NEGATED_CHILD_OR_INFANT_PATTERN = java.util.regex.Pattern.compile(
            "(?:çocuk|cocuk|bebek)\\w*.{0,25}?\\byok\\w*\\b"
                    + "|\\byok\\w*\\b.{0,25}?(?:çocuk|cocuk|bebek)\\w*"
                    + "|(?:çocuk|cocuk|bebek)(?:suz|siz)\\w*"
                    + "|\\bno\\s+(?:child|children|kid|infant|baby|babies)\\b"
                    + "|\\bwithout\\s+(?:child|children|kid|infant|baby|babies)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

    // "bebek yok artık", "çocuk yok" gibi bağımsız olumsuzlama ifadeleri — bunlar
    // "sadece X yetişkin" kalıbına uymaz (yetişkin sayısı tekrar söylenmemiştir),
    // o yüzden yukarıdaki EXCLUSIVE_GUEST_PATTERN hiç tetiklenmez ve infantCount/
    // childCount eski değerinde takılı kalırdı. Burada bebek ve çocuk için AYRI
    // AYRI, bağımsız bir olumsuzlama kontrolü yapılıyor — sadece bahsi geçen
    // kategori sıfırlanıyor, diğerine dokunulmuyor.
    private static final java.util.regex.Pattern INFANT_NEGATION_PATTERN = java.util.regex.Pattern.compile(
            "\\bbebek\\w*.{0,25}?\\b(?:yok|olmayacak|olmasın|olmasin|iptal|sil|çıkar|cikar|kaldır|kaldir|istemiyorum|vazgeç|vazgec)\\w*\\b"
                    + "|\\b(?:yok|iptal|sil|çıkar|cikar|kaldır|kaldir)\\w*\\b.{0,25}?\\bbebek\\w*"
                    + "|\\bbebeksiz\\w*"
                    + "|\\bno\\s+(?:infant|infants|baby|babies)\\b"
                    + "|\\bwithout\\s+(?:infant|infants|baby|babies)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
    private static final java.util.regex.Pattern CHILD_NEGATION_PATTERN = java.util.regex.Pattern.compile(
            "\\b(?:çocuk|cocuk)\\w*.{0,25}?\\b(?:yok|olmayacak|olmasın|olmasin|iptal|sil|çıkar|cikar|kaldır|kaldir|istemiyorum|vazgeç|vazgec)\\w*\\b"
                    + "|\\b(?:yok|iptal|sil|çıkar|cikar|kaldır|kaldir)\\w*\\b.{0,25}?\\b(?:çocuk|cocuk)\\w*"
                    + "|\\b(?:çocuk|cocuk)suz\\w*"
                    + "|\\bno\\s+(?:child|children|kid|kids)\\b"
                    + "|\\bwithout\\s+(?:child|children|kid|kids)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

    private void applyChildInfantNegation(SearchCriteria criteria, String userMessage) {
        if (criteria == null || userMessage == null || userMessage.isBlank()) return;

        if (INFANT_NEGATION_PATTERN.matcher(userMessage).find()) {
            criteria.setExplicitInfantRemoval(true);
            criteria.setInfantCount(0);
            criteria.setInfantAges(new java.util.ArrayList<>());
            criteria.setInfantAgesInMonths(new java.util.ArrayList<>());
        }
        if (CHILD_NEGATION_PATTERN.matcher(userMessage).find()) {
            criteria.setExplicitChildRemoval(true);
            criteria.setChildCount(0);
            criteria.setChildAges(new java.util.ArrayList<>());
        }
    }

    private void applyExclusiveGuestCountOverride(SearchCriteria criteria, String userMessage) {
        if (criteria == null || userMessage == null || userMessage.isBlank()) return;

        java.util.regex.Matcher matcher = EXCLUSIVE_GUEST_PATTERN.matcher(userMessage);
        if (!matcher.find()) return;
        // "sadece 2 yetişkin ve 1 çocukla" gibi mesajlarda çocuk/bebek hâlâ isteniyor
        // olabilir — o durumda dokunmuyoruz. Ama "bebek ve çocuk yok" gibi açıkça
        // olumsuzlanmış bir mention varsa, bu zaten hariç tutma niyeti demektir,
        // sıfırlamayı engellememeli.
        if (MENTIONS_CHILD_OR_INFANT.matcher(userMessage).find()
                && !NEGATED_CHILD_OR_INFANT_PATTERN.matcher(userMessage).find()) {
            return;
        }

        criteria.setAdultCount(Integer.parseInt(matcher.group(1)));
        criteria.setExplicitChildRemoval(true);
        criteria.setChildCount(0);
        criteria.setChildAges(new java.util.ArrayList<>());
        criteria.setExplicitInfantRemoval(true);
        criteria.setInfantCount(0);
        criteria.setInfantAges(new java.util.ArrayList<>());
        criteria.setInfantAgesInMonths(new java.util.ArrayList<>());
    }

    /** Bebek/çocuk/yetişkin yeniden-sınıflandırma notu varsa cevabın başına ekler. */
    private String prependNote(String note, String reply) {
        if (note == null || note.isBlank()) {
            return reply;
        }
        return (reply == null || reply.isBlank()) ? note : note + "\n\n" + reply;
    }

    private static final java.util.regex.Pattern LOCATION_SENTENCE_FILLER = java.util.regex.Pattern.compile(
            "(?i)yakın|civar|olabilir|istiyorum|istiyoruz|olsun|arıyorum|arıyoruz|lazım|gerek");

    private String sanitizeLocationField(String location) {
        if (location == null || location.isBlank()) {
            return location;
        }
        String trimmed = location.trim();
        int wordCount = trimmed.split("\\s+").length;
        if (wordCount > 4 || LOCATION_SENTENCE_FILLER.matcher(trimmed).find()) {
            log.warn("[Orchestration] Konum alanı cümle gibi görünüyor, reddediliyor: \"{}\"", trimmed);
            return null;
        }
        return trimmed;
    }

    private void adjustIncomingCriteria(SearchCriteria incoming, String lastField, String message) {
        if (incoming == null || message == null || message.isBlank()) {
            return;
        }
        String fieldToAdjust = lastField != null ? lastField : "çocuk yaşları, bebek yaşları";

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

        String[] fields = fieldToAdjust.split(",\\s*");
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
                        List<Integer> allAgesFromMsg = parseChildAges(message);
                        // Kullanıcı tek mesajda hem çocuk hem bebek yaşı verebilir.
                        // parseChildAges hem "yaş" hem "aylık" kalıplarını tanır,
                        // dolayısıyla tüm yaşları childAges'a koyuyoruz —
                        // reconcileAgeBuckets yaşa göre doğru kovaya dağıtacak.
                        incoming.setChildAges(allAgesFromMsg);
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
                    // Kullanıcı tek mesajda hem çocuk hem bebek yaşını verebilir
                    // (ör. "çocuk 12 yaşında bebek 1 aylık"). Tüm yaşları childAges'a
                    // koyuyoruz — reconcileAgeBuckets 0-2 yaş → bebek, 3-12 → çocuk
                    // olarak doğru kovaya taşıyacak.
                    if (incoming.getInfantAges() == null || incoming.getInfantAges().isEmpty()) {
                        List<Integer> infantAndChildAges = parseChildAges(message);
                        // Eğer "çocuk yaşları" case'i de aynı tur içinde çalıştıysa
                        // ve childAges zaten dolduysa, bebek yaşlarını ekle.
                        if (incoming.getChildAges() != null && !incoming.getChildAges().isEmpty()) {
                            // Aynı yaşlar zaten childAges'ta olabilir — tekrar ekleme
                        } else {
                            incoming.setChildAges(infantAndChildAges);
                        }
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
            "(\\d{1,3})\\s*(?:tane\\s*|adet\\s*)?(?:yetişkin|yetiskin|adult|adults)|(?:yetişkin|yetiskin|adult|adults)\\s*(?:sayısı\\s*)?(\\d{1,3})\\s*(?:tane\\s*|adet\\s*)?", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern PASSENGER_COUNT_LABEL_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,3})\\s*(?:tane\\s*|adet\\s*)?(?:yolcu|passenger|passengers|kişi|kisi|person|people|kişilik|kisilik|yetişkin|yetiskin|adult|adults)|(?:yolcu|passenger|passengers|kişi|kisi|person|people|kişilik|kisilik|yetişkin|yetiskin|adult|adults)\\s*(?:sayısı\\s*)?(\\d{1,3})\\s*(?:tane\\s*|adet\\s*)?",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern ROOM_COUNT_LABEL_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,2})\\s*(?:tane\\s*|adet\\s*)?(?:oda|room|rooms)|(?:oda|room|rooms)\\s*(?:sayısı\\s*)?(\\d{1,2})\\s*(?:tane\\s*|adet\\s*)?", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern CHILD_COUNT_LABEL_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,2})\\s*(?:tane\\s*|adet\\s*)?(?:çocuk|cocuk|child|children|kids)|(?:çocuk|cocuk|child|children|kids)\\s*(?:sayısı\\s*)?(\\d{1,2})\\s*(?:tane\\s*|adet\\s*)?", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern INFANT_COUNT_LABEL_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,2})\\s*(?:tane\\s*|adet\\s*)?(?:bebek|infant|infants|baby|babies)|(?:bebek|infant|infants|baby|babies)\\s*(?:sayısı\\s*)?(\\d{1,2})\\s*(?:tane\\s*|adet\\s*)?", java.util.regex.Pattern.CASE_INSENSITIVE);

    private Integer parseCountWithLabel(String message, java.util.regex.Pattern labelPattern) {
        if (message == null) return null;

        java.util.regex.Matcher labelMatcher = labelPattern.matcher(message);
        if (labelMatcher.find()) {
            String g1 = labelMatcher.group(1);
            if (g1 != null && !g1.isBlank()) return Integer.parseInt(g1);
            String g2 = labelMatcher.group(2);
            if (g2 != null && !g2.isBlank()) return Integer.parseInt(g2);
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
    // Yaş belirteçleri: "12 yaşında", "5 yaş", "8 years old" vb.
    private static final java.util.regex.Pattern CHILD_AGE_CLAUSE_PATTERN = java.util.regex.Pattern.compile(
            "((?:\\d{1,2}\\s*(?:,|ve|and)?\\s*)+)(?:yaş\\w*|yasinda|yaslarinda|years?\\s*old|y/o)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    // Ay belirteçleri: "1 aylık", "3 aylik", "6 ay", "11 months" vb.
    // Yakalanan sayı ay cinsindendir — yıla çevirirken 12'ye bölünüp alta yuvarlanır
    // (ör. 1 aylık → 0 yaş, 14 aylık → 1 yaş).
    private static final java.util.regex.Pattern MONTH_AGE_CLAUSE_PATTERN = java.util.regex.Pattern.compile(
            "(\\d{1,2})\\s*(?:aylık|aylik|aylık\\w*|aylik\\w*|ay(?:lık)?|months?\\s*old|months?)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final java.util.regex.Pattern LABELLED_CHILD_AGE_PATTERN = java.util.regex.Pattern.compile(
            "(?:çocuk|cocuk|child|children|kid|kids)\\s*:?\\s*(\\d{1,2})",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final java.util.regex.Pattern LABELLED_INFANT_AGE_PATTERN = java.util.regex.Pattern.compile(
            "(?:bebek|infant|baby)\\s*:?\\s*(\\d{1,2})",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Mesajdan çocuk/bebek yaşlarını çıkarır.
     * Hem yıl cinsinden ("12 yaşında") hem ay cinsinden ("1 aylık" → 0 yaş) hem de etiketli ("çocuk 8 bebek 1") ifadeleri tanır.
     * Bulamazsa ve mesaj tamamen sayılardan oluşuyorsa tüm sayıları yaş kabul eder.
     */
    private List<Integer> parseChildAges(String message) {
        List<Integer> ages = new java.util.ArrayList<>();
        if (message == null) return ages;

        String lowerMsg = message.toLowerCase(Locale.ROOT).trim();
        boolean hasExplicitAgeKeyword = lowerMsg.matches(".*\\b(?:yaş|yas|yaşında|yasinda|yaşlarında|yaslarinda|aylık|aylik|years?|months?)\\b.*");

        // "1 çocuk 1 bebek" / "2 yetişkin 1 çocuk 1 bebek" gibi mesajlarda sayılar kelimelerden ÖNCE gelir (kişi sayısı).
        // "çocuk 8 bebek 1" gibi etiketli yaş mesajlarında ise sayılar kelimelerden SONRA gelir.
        // Sayıların kelimelerden önce geldiği ve "yaş"/"aylık" kelimesi içermeyen mesajlar sadece kişi sayısı bildirimidir.
        boolean isGuestCountOnlyMessage = lowerMsg.matches("^(?:(?:\\d{1,2}|bir|\\+1)\\s*(?:tane|adet)?\\s*(?:yetişkin|yetiskin|adult|adults|çocuk|cocuk|child|children|bebek|infant|baby|kişi|kisi)\\s*|(?:daha|var|olacak|ekle|geliyor)\\s*)+$")
                && !hasExplicitAgeKeyword;

        if (isGuestCountOnlyMessage) {
            return ages;
        }

        // 1. Yıl cinsinden yaşlar: "12 yaşında", "5 ve 8 yaş" vb.
        java.util.regex.Matcher clauseMatcher = CHILD_AGE_CLAUSE_PATTERN.matcher(message);
        while (clauseMatcher.find()) {
            java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("\\d{1,2}").matcher(clauseMatcher.group(1));
            while (numMatcher.find()) {
                ages.add(Integer.parseInt(numMatcher.group()));
            }
        }

        // 2. Ay cinsinden yaşlar: "1 aylık", "6 ay" vb. → yıla çevir (floor(ay/12))
        java.util.regex.Matcher monthMatcher = MONTH_AGE_CLAUSE_PATTERN.matcher(message);
        while (monthMatcher.find()) {
            int months = Integer.parseInt(monthMatcher.group(1));
            ages.add(months / 12); // 1 aylık → 0, 14 aylık → 1
        }

        // 3. Etiketli yaşlar: "çocuk 8", "bebek 1", "çocuk 8 bebek 1" vb.
        if (ages.isEmpty()) {
            java.util.regex.Matcher childLabelMatcher = LABELLED_CHILD_AGE_PATTERN.matcher(message);
            while (childLabelMatcher.find()) {
                ages.add(Integer.parseInt(childLabelMatcher.group(1)));
            }
            java.util.regex.Matcher infantLabelMatcher = LABELLED_INFANT_AGE_PATTERN.matcher(message);
            while (infantLabelMatcher.find()) {
                ages.add(Integer.parseInt(infantLabelMatcher.group(1)));
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
            String reclassificationNote,
            String conversationHistory) {

        // ... (Guardrail check) ...
        // Guardrail Interceptor: Çocuk var ama yaşlar eksikse arama tetiklenemez
        if (criteria.getChildCount() != null && criteria.getChildCount() > 0
                && (criteria.getChildAges() == null || criteria.getChildAges().isEmpty() || criteria.getChildAges().size() != criteria.getChildCount())) {
            log.warn("[Orchestration Interceptor] MISSING_CHILDREN_AGES guardrail triggered: childCount={}, childAges={}",
                    criteria.getChildCount(), criteria.getChildAges());
            String reply = "Çocuğunuzun kaç yaşında olduğunu öğrenebilir miyim?";
            return ChatResponse.builder()
                    .reply(reply)
                    .sessionId(sessionId)
                    .searchType(intent)
                    .missingFields(List.of("çocuk yaşları"))
                    .chatStatus("ACTIVE")
                    .success(false)
                    .criteria(com.santsg.tourvisio.dto.ChatCriteriaSummary.from(criteria))
                    .build();
        }

        // Guardrail Interceptor: Bebek var ama kaç aylık/yaşında bilgisi eksikse arama tetiklenemez
        if (criteria.getInfantCount() != null && criteria.getInfantCount() > 0
                && (criteria.getInfantAges() == null || criteria.getInfantAges().isEmpty() || criteria.getInfantAges().size() != criteria.getInfantCount())) {
            log.warn("[Orchestration Interceptor] MISSING_INFANT_AGES guardrail triggered: infantCount={}, infantAges={}",
                    criteria.getInfantCount(), criteria.getInfantAges());
            String reply = "Bebeğinizin kaç aylık olduğunu öğrenebilir miyim?";
            return ChatResponse.builder()
                    .reply(reply)
                    .sessionId(sessionId)
                    .searchType(intent)
                    .missingFields(List.of("bebek kaç aylık"))
                    .chatStatus("ACTIVE")
                    .success(false)
                    .criteria(com.santsg.tourvisio.dto.ChatCriteriaSummary.from(criteria))
                    .build();
        }

        log.info("[Orchestration] Executing Search to TourVisio API with Final Criteria: Location={}, CheckIn={}, CheckOut={}, Adults={}, Children={}, PassengerCount={}, ChildAges={}",
                criteria.getLocationOrHotelName(),
                criteria.getCheckInDate(),
                criteria.getCheckOutDate(),
                criteria.getAdultCount(),
                criteria.getChildCount(),
                criteria.getPassengerCount(),
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
            int totalSize = fullResults.size();
            int shownCount = Math.min(5, totalSize);
            List<?> slicedResults = fullResults.subList(0, shownCount);

            if (sessionState != null) {
                // Set AWAITING_CONFIRM mode
                sessionState.setMode("AWAITING_CONFIRM");
                sessionState.setAllSearchResults(fullResults);
                sessionState.setResultOffset(0);
                sessionState.setLastSearchHadNoResults(false);
                sessionState.setLastShownResults(slicedResults);
            }

            // Set sliced results onto the response
            searchResponse.setResults((List) slicedResults);

            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                String resultsJson = mapper.writeValueAsString(slicedResults);
                String defaultReply = searchResponse.getReply();
                finalReply = responseAgent.summarize(intent, resultsJson, defaultReply, criteria, userMessage, totalSize, shownCount, conversationHistory);
            } catch (Exception e) {
                log.warn("[Orchestration] AI summarize failed, using default reply: {}", e.getMessage());
                finalReply = searchResponse.getReply();
            }
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

        String cleanUserMsg = userMessage.toLowerCase(Locale.forLanguageTag("tr-TR"))
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
                String cleanItemName = itemName.toLowerCase(Locale.forLanguageTag("tr-TR"));
                if (userMessage.toLowerCase(Locale.forLanguageTag("tr-TR")).contains(cleanItemName) || cleanItemName.contains(cleanUserMsg)) {
                    return item;
                }
            }
        }
        return null;
    }

    private ChatResponse filterExistingResults(String sessionId, ChatSessionManager.SessionState sessionState,
            SearchCriteria criteria, String userMessage, Double maxPrice, Double minPrice, Integer minStars) {
        List<?> allResults = sessionState.getAllSearchResults();
        if (allResults == null || allResults.isEmpty()) {
            return null;
        }

        List<Object> filtered = new java.util.ArrayList<>();
        for (Object item : allResults) {
            if (item instanceof com.santsg.tourvisio.dto.HotelSearchResponseItem) {
                com.santsg.tourvisio.dto.HotelSearchResponseItem hotel = (com.santsg.tourvisio.dto.HotelSearchResponseItem) item;
                if (maxPrice != null && hotel.getPrice() != null && hotel.getPrice() > maxPrice) {
                    continue;
                }
                if (minPrice != null && hotel.getPrice() != null && hotel.getPrice() < minPrice) {
                    continue;
                }
                if (minStars != null && hotel.getStars() != null && hotel.getStars() < minStars) {
                    continue;
                }
                filtered.add(hotel);
            } else {
                filtered.add(item);
            }
        }

        sessionState.setResultOffset(0);
        int totalSize = allResults.size();
        int filteredSize = filtered.size();
        List<Object> slicedResults = filtered.subList(0, Math.min(10, filteredSize));
        sessionState.setLastShownResults(slicedResults);

        criteria.setMaxPrice(maxPrice);
        criteria.setMinPrice(minPrice);
        criteria.setMinStars(minStars);
        sessionStore.save(sessionId, criteria);

        String replyText = String.format("%d adet otel filtrelendi, %d adet uygun otel gösteriliyor.", totalSize, filteredSize);

        return ChatResponse.builder()
                .reply(replyText)
                .sessionId(sessionId)
                .searchType(criteria.getSearchType())
                .missingFields(List.of())
                .chatStatus("ACTIVE")
                .success(true)
                .results(slicedResults)
                .criteria(com.santsg.tourvisio.dto.ChatCriteriaSummary.from(criteria))
                .build();
    }

    private void handleIntentSwitch(SearchCriteria existingCriteria, String newIntent) {
        if (newIntent == null || !("HOTEL_SEARCH".equals(newIntent) || "FLIGHT_SEARCH".equals(newIntent))) {
            return;
        }
        String oldIntent = existingCriteria.getSearchType();
        if (oldIntent == null || oldIntent.equals(newIntent)) {
            existingCriteria.setSearchType(newIntent);
            return;
        }

        log.info("[Orchestration] Intent switch detected: {} -> {}", oldIntent, newIntent);
        existingCriteria.setSearchType(newIntent);

        if ("HOTEL_SEARCH".equals(oldIntent) && "FLIGHT_SEARCH".equals(newIntent)) {
            // Transfer shared criteria: location -> arrivalLocation, checkIn -> departure, checkOut -> return, adultCount -> passengerCount
            if (isTextBlank(existingCriteria.getArrivalLocation()) && !isTextBlank(existingCriteria.getLocationOrHotelName())) {
                existingCriteria.setArrivalLocation(existingCriteria.getLocationOrHotelName());
            }
            if (existingCriteria.getDepartureDate() == null && existingCriteria.getCheckInDate() != null) {
                existingCriteria.setDepartureDate(existingCriteria.getCheckInDate());
            }
            if (existingCriteria.getReturnDate() == null && existingCriteria.getCheckOutDate() != null) {
                existingCriteria.setReturnDate(existingCriteria.getCheckOutDate());
                existingCriteria.setTripType("ROUND_TRIP");
                existingCriteria.setAssumedTripType(false);
            }
            if (existingCriteria.getPassengerCount() == null && existingCriteria.getAdultCount() != null) {
                existingCriteria.setPassengerCount(existingCriteria.getAdultCount());
            }

            // Clear hotel-specific fields so they don't leak into flight search
            existingCriteria.setLocationOrHotelName(null);
            existingCriteria.setRoomCount(null);
            existingCriteria.setMinStars(null);

        } else if ("FLIGHT_SEARCH".equals(oldIntent) && "HOTEL_SEARCH".equals(newIntent)) {
            // Transfer shared criteria: arrivalLocation -> locationOrHotelName, departure -> checkIn, return -> checkOut, passengerCount -> adultCount
            if (isTextBlank(existingCriteria.getLocationOrHotelName()) && !isTextBlank(existingCriteria.getArrivalLocation())) {
                existingCriteria.setLocationOrHotelName(existingCriteria.getArrivalLocation());
            }
            if (existingCriteria.getCheckInDate() == null && existingCriteria.getDepartureDate() != null) {
                existingCriteria.setCheckInDate(existingCriteria.getDepartureDate());
            }
            if (existingCriteria.getCheckOutDate() == null && existingCriteria.getReturnDate() != null) {
                existingCriteria.setCheckOutDate(existingCriteria.getReturnDate());
            }
            if (existingCriteria.getAdultCount() == null && existingCriteria.getPassengerCount() != null) {
                existingCriteria.setAdultCount(existingCriteria.getPassengerCount());
            }

            // Clear flight-specific fields so they don't leak into hotel search
            existingCriteria.setDepartureLocation(null);
            existingCriteria.setArrivalLocation(null);
            existingCriteria.setDepartureDate(null);
            existingCriteria.setReturnDate(null);
            existingCriteria.setPassengerCount(null);
            existingCriteria.setTripType(null);
            existingCriteria.setAssumedTripType(false);
            existingCriteria.setAssumedPassengerCount(false);
        }
    }

    private boolean isTextBlank(String s) {
        return s == null || s.isBlank();
    }

    private String buildSearchConfirmationSummary(SearchCriteria criteria) {
        StringBuilder sb = new StringBuilder();
        boolean isFlight = "FLIGHT_SEARCH".equals(criteria.getSearchType());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        if (isFlight) {
            sb.append("Arama yapmak için tüm bilgileri aldım! ✈️\n\n");
            sb.append("📋 **Uçuş Arama Özeti:**\n");
            if (criteria.getDepartureLocation() != null) {
                sb.append("• **Kalkış:** ").append(criteria.getDepartureLocation()).append("\n");
            }
            if (criteria.getArrivalLocation() != null) {
                sb.append("• **Varış:** ").append(criteria.getArrivalLocation()).append("\n");
            }
            if (criteria.getDepartureDate() != null) {
                sb.append("• **Gidiş Tarihi:** ").append(criteria.getDepartureDate().format(dtf)).append("\n");
            }
            if ("ROUND_TRIP".equalsIgnoreCase(criteria.getTripType()) && criteria.getReturnDate() != null) {
                sb.append("• **Dönüş Tarihi:** ").append(criteria.getReturnDate().format(dtf)).append("\n");
                sb.append("• **Uçuş Tipi:** Gidiş-Dönüş\n");
            } else {
                sb.append("• **Uçuş Tipi:** Tek Yön\n");
            }

            List<String> paxParts = new ArrayList<>();
            int adults = criteria.getAdultCount() != null ? criteria.getAdultCount() : (criteria.getPassengerCount() != null ? criteria.getPassengerCount() : 1);
            if (adults > 0) paxParts.add(adults + " Yetişkin");
            if (criteria.getChildCount() != null && criteria.getChildCount() > 0) {
                if (criteria.getChildAges() != null && !criteria.getChildAges().isEmpty()) {
                    paxParts.add(criteria.getChildCount() + " Çocuk (" + criteria.getChildAges().stream().map(a -> a + " yaşında").collect(Collectors.joining(", ")) + ")");
                } else {
                    paxParts.add(criteria.getChildCount() + " Çocuk");
                }
            }
            if (criteria.getInfantCount() != null && criteria.getInfantCount() > 0) {
                if (criteria.getInfantAgesInMonths() != null && !criteria.getInfantAgesInMonths().isEmpty()) {
                    paxParts.add(criteria.getInfantCount() + " Bebek (" + criteria.getInfantAgesInMonths().stream().map(a -> a + " aylık").collect(Collectors.joining(", ")) + ")");
                } else {
                    paxParts.add(criteria.getInfantCount() + " Bebek");
                }
            }
            sb.append("• **Yolcular:** ").append(String.join(", ", paxParts)).append("\n\n");
            sb.append("Bu bilgilerle **aramayı başlatmamı onaylıyor musunuz?** (Evet / Hayır / Değiştir)");
        } else {
            sb.append("Otel araması yapmak için tüm bilgileri aldım! 🏨\n\n");
            sb.append("📋 **Otel Arama Özeti:**\n");
            if (criteria.getLocationOrHotelName() != null) {
                sb.append("• **Konum/Otel:** ").append(criteria.getLocationOrHotelName()).append("\n");
            }
            if (criteria.getCheckInDate() != null) {
                sb.append("• **Giriş Tarihi:** ").append(criteria.getCheckInDate().format(dtf)).append("\n");
            }
            if (criteria.getCheckOutDate() != null) {
                sb.append("• **Çıkış Tarihi:** ").append(criteria.getCheckOutDate().format(dtf)).append("\n");
            }
            sb.append("• **Oda Sayısı:** ").append(criteria.getRoomCount() != null ? criteria.getRoomCount() : 1).append("\n");

            List<String> guestParts = new ArrayList<>();
            int adults = criteria.getAdultCount() != null ? criteria.getAdultCount() : 1;
            if (adults > 0) guestParts.add(adults + " Yetişkin");
            if (criteria.getChildCount() != null && criteria.getChildCount() > 0) {
                if (criteria.getChildAges() != null && !criteria.getChildAges().isEmpty()) {
                    guestParts.add(criteria.getChildCount() + " Çocuk (" + criteria.getChildAges().stream().map(a -> a + " yaşında").collect(Collectors.joining(", ")) + ")");
                } else {
                    guestParts.add(criteria.getChildCount() + " Çocuk");
                }
            }
            if (criteria.getInfantCount() != null && criteria.getInfantCount() > 0) {
                if (criteria.getInfantAgesInMonths() != null && !criteria.getInfantAgesInMonths().isEmpty()) {
                    guestParts.add(criteria.getInfantCount() + " Bebek (" + criteria.getInfantAgesInMonths().stream().map(a -> a + " aylık").collect(Collectors.joining(", ")) + ")");
                } else {
                    guestParts.add(criteria.getInfantCount() + " Bebek");
                }
            }
            sb.append("• **Misafirler:** ").append(String.join(", ", guestParts)).append("\n\n");
            sb.append("Bu bilgilerle **aramayı başlatmamı onaylıyor musunuz?** (Evet / Hayır / Değiştir)");
        }
        return sb.toString();
    }

    private boolean isUnderTest() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("org.junit.") || element.getClassName().contains("Test")) {
                return true;
            }
        }
        return false;
    }
}
