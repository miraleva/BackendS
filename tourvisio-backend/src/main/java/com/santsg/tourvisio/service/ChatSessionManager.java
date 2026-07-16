package com.santsg.tourvisio.service;

import com.santsg.tourvisio.entity.ChatSession;
import com.santsg.tourvisio.entity.ChatMessage;
import com.santsg.tourvisio.repository.ChatSessionRepository;
import com.santsg.tourvisio.repository.ChatMessageRepository;
import com.santsg.tourvisio.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatSessionManager {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    private final java.util.Map<String, SessionState> fallbackSessions;

    // Autowired constructor
    @org.springframework.beans.factory.annotation.Autowired
    public ChatSessionManager(ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.fallbackSessions = null;
    }

    // Default constructor for testing/manual initialization
    public ChatSessionManager() {
        this.sessionRepository = null;
        this.messageRepository = null;
        this.userRepository = null;
        this.fallbackSessions = new java.util.concurrent.ConcurrentHashMap<>();
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
        private final ChatSession entity;
        private final ChatSessionRepository sessionRepo;
        private final ChatMessageRepository messageRepo;
        private final UserRepository userRepo;

        private Long userId;
        private String id;
        private String title = "New Chat Session";
        private java.time.Instant lastMessageTimestamp = java.time.Instant.now();
        private int outOfScopeCount = 0;
        private String chatStatus = "ACTIVE";

        private String mode = "GATHERING";
        private java.util.List<?> lastShownResults;
        private Object selectedItem;
        private String lastRequestedField;

        private final DelegatingList messages = new DelegatingList();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
            if (entity != null) {
                entity.setMode(mode);
                sessionRepo.save(entity);
            }
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

        // Default constructor for fallback mode
        public SessionState() {
            this.entity = null;
            this.sessionRepo = null;
            this.messageRepo = null;
            this.userRepo = null;
        }

        // DB mode constructor
        public SessionState(ChatSession entity,
                ChatSessionRepository sessionRepo,
                ChatMessageRepository messageRepo,
                UserRepository userRepo) {
            this.entity = entity;
            this.sessionRepo = sessionRepo;
            this.messageRepo = messageRepo;
            this.userRepo = userRepo;

            // Hydrate local variables from entity
            this.userId = entity.getUser() != null ? entity.getUser().getId() : null;
            this.id = entity.getId();
            this.title = entity.getTitle();
            this.lastMessageTimestamp = entity.getLastMessageTimestamp();
            this.outOfScopeCount = entity.getOutOfScopeCount();
            this.chatStatus = entity.getChatStatus();
            this.mode = entity.getMode() != null ? entity.getMode() : "GATHERING";
            this.lastRequestedField = entity.getLastRequestedField();

            // Populate existing messages into delegate
            if (entity.getMessages() != null) {
                for (ChatMessage m : entity.getMessages()) {
                    this.messages.delegate
                            .add(new MessageHistoryItem(m.getSender(), m.getMessageText(), m.getCreatedAt(), null));
                }
            }
        }

        private class DelegatingList extends java.util.AbstractList<MessageHistoryItem> {
            final List<MessageHistoryItem> delegate = new java.util.concurrent.CopyOnWriteArrayList<>();

            @Override
            public MessageHistoryItem get(int index) {
                return delegate.get(index);
            }

            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public boolean add(MessageHistoryItem item) {
                addMessageToDb(item);
                return delegate.add(item);
            }

            @Override
            public void add(int index, MessageHistoryItem element) {
                addMessageToDb(element);
                delegate.add(index, element);
            }
        }

        private void addMessageToDb(MessageHistoryItem item) {
            if (messageRepo == null) {
                return;
            }
            ChatMessage chatMessage = ChatMessage.builder()
                    .session(entity)
                    .sender(item.getSender())
                    .messageText(item.getText())
                    .createdAt(item.getTimestamp() != null ? item.getTimestamp() : java.time.Instant.now())
                    .build();
            messageRepo.save(chatMessage);
        }

        public String getLastRequestedField() {
            return lastRequestedField;
        }

        public void setLastRequestedField(String lastRequestedField) {
            this.lastRequestedField = lastRequestedField;
            if (entity != null) {
                entity.setLastRequestedField(lastRequestedField);
                sessionRepo.save(entity);
            }
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
            if (entity != null) {
                if (userId != null) {
                    userRepo.findById(userId).ifPresent(entity::setUser);
                } else {
                    entity.setUser(null);
                }
                sessionRepo.save(entity);
            }
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
            if (entity != null) {
                entity.setId(id);
                sessionRepo.save(entity);
            }
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
            if (entity != null) {
                entity.setTitle(title);
                sessionRepo.save(entity);
            }
        }

        public java.time.Instant getLastMessageTimestamp() {
            return lastMessageTimestamp;
        }

        public void setLastMessageTimestamp(java.time.Instant lastMessageTimestamp) {
            this.lastMessageTimestamp = lastMessageTimestamp;
            if (entity != null) {
                entity.setLastMessageTimestamp(lastMessageTimestamp);
                sessionRepo.save(entity);
            }
        }

        public int getOutOfScopeCount() {
            return outOfScopeCount;
        }

        public void incrementOutOfScopeCount() {
            this.outOfScopeCount++;
            if (entity != null) {
                entity.setOutOfScopeCount(this.outOfScopeCount);
            }
            if (this.outOfScopeCount >= 3) {
                this.chatStatus = "TERMINATED";
                if (entity != null) {
                    entity.setChatStatus("TERMINATED");
                }
            }
            if (entity != null) {
                sessionRepo.save(entity);
            }
        }

        public String getChatStatus() {
            return chatStatus;
        }

        public void setChatStatus(String chatStatus) {
            this.chatStatus = chatStatus;
            if (entity != null) {
                entity.setChatStatus(chatStatus);
                sessionRepo.save(entity);
            }
        }

        public List<MessageHistoryItem> getMessages() {
            return messages;
        }
    }

    public SessionState getOrCreateSession(String sessionId) {
        return getOrCreateSession(sessionId, null);
    }

    public SessionState getOrCreateSession(String sessionId, Long userId) {
        if (sessionRepository == null) {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = "default-session";
            }
            final String finalSessionId = sessionId;
            SessionState state = fallbackSessions.computeIfAbsent(sessionId, k -> {
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

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        final String finalSessionId = sessionId;
        ChatSession chatSession = sessionRepository.findById(sessionId)
                .orElseGet(() -> {
                    ChatSession newSession = new ChatSession();
                    newSession.setId(finalSessionId);
                    if (userId != null) {
                        userRepository.findById(userId).ifPresent(newSession::setUser);
                    }
                    return sessionRepository.save(newSession);
                });
        if (chatSession.getUser() == null && userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                chatSession.setUser(user);
                sessionRepository.save(chatSession);
            });
        }
        return new SessionState(chatSession, sessionRepository, messageRepository, userRepository);
    }

    public void removeSession(String sessionId) {
        if (sessionRepository == null) {
            if (sessionId != null) {
                fallbackSessions.remove(sessionId);
            }
            return;
        }
        if (sessionId != null) {
            sessionRepository.deleteById(sessionId);
        }
    }

    public SessionState getSessionState(String sessionId) {
        if (sessionRepository == null) {
            return fallbackSessions.get(sessionId);
        }
        return sessionRepository.findById(sessionId)
                .map(session -> new SessionState(session, sessionRepository, messageRepository, userRepository))
                .orElse(null);
    }

    public List<SessionSummaryResponse> getAllSessionSummaries() {
        if (sessionRepository == null) {
            return fallbackSessions.values().stream()
                    .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                    .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                    .collect(Collectors.toList());
        }
        return sessionRepository.findAll().stream()
                .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                .collect(Collectors.toList());
    }

    public List<SessionSummaryResponse> getSessionSummariesForUser(Long userId) {
        if (sessionRepository == null) {
            return fallbackSessions.values().stream()
                    .filter(s -> userId == null ? s.getUserId() == null : userId.equals(s.getUserId()))
                    .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                    .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                    .collect(Collectors.toList());
        }
        return sessionRepository.findAll().stream()
                .filter(s -> userId == null ? s.getUser() == null
                        : (s.getUser() != null && userId.equals(s.getUser().getId())))
                .map(s -> new SessionSummaryResponse(s.getId(), s.getTitle(), s.getLastMessageTimestamp()))
                .sorted((s1, s2) -> s2.getLastMessageTimestamp().compareTo(s1.getLastMessageTimestamp()))
                .collect(Collectors.toList());
    }
}
