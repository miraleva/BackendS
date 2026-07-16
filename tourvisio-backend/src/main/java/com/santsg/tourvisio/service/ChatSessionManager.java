package com.santsg.tourvisio.service;
 
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
 
@Service
public class ChatSessionManager {
 
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
        private Long userId;
        private String id;
        private String title = "New Chat Session";
        private java.time.Instant lastMessageTimestamp = java.time.Instant.now();
        private int outOfScopeCount = 0;
        private String chatStatus = "ACTIVE";
        private String mode = "GATHERING";
        private java.util.List<?> lastShownResults;
        private Object selectedItem;
        private final java.util.List<MessageHistoryItem> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
        private String lastRequestedField;
 
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        
        public java.util.List<?> getLastShownResults() { return lastShownResults; }
        public void setLastShownResults(java.util.List<?> lastShownResults) { this.lastShownResults = lastShownResults; }
        
        public Object getSelectedItem() { return selectedItem; }
        public void setSelectedItem(Object selectedItem) { this.selectedItem = selectedItem; }

        public String getLastRequestedField() {
            return lastRequestedField;
        }

        public void setLastRequestedField(String lastRequestedField) {
            this.lastRequestedField = lastRequestedField;
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
 
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
 
    public SessionState getOrCreateSession(String sessionId) {
        return getOrCreateSession(sessionId, null);
    }

    public SessionState getOrCreateSession(String sessionId, Long userId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "default-session";
        }
        final String finalSessionId = sessionId;
        SessionState state = sessions.computeIfAbsent(sessionId, k -> {
            SessionState s = new SessionState();
            s.setId(finalSessionId);
            s.setUserId(userId);
            return s;
        });
        if (state.getUserId() == null && userId != null) {
            state.setUserId(userId);
        }
        return state;
    }
 
    public void removeSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
 
    public SessionState getSessionState(String sessionId) {
        return sessions.get(sessionId);
    }
 
    public java.util.List<SessionSummaryResponse> getAllSessionSummaries() {
        return sessions.values().stream()
                .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                .collect(java.util.stream.Collectors.toList());
    }

    public java.util.List<SessionSummaryResponse> getSessionSummariesForUser(Long userId) {
        return sessions.values().stream()
                .filter(s -> userId == null ? s.getUserId() == null : userId.equals(s.getUserId()))
                .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                .collect(java.util.stream.Collectors.toList());
    }
}
