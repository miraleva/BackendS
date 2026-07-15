package com.santsg.tourvisio.chat;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oturum bazlı arama kriterlerini bellekte tutan depo.
 *
 * <p>
 * Her {@code sessionId} için tek bir {@link SearchCriteria} nesnesi
 * saklanır; mesajlar arası birikim bu nesne üzerinden yapılır.
 * </p>
 *
 * <p>
 * <strong>Genişletme notu:</strong> Bu sınıf yalnızca bir bellek deposudur.
 * İleride {@code ChatSession} JPA entity'siyle veya Redis/Hazelcast gibi
 * bir dağıtık cache ile değiştirilebilir; çağıran kod değişmez.
 * </p>
 */
@Component
public class ChatSessionStore {

    /**
     * sessionId → biriktirilmiş arama kriterleri
     *
     * <p>
     * Şimdilik uygulama yeniden başlayana kadar bellekte tutulur.
     * Üretim ortamı için TTL eklenebilir (örn. Caffeine cache).
     * </p>
     */
    private final Map<String, SearchCriteria> store = new ConcurrentHashMap<>();

    /**
     * Var olan kriteri döner; yoksa yeni boş bir {@link SearchCriteria} oluşturur.
     */
    public SearchCriteria getOrCreate(String sessionId) {
        return store.computeIfAbsent(sessionId, id -> new SearchCriteria());
    }

    /**
     * Güncellenmiş kriteri oturuma yazar.
     */
    public void save(String sessionId, SearchCriteria criteria) {
        store.put(sessionId, criteria);
    }

    /**
     * Oturumu siler (sohbet bitince temizlik için).
     */
    public void remove(String sessionId) {
        store.remove(sessionId);
    }

    /**
     * Test/diagnostic amaçlı: oturum var mı?
     */
    public boolean exists(String sessionId) {
        return store.containsKey(sessionId);
    }
}
