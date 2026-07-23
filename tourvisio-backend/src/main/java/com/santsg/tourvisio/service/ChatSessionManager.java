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
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    // Autowired constructor
    @org.springframework.beans.factory.annotation.Autowired
    public ChatSessionManager(ChatSessionRepository chatSessionRepository,
            ChatSessionStore chatSessionStore,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatSessionStore = chatSessionStore;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // Default constructor for testing fallback
    public ChatSessionManager() {
        this.chatSessionRepository = null;
        this.chatSessionStore = null;
        this.userRepository = null;
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

        public boolean isLastSearchHadNoResults() { return lastSearchHadNoResults; }
        public void setLastSearchHadNoResults(boolean lastSearchHadNoResults) { this.lastSearchHadNoResults = lastSearchHadNoResults; }

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

        public void incrementOutOfScopeCount() {
            this.outOfScopeCount++;
            if (this.outOfScopeCount >= 3) {
                this.chatStatus = "TERMINATED";
            }
        }

        public void resetOutOfScopeCount() {
            this.outOfScopeCount = 0;
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

    @Transactional
    public void updateChatStatus(String sessionId, String newStatus) {
        SessionState state = getSessionState(sessionId);
        if (state != null) {
            state.setChatStatus(newStatus);
            saveSession(state);
        }
    }

    public List<SessionSummaryResponse> getAllSessionSummaries() {
        if (chatSessionRepository != null) {
            return chatSessionRepository.findAll().stream()
                    .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                    .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                    .collect(Collectors.toList());
        }

        return sessions.values().stream()
                .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                .collect(Collectors.toList());
    }

    public List<SessionSummaryResponse> getSessionSummariesForUser(Long userId) {
        if (chatSessionRepository != null) {
            return chatSessionRepository.findByUserIdOrderByLastMessageTimestampDesc(userId).stream()
                    .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                    .collect(Collectors.toList());
        }

        return sessions.values().stream()
                .filter(s -> userId == null ? s.getUserId() == null : userId.equals(s.getUserId()))
                .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                .collect(Collectors.toList());
    }
}
