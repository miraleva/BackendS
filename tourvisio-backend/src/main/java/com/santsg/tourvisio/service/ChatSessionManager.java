package com.santsg.tourvisio.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatSessionManager {

    public static class SessionState {
        private int outOfScopeCount = 0;
        private String chatStatus = "ACTIVE";

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
    }

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionState getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "default-session";
        }
        return sessions.computeIfAbsent(sessionId, k -> new SessionState());
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
}
