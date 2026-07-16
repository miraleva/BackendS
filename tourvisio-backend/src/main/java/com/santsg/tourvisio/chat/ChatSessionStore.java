package com.santsg.tourvisio.chat;

import com.santsg.tourvisio.entity.ChatSession;
import com.santsg.tourvisio.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Oturum bazlı arama kriterlerini veritabanında (veya fallback olarak bellekte)
 * tutan depo.
 */
@Component
public class ChatSessionStore {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionStore.class);

    private final ChatSessionRepository chatSessionRepository;
    private final Map<String, SearchCriteria> store = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    // Autowired constructor
    @org.springframework.beans.factory.annotation.Autowired
    public ChatSessionStore(ChatSessionRepository chatSessionRepository, ObjectMapper objectMapper) {
        this.chatSessionRepository = chatSessionRepository;
        this.objectMapper = objectMapper;
    }

    // No-arg constructor for fallback mode in testing / manual initialization
    public ChatSessionStore() {
        this.chatSessionRepository = null;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public Map<String, SearchCriteria> getStoreMap() {
        return this.store;
    }

    public void restoreStoreMap(Map<String, SearchCriteria> restoredStore) {
        this.store.clear();
        if (restoredStore != null) {
            this.store.putAll(restoredStore);
        }
    }

    /**
     * Var olan kriteri döner; yoksa yeni boş bir {@link SearchCriteria} oluşturur.
     */
    public SearchCriteria getOrCreate(String sessionId) {
        if (chatSessionRepository == null) {
            return store.computeIfAbsent(sessionId, id -> new SearchCriteria());
        }
        return chatSessionRepository.findById(sessionId)
                .map(session -> {
                    if (session.getSearchCriteriaJson() != null && !session.getSearchCriteriaJson().isBlank()) {
                        try {
                            return objectMapper.readValue(session.getSearchCriteriaJson(), SearchCriteria.class);
                        } catch (Exception e) {
                            log.error("Failed to deserialize SearchCriteria from JSON", e);
                        }
                    }
                    return new SearchCriteria();
                })
                .orElseGet(() -> store.computeIfAbsent(sessionId, id -> new SearchCriteria()));
    }

    /**
     * Güncellenmiş kriteri oturuma yazar.
     */
    public void save(String sessionId, SearchCriteria criteria) {
        store.put(sessionId, criteria);
        if (chatSessionRepository == null) {
            return;
        }
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseGet(() -> {
                    ChatSession s = new ChatSession();
                    s.setId(sessionId);
                    return s;
                });
        try {
            session.setSearchCriteriaJson(objectMapper.writeValueAsString(criteria));
            chatSessionRepository.save(session);
        } catch (Exception e) {
            log.error("Failed to serialize SearchCriteria to JSON", e);
        }
    }

    /**
     * Oturumu siler (sohbet bitince temizlik için).
     */
    public void remove(String sessionId) {
        store.remove(sessionId);
        if (chatSessionRepository == null) {
            return;
        }
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setSearchCriteriaJson(null);
            chatSessionRepository.save(session);
        });
    }

    /**
     * Test/diagnostic amaçlı: oturum var mı?
     */
    public boolean exists(String sessionId) {
        if (chatSessionRepository == null) {
            return store.containsKey(sessionId);
        }
        return chatSessionRepository.existsById(sessionId) || store.containsKey(sessionId);
    }
}
