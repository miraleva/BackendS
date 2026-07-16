package com.santsg.tourvisio.chat;

import com.santsg.tourvisio.entity.ChatSession;
import com.santsg.tourvisio.repository.ChatSessionRepository;
import org.springframework.stereotype.Component;

/**
 * Oturum bazlı arama kriterlerini veritabanında (veya fallback olarak bellekte) tutan depo.
 */
@Component
public class ChatSessionStore {

    private final ChatSessionRepository chatSessionRepository;
    private final java.util.Map<String, SearchCriteria> fallbackStore;

    // Autowired constructor
    @org.springframework.beans.factory.annotation.Autowired
    public ChatSessionStore(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.fallbackStore = null;
    }

    // No-arg constructor for fallback mode in testing / manual initialization
    public ChatSessionStore() {
        this.chatSessionRepository = null;
        this.fallbackStore = new java.util.concurrent.ConcurrentHashMap<>();
    }

    /**
     * Var olan kriteri döner; yoksa yeni boş bir {@link SearchCriteria} oluşturur.
     */
    public SearchCriteria getOrCreate(String sessionId) {
        if (chatSessionRepository == null) {
            return fallbackStore.computeIfAbsent(sessionId, id -> new SearchCriteria());
        }
        return chatSessionRepository.findById(sessionId)
                .map(ChatSession::getSearchCriteria)
                .orElseGet(SearchCriteria::new);
    }

    /**
     * Güncellenmiş kriteri oturuma yazar.
     */
    public void save(String sessionId, SearchCriteria criteria) {
        if (chatSessionRepository == null) {
            fallbackStore.put(sessionId, criteria);
            return;
        }
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseGet(() -> {
                    ChatSession s = new ChatSession();
                    s.setId(sessionId);
                    return s;
                });
        session.setSearchCriteria(criteria);
        chatSessionRepository.save(session);
    }

    /**
     * Oturumu siler (sohbet bitince temizlik için).
     */
    public void remove(String sessionId) {
        if (chatSessionRepository == null) {
            fallbackStore.remove(sessionId);
            return;
        }
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setSearchCriteria(new SearchCriteria());
            chatSessionRepository.save(session);
        });
    }

    /**
     * Test/diagnostic amaçlı: oturum var mı?
     */
    public boolean exists(String sessionId) {
        if (chatSessionRepository == null) {
            return fallbackStore.containsKey(sessionId);
        }
        return chatSessionRepository.existsById(sessionId);
    }
}
