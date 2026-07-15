package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.config.ActiveTokenRegistry;
import com.santsg.tourvisio.entity.InMemorySnapshot;
import com.santsg.tourvisio.repository.InMemorySnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SnapshotServiceTest {

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private ChatSessionManager chatSessionManager;

    @Autowired
    private ChatSessionStore chatSessionStore;

    @Autowired
    private ActiveTokenRegistry activeTokenRegistry;

    @Autowired
    private InMemorySnapshotRepository snapshotRepository;

    @BeforeEach
    void setUp() {
        // Clear all state before each test
        chatSessionManager.getSessionsMap().clear();
        chatSessionStore.getStoreMap().clear();
        activeTokenRegistry.getActiveTokensSet().clear();
        snapshotRepository.deleteAll();
    }

    @Test
    void testSaveAndRestoreSnapshot() {
        // 1. Populate initial test data in-memory
        String sessionId = "test-session-123";

        // Chat Session State
        ChatSessionManager.SessionState originalSession = chatSessionManager.getOrCreateSession(sessionId, 42L);
        originalSession.setTitle("Hotel Search Antalya");
        originalSession.setChatStatus("ACTIVE");
        originalSession.setLastRequestedField("check_in_date");
        originalSession.getMessages().add(
                new ChatSessionManager.MessageHistoryItem("user", "I want to search a hotel in Antalya", java.time.Instant.now(), null)
        );
        originalSession.getMessages().add(
                new ChatSessionManager.MessageHistoryItem("bot", "Sure, what are the check-in and check-out dates?", java.time.Instant.now(), null)
        );

        // Search Criteria
        SearchCriteria originalCriteria = chatSessionStore.getOrCreate(sessionId);
        originalCriteria.setSearchType("HOTEL_SEARCH");
        originalCriteria.setLocationOrHotelName("Antalya");
        originalCriteria.setCheckInDate(LocalDate.of(2026, 8, 1));
        originalCriteria.setCheckOutDate(LocalDate.of(2026, 8, 10));
        originalCriteria.setAdultCount(2);
        originalCriteria.setChildCount(1);
        originalCriteria.getChildAges().add(6);
        chatSessionStore.save(sessionId, originalCriteria);

        // Active Tokens
        String token = "sample-bearer-token-xyz";
        activeTokenRegistry.registerToken(token);

        // 2. Perform Save
        snapshotService.saveSnapshots();

        // Verify snapshots are in database
        Iterable<InMemorySnapshot> snapshots = snapshotRepository.findAll();
        assertThat(snapshots).hasSize(3);

        // 3. Clear In-Memory Stores
        chatSessionManager.getSessionsMap().clear();
        chatSessionStore.getStoreMap().clear();
        activeTokenRegistry.getActiveTokensSet().clear();

        assertThat(chatSessionManager.getSessionsMap()).isEmpty();
        assertThat(chatSessionStore.getStoreMap()).isEmpty();
        assertThat(activeTokenRegistry.getActiveTokensSet()).isEmpty();

        // 4. Perform Restore
        snapshotService.restoreSnapshots();

        // 5. Assert Restored Data
        // Chat Session Assertions
        Map<String, ChatSessionManager.SessionState> restoredSessions = chatSessionManager.getSessionsMap();
        assertThat(restoredSessions).containsKey(sessionId);
        ChatSessionManager.SessionState restoredSession = restoredSessions.get(sessionId);
        assertThat(restoredSession.getUserId()).isEqualTo(42L);
        assertThat(restoredSession.getTitle()).isEqualTo("Hotel Search Antalya");
        assertThat(restoredSession.getChatStatus()).isEqualTo("ACTIVE");
        assertThat(restoredSession.getLastRequestedField()).isEqualTo("check_in_date");
        assertThat(restoredSession.getMessages()).hasSize(2);
        assertThat(restoredSession.getMessages().get(0).getSender()).isEqualTo("user");
        assertThat(restoredSession.getMessages().get(0).getText()).isEqualTo("I want to search a hotel in Antalya");

        // Search Criteria Assertions
        Map<String, SearchCriteria> restoredCriteriaMap = chatSessionStore.getStoreMap();
        assertThat(restoredCriteriaMap).containsKey(sessionId);
        SearchCriteria restoredCriteria = restoredCriteriaMap.get(sessionId);
        assertThat(restoredCriteria.getSearchType()).isEqualTo("HOTEL_SEARCH");
        assertThat(restoredCriteria.getLocationOrHotelName()).isEqualTo("Antalya");
        assertThat(restoredCriteria.getCheckInDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(restoredCriteria.getCheckOutDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(restoredCriteria.getAdultCount()).isEqualTo(2);
        assertThat(restoredCriteria.getChildCount()).isEqualTo(1);
        assertThat(restoredCriteria.getChildAges()).containsExactly(6);

        // Active Token Assertions
        Set<String> restoredTokens = activeTokenRegistry.getActiveTokensSet();
        assertThat(restoredTokens).contains(token);
    }
}
