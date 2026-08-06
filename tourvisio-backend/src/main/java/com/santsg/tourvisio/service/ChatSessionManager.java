package com.santsg.tourvisio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.entity.ChatMessage;
import com.santsg.tourvisio.entity.ChatSession;
import com.santsg.tourvisio.repository.ChatSessionRepository;
import com.santsg.tourvisio.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ChatSessionManager {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatSessionStore chatSessionStore;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final com.santsg.tourvisio.repository.ReservationRepository reservationRepository;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    // Autowired constructor
    @org.springframework.beans.factory.annotation.Autowired
    public ChatSessionManager(ChatSessionRepository chatSessionRepository,
            ChatSessionStore chatSessionStore,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.santsg.tourvisio.repository.ReservationRepository reservationRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatSessionStore = chatSessionStore;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.reservationRepository = reservationRepository;
    }

    // Default constructor for testing fallback
    public ChatSessionManager() {
        this.chatSessionRepository = null;
        this.chatSessionStore = null;
        this.userRepository = null;
        this.reservationRepository = null;
        this.objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Builder
    public static class SessionSummaryResponse {
        private String id;
        private String title;
        private java.time.Instant lastMessageTimestamp;
        private String category;
        private String snippet;

        public SessionSummaryResponse(String id, String title, java.time.Instant lastMessageTimestamp) {
            this.id = id;
            this.title = title;
            this.lastMessageTimestamp = lastMessageTimestamp;
            this.category = "General SOP";
            this.snippet = "";
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Builder
    public static class MessageHistoryItem {
        private String sender; // "user" or "bot"
        private String text;
        private java.time.Instant timestamp;
        private java.util.List<?> results;
    }

    public static class SessionState {
        private String id;
        private Long userId;
        private String title = "New Chat Session";
        private java.time.Instant lastMessageTimestamp = java.time.Instant.now();
        private int outOfScopeCount = 0;
        private String chatStatus = "ACTIVE";
        private String mode = "GATHERING";
        private java.util.List<?> lastShownResults;
        private Object selectedItem;
        private String lastRequestedField;
        private final java.util.List<MessageHistoryItem> messages = new java.util.concurrent.CopyOnWriteArrayList<>();

        public SessionState() {
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public java.util.List<?> getLastShownResults() {
            return lastShownResults;
        }

        public void setLastShownResults(java.util.List<?> lastShownResults) {
            this.lastShownResults = lastShownResults;
        }

        public Object getSelectedItem() {
            return selectedItem;
        }

        public void setSelectedItem(Object selectedItem) {
            this.selectedItem = selectedItem;
        }

        public String getLastRequestedField() {
            return lastRequestedField;
        }

        public void setLastRequestedField(String lastRequestedField) {
            this.lastRequestedField = lastRequestedField;
        }

        /**
         * Son arama sonuçsuz mu kaldı? Kullanıcı hiçbir yeni kriter vermeden
         * "en yakın tarih ne var" gibi bir soru sorduğunda, aynı (zaten
         * başarısız olduğu bilinen) tarihi tekrar aramak yerine doğrudan
         * yakın tarih önerisine atlamak için kullanılır.
         */
        private boolean lastSearchHadNoResults = false;

        public boolean isLastSearchHadNoResults() {
            return lastSearchHadNoResults;
        }

        public void setLastSearchHadNoResults(boolean lastSearchHadNoResults) {
            this.lastSearchHadNoResults = lastSearchHadNoResults;
        }

        private java.util.List<?> allSearchResults;
        private int resultOffset = 0;

        public java.util.List<?> getAllSearchResults() {
            return allSearchResults;
        }

        public void setAllSearchResults(java.util.List<?> allSearchResults) {
            this.allSearchResults = allSearchResults;
        }

        public int getResultOffset() {
            return resultOffset;
        }

        public void setResultOffset(int resultOffset) {
            this.resultOffset = resultOffset;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public java.time.Instant getLastMessageTimestamp() {
            return lastMessageTimestamp;
        }

        public void setLastMessageTimestamp(java.time.Instant lastMessageTimestamp) {
            this.lastMessageTimestamp = lastMessageTimestamp;
        }

        public int getOutOfScopeCount() {
            return outOfScopeCount;
        }

        public void setOutOfScopeCount(int outOfScopeCount) {
            this.outOfScopeCount = outOfScopeCount;
        }

        public int getIrrelevantCount() {
            return outOfScopeCount;
        }

        public int incrementIrrelevantCount() {
            this.outOfScopeCount++;
            if (this.outOfScopeCount >= 3) {
                this.chatStatus = "TERMINATED";
            }
            return this.outOfScopeCount;
        }

        public void resetIrrelevantCount() {
            this.outOfScopeCount = 0;
        }

        public void incrementOutOfScopeCount() {
            this.outOfScopeCount++;
            if (this.outOfScopeCount >= 3) {
                this.chatStatus = "TERMINATED";
            }
        }

        public String getChatStatus() {
            return chatStatus;
        }

        public void setChatStatus(String chatStatus) {
            this.chatStatus = chatStatus;
        }

        public java.util.List<MessageHistoryItem> getMessages() {
            return messages;
        }
    }

    private SessionState convertToSessionState(ChatSession entity) {
        SessionState s = new SessionState();
        s.setId(entity.getId());
        s.setUserId(entity.getUserId());
        s.setTitle(entity.getTitle());
        s.setLastMessageTimestamp(entity.getLastMessageTimestamp());
        s.setChatStatus(entity.getChatStatus());
        s.setMode(entity.getMode());
        s.setLastRequestedField(entity.getLastRequestedField());
        s.setOutOfScopeCount(entity.getOutOfScopeCount());

        // Restore messages
        if (entity.getMessages() != null) {
            for (ChatMessage msgEntity : entity.getMessages()) {
                java.util.List<?> results = null;
                if (msgEntity.getResultsJson() != null && !msgEntity.getResultsJson().isBlank()) {
                    try {
                        results = objectMapper.readValue(msgEntity.getResultsJson(),
                                new TypeReference<java.util.List<Object>>() {
                                });
                    } catch (Exception e) {
                        // ignore/log
                    }
                }
                String messageText = msgEntity.getText() != null ? msgEntity.getText() : "";
                java.time.Instant msgTimestamp = msgEntity.getTimestamp() != null ? msgEntity.getTimestamp()
                        : java.time.Instant.now();
                s.getMessages().add(new MessageHistoryItem(msgEntity.getSender(), messageText, msgTimestamp, results));
            }
        }

        // Restore SearchCriteria to ChatSessionStore
        if (chatSessionStore != null && entity.getSearchCriteriaJson() != null
                && !entity.getSearchCriteriaJson().isBlank()) {
            try {
                SearchCriteria criteria = objectMapper.readValue(entity.getSearchCriteriaJson(), SearchCriteria.class);
                chatSessionStore.save(entity.getId(), criteria);
            } catch (Exception e) {
                // ignore/log
            }
        }

        return s;
    }

    /**
     * Formats recent chat history messages into a string for LLM prompt context.
     *
     * @param state       The session state.
     * @param maxMessages The maximum number of recent messages to include.
     * @return Formatted conversation history string.
     */
    public String getRecentHistoryFormat(SessionState state, int maxMessages) {
        if (state == null || state.getMessages() == null || state.getMessages().isEmpty()) {
            return "";
        }
        java.util.List<MessageHistoryItem> messages = state.getMessages();
        int total = messages.size();
        int startIndex = Math.max(0, total - maxMessages);

        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < total; i++) {
            MessageHistoryItem item = messages.get(i);
            String role = "user".equalsIgnoreCase(item.getSender()) ? "User" : "Assistant";
            if (item.getText() != null && !item.getText().isBlank()) {
                sb.append("[").append(role).append("]: ").append(item.getText().trim()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    @Transactional
    public void saveSession(SessionState state) {
        if (chatSessionRepository == null) {
            return;
        }

        ChatSession entity = chatSessionRepository.findById(state.getId())
                .orElseGet(() -> ChatSession.builder().id(state.getId()).build());

        // Resolve and set User association for cascade delete capability
        if (state.getUserId() != null && userRepository != null) {
            userRepository.findById(state.getUserId()).ifPresent(entity::setUser);
        } else {
            entity.setUser(null);
        }

        entity.setTitle(state.getTitle());
        entity.setChatStatus(state.getChatStatus());
        entity.setMode(state.getMode());
        entity.setOutOfScopeCount(state.getOutOfScopeCount());
        entity.setLastRequestedField(state.getLastRequestedField());
        entity.setLastMessageTimestamp(
                state.getLastMessageTimestamp() != null ? state.getLastMessageTimestamp() : java.time.Instant.now());

        // Save SearchCriteria
        if (chatSessionStore != null) {
            SearchCriteria criteria = chatSessionStore.getOrCreate(state.getId());
            if (criteria != null) {
                try {
                    entity.setSearchCriteriaJson(objectMapper.writeValueAsString(criteria));
                } catch (Exception e) {
                    // ignore/log
                }
            }
        }

        // Sync messages: rebuild messages list to preserve order and keep collection
        // sync
        if (entity.getMessages() == null) {
            entity.setMessages(new java.util.ArrayList<>());
        } else {
            entity.getMessages().clear();
        }

        if (state.getMessages() != null) {
            for (MessageHistoryItem item : state.getMessages()) {
                String resultsJson = null;
                if (item.getResults() != null) {
                    try {
                        resultsJson = objectMapper.writeValueAsString(item.getResults());
                    } catch (Exception e) {
                        // ignore
                    }
                }
                ChatMessage msgEntity = ChatMessage.builder()
                        .session(entity)
                        .sender(item.getSender())
                        .text(item.getText())
                        .timestamp(item.getTimestamp() != null ? item.getTimestamp() : java.time.Instant.now())
                        .resultsJson(resultsJson)
                        .build();
                entity.getMessages().add(msgEntity);
            }
        }

        chatSessionRepository.save(entity);
    }

    public SessionState getOrCreateSession(String sessionId) {
        return getOrCreateSession(sessionId, null);
    }

    public SessionState getOrCreateSession(String sessionId, Long userId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 1. Check in-memory cache
        SessionState state = sessions.get(sessionId);
        if (state != null) {
            if (state.getUserId() == null && userId != null) {
                state.setUserId(userId);
                saveSession(state);
            }
            return state;
        }

        // 2. Check DB
        if (chatSessionRepository != null) {
            Optional<ChatSession> optSession = chatSessionRepository.findById(sessionId);
            if (optSession.isPresent()) {
                state = convertToSessionState(optSession.get());

                /*
                 * Veritabanından yüklenen oturum henüz bir kullanıcıya ait değilse
                 * ve istek giriş yapmış bir kullanıcıdan geldiyse, misafir oturumunu
                 * bu kullanıcıya bağla ve kalıcı olarak kaydet.
                 */
                if (state.getUserId() == null && userId != null) {
                    state.setUserId(userId);
                    saveSession(state);
                }

                sessions.put(sessionId, state);
                return state;
            }
        }

        // 3. Create new
        SessionState s = new SessionState();
        s.setId(sessionId);
        s.setUserId(userId);
        sessions.put(sessionId, s);

        saveSession(s);

        return s;
    }

    /**
     * Misafir olarak oluşturulmuş bir sohbet oturumunu giriş yapan kullanıcıya
     * bağlar.
     * Oturum başka bir kullanıcıya aitse güvenlik nedeniyle devralınmasına izin
     * vermez.
     */
    @Transactional
    public SessionState claimGuestSession(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session id cannot be empty");
        }

        if (userId == null) {
            throw new IllegalArgumentException("User id cannot be null");
        }

        SessionState state = getSessionState(sessionId);

        if (state == null) {
            throw new com.santsg.tourvisio.exception.ResourceNotFoundException(
                    "Session not found: " + sessionId);
        }

        if (state.getUserId() != null && !userId.equals(state.getUserId())) {
            throw new IllegalStateException(
                    "Session already belongs to another user");
        }

        if (state.getUserId() == null) {
            state.setUserId(userId);
            saveSession(state);
        }

        sessions.put(sessionId, state);
        return state;
    }

    @Transactional
    public void removeSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
            if (chatSessionRepository != null) {
                chatSessionRepository.deleteById(sessionId);
            }
            if (chatSessionStore != null) {
                chatSessionStore.remove(sessionId);
            }
        }
    }

    public SessionState getSessionState(String sessionId) {
        if (sessionId == null)
            return null;
        SessionState state = sessions.get(sessionId);
        if (state == null && chatSessionRepository != null) {
            Optional<ChatSession> optSession = chatSessionRepository.findById(sessionId);
            if (optSession.isPresent()) {
                state = convertToSessionState(optSession.get());
                sessions.put(sessionId, state);
            }
        }
        return state;
    }

    private String inferCategory(String title, String criteriaJson, List<ChatMessage> messages) {
        String t = (title != null ? title : "").toLowerCase();

        StringBuilder userMsgs = new StringBuilder();
        if (messages != null) {
            for (ChatMessage m : messages) {
                if ("user".equalsIgnoreCase(m.getSender()) && m.getText() != null) {
                    userMsgs.append(" ").append(m.getText().toLowerCase());
                }
            }
        }
        String combined = t + " " + userMsgs.toString();

        if (combined.contains("uçak") || combined.contains("uçuş") || combined.contains("bilet")
                || combined.contains("flight") || combined.contains("havayolu") || combined.contains("havalimanı")
                || combined.contains("havaalanı") || combined.contains("gidiş") || combined.contains("dönüş")) {
            return "Flight";
        }
        if (combined.contains("otel") || combined.contains("hotel") || combined.contains("konaklama")
                || combined.contains("oda") || combined.contains("resort") || combined.contains("pansiyon")) {
            return "Hotel";
        }
        if (combined.contains("transfer") || combined.contains("servis") || combined.contains("taksi")
                || combined.contains("shuttle")) {
            return "Transfer";
        }
        return "General SOP";
    }

    private String getLastSnippet(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty())
            return "";
        ChatMessage last = messages.get(messages.size() - 1);
        String text = last.getText();
        if (text == null)
            return "";
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }

    private SessionSummaryResponse mapToSummary(ChatSession s) {
        String cat = inferCategory(s.getTitle(), s.getSearchCriteriaJson(), s.getMessages());
        String snippet = getLastSnippet(s.getMessages());
        return new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp(), cat, snippet);
    }

    private SessionSummaryResponse mapStateToSummary(SessionState s) {
        String t = (s.getTitle() != null ? s.getTitle() : "").toLowerCase();

        StringBuilder userMsgs = new StringBuilder();
        if (s.getMessages() != null) {
            for (MessageHistoryItem m : s.getMessages()) {
                if ("user".equalsIgnoreCase(m.getSender()) && m.getText() != null) {
                    userMsgs.append(" ").append(m.getText().toLowerCase());
                }
            }
        }
        String combined = t + " " + userMsgs.toString();

        String cat = "General SOP";
        if (combined.contains("uçak") || combined.contains("uçuş") || combined.contains("bilet")
                || combined.contains("flight") || combined.contains("havayolu") || combined.contains("havalimanı")
                || combined.contains("havaalanı") || combined.contains("gidiş") || combined.contains("dönüş")) {
            cat = "Flight";
        } else if (combined.contains("otel") || combined.contains("hotel") || combined.contains("konaklama")
                || combined.contains("oda") || combined.contains("resort") || combined.contains("pansiyon")) {
            cat = "Hotel";
        } else if (combined.contains("transfer") || combined.contains("servis") || combined.contains("taksi")
                || combined.contains("shuttle")) {
            cat = "Transfer";
        }
        if (s.getMode() != null) {
            String mLower = s.getMode().toLowerCase();
            if ("hotel".equals(mLower) || "hotels".equals(mLower))
                cat = "Hotel";
            else if ("flight".equals(mLower) || "flights".equals(mLower))
                cat = "Flight";
            else if ("transfer".equals(mLower))
                cat = "Transfer";
        }

        String snippet = "";
        if (!s.getMessages().isEmpty()) {
            MessageHistoryItem last = s.getMessages().get(s.getMessages().size() - 1);
            if (last.getText() != null) {
                snippet = last.getText().length() > 120 ? last.getText().substring(0, 120) + "..." : last.getText();
            }
        }
        return new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp(), cat, snippet);
    }

    public List<SessionSummaryResponse> getAllSessionSummaries() {
        if (chatSessionRepository != null) {
            return chatSessionRepository.findAll().stream()
                    .map(this::mapToSummary)
                    .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                    .collect(Collectors.toList());
        }

        return sessions.values().stream()
                .map(this::mapStateToSummary)
                .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                .collect(Collectors.toList());
    }

    public List<SessionSummaryResponse> getSessionSummariesForUser(Long userId) {
        if (chatSessionRepository != null) {
            return chatSessionRepository.findByUserIdOrderByLastMessageTimestampDesc(userId).stream()
                    .map(this::mapToSummary)
                    .collect(Collectors.toList());
        }

        return sessions.values().stream()
                .filter(s -> userId == null ? s.getUserId() == null : userId.equals(s.getUserId()))
                .map(this::mapStateToSummary)
                .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                .collect(Collectors.toList());
    }

    public List<SessionSummaryResponse> searchSessionsForUser(Long userId, String query) {
        List<SessionSummaryResponse> list = getSessionSummariesForUser(userId);
        if (query == null || query.trim().isEmpty()) {
            return list;
        }
        String rawQuery = query.trim().toLowerCase();
        String cleanQuery = rawQuery.replaceAll("^(?i)pnr-?", "").trim();
        String alphaNumQuery = rawQuery.replaceAll("[^a-z0-9]", "");

        java.util.Set<String> matchingSessionIdsFromReservations = new java.util.HashSet<>();
        if (reservationRepository != null) {
            try {
                List<com.santsg.tourvisio.entity.Reservation> userReservations = (userId != null)
                        ? reservationRepository.findByUserId(userId)
                        : reservationRepository.findAll();
                for (com.santsg.tourvisio.entity.Reservation res : userReservations) {
                    if (res.getChatSessionId() == null)
                        continue;

                    String resNum = res.getReservationNumber() != null ? res.getReservationNumber().toLowerCase() : "";
                    String flightNo = res.getFlightNumber() != null ? res.getFlightNumber() : "";
                    if (flightNo.isBlank() && ("Flight".equalsIgnoreCase(res.getType())
                            || (res.getItemName() != null && (res.getItemName().toLowerCase().contains("ajet")
                                    || res.getItemName().toLowerCase().contains("uçuş")
                                    || res.getItemName().toLowerCase().contains("flight"))))) {
                        String pnrDigits = (res.getReservationNumber() != null ? res.getReservationNumber()
                                : String.valueOf(res.getId())).replaceAll("\\D", "");
                        int num = pnrDigits.length() >= 2
                                ? Math.abs(Integer.parseInt(pnrDigits.substring(Math.max(0, pnrDigits.length() - 4)))
                                        % 8999)
                                : 2024;
                        String prefix = (res.getItemName() != null
                                && res.getItemName().toLowerCase().contains("pegasus"))
                                        ? "PC"
                                        : (res.getItemName() != null && res.getItemName().toLowerCase().contains("thy"))
                                                ? "TK"
                                                : "VF";
                        flightNo = prefix + "-" + (1000 + num);
                    }

                    String alphaNumFlightNo = flightNo.toLowerCase().replaceAll("[^a-z0-9]", "");
                    boolean matchResNum = !resNum.isEmpty() && (resNum.contains(rawQuery) || resNum.contains(cleanQuery)
                            || resNum.replace("pnr-", "").contains(cleanQuery));
                    boolean matchFlightNo = !alphaNumFlightNo.isEmpty() && !alphaNumQuery.isEmpty()
                            && (alphaNumFlightNo.contains(alphaNumQuery) || alphaNumQuery.contains(alphaNumFlightNo));

                    if (matchResNum || matchFlightNo) {
                        matchingSessionIdsFromReservations.add(res.getChatSessionId());
                    }
                }
            } catch (Exception e) {
                // Ignore reservation lookup error
            }
        }

        return list.stream().filter(s -> {
            if (matchingSessionIdsFromReservations.contains(s.getId())) {
                return true;
            }

            boolean titleMatch = s.getTitle() != null && (s.getTitle().toLowerCase().contains(rawQuery)
                    || s.getTitle().toLowerCase().contains(cleanQuery));
            boolean categoryMatch = s.getCategory() != null && s.getCategory().toLowerCase().contains(rawQuery);
            boolean snippetMatch = s.getSnippet() != null && (s.getSnippet().toLowerCase().contains(rawQuery)
                    || s.getSnippet().toLowerCase().contains(cleanQuery));

            boolean messageMatch = false;
            SessionState state = sessions.get(s.getId());
            if (state != null) {
                messageMatch = state.getMessages().stream().anyMatch(m -> {
                    if (m.getText() == null)
                        return false;
                    String txt = m.getText().toLowerCase();
                    return txt.contains(rawQuery) || txt.contains(cleanQuery);
                });
            } else if (chatSessionRepository != null) {
                Optional<ChatSession> opt = chatSessionRepository.findById(s.getId());
                if (opt.isPresent() && opt.get().getMessages() != null) {
                    messageMatch = opt.get().getMessages().stream().anyMatch(m -> {
                        if (m.getText() == null)
                            return false;
                        String txt = m.getText().toLowerCase();
                        return txt.contains(rawQuery) || txt.contains(cleanQuery);
                    });
                }
            }
            return titleMatch || categoryMatch || snippetMatch || messageMatch;
        }).collect(Collectors.toList());
    }

}