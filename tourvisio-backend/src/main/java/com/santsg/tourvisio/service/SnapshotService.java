package com.santsg.tourvisio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.config.ActiveTokenRegistry;
import com.santsg.tourvisio.entity.InMemorySnapshot;
import com.santsg.tourvisio.repository.InMemorySnapshotRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    private static final String KEY_CHAT_SESSIONS = "chat_sessions";
    private static final String KEY_CHAT_CRITERIA = "chat_criteria";
    private static final String KEY_ACTIVE_TOKENS = "active_tokens";

    private final ChatSessionManager chatSessionManager;
    private final ChatSessionStore chatSessionStore;
    private final ActiveTokenRegistry activeTokenRegistry;
    private final InMemorySnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public SnapshotService(
            ChatSessionManager chatSessionManager,
            ChatSessionStore chatSessionStore,
            ActiveTokenRegistry activeTokenRegistry,
            InMemorySnapshotRepository snapshotRepository,
            ObjectMapper objectMapper) {
        this.chatSessionManager = chatSessionManager;
        this.chatSessionStore = chatSessionStore;
        this.activeTokenRegistry = activeTokenRegistry;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Restores all in-memory states from the database on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void restoreSnapshots() {
        log.info("[SnapshotService] Starting in-memory state restoration from PostgreSQL...");

        // Restore Chat Sessions
        try {
            snapshotRepository.findById(KEY_CHAT_SESSIONS).ifPresent(snapshot -> {
                try {
                    Map<String, ChatSessionManager.SessionState> sessions = objectMapper.readValue(
                            snapshot.getSnapshotData(),
                            new TypeReference<Map<String, ChatSessionManager.SessionState>>() {}
                    );
                    chatSessionManager.restoreSessionsMap(sessions);
                    log.info("[SnapshotService] Successfully restored {} chat sessions.", sessions.size());
                } catch (Exception e) {
                    log.warn("[SnapshotService] Failed to deserialize chat sessions snapshot. Continuing with empty state.", e);
                }
            });
        } catch (Exception e) {
            log.warn("[SnapshotService] Database error while restoring chat sessions.", e);
        }

        // Restore Search Criteria
        try {
            snapshotRepository.findById(KEY_CHAT_CRITERIA).ifPresent(snapshot -> {
                try {
                    Map<String, SearchCriteria> criteriaMap = objectMapper.readValue(
                            snapshot.getSnapshotData(),
                            new TypeReference<Map<String, SearchCriteria>>() {}
                    );
                    chatSessionStore.restoreStoreMap(criteriaMap);
                    log.info("[SnapshotService] Successfully restored {} search criteria entries.", criteriaMap.size());
                } catch (Exception e) {
                    log.warn("[SnapshotService] Failed to deserialize search criteria snapshot. Continuing with empty state.", e);
                }
            });
        } catch (Exception e) {
            log.warn("[SnapshotService] Database error while restoring search criteria.", e);
        }

        // Restore Active Tokens
        try {
            snapshotRepository.findById(KEY_ACTIVE_TOKENS).ifPresent(snapshot -> {
                try {
                    Set<String> activeTokens = objectMapper.readValue(
                            snapshot.getSnapshotData(),
                            new TypeReference<Set<String>>() {}
                    );
                    activeTokenRegistry.restoreActiveTokensSet(activeTokens);
                    log.info("[SnapshotService] Successfully restored {} active user tokens.", activeTokens.size());
                } catch (Exception e) {
                    log.warn("[SnapshotService] Failed to deserialize active user tokens. Continuing with empty state.", e);
                }
            });
        } catch (Exception e) {
            log.warn("[SnapshotService] Database error while restoring active tokens.", e);
        }

        log.info("[SnapshotService] In-memory state restoration complete.");
    }

    /**
     * Saves all in-memory states to the database.
     * Triggered periodically and on clean application shutdown.
     */
    @Transactional
    public void saveSnapshots() {
        log.debug("[SnapshotService] Saving in-memory state snapshots to PostgreSQL...");
        Instant now = Instant.now();

        // Save Chat Sessions
        try {
            Map<String, ChatSessionManager.SessionState> sessions = chatSessionManager.getSessionsMap();
            String sessionsJson = objectMapper.writeValueAsString(sessions);
            InMemorySnapshot sessionsSnapshot = InMemorySnapshot.builder()
                    .snapshotKey(KEY_CHAT_SESSIONS)
                    .snapshotData(sessionsJson)
                    .updatedAt(now)
                    .build();
            snapshotRepository.save(sessionsSnapshot);
            log.debug("[SnapshotService] Chat sessions snapshot saved successfully.");
        } catch (Exception e) {
            log.error("[SnapshotService] Error occurred while saving chat sessions snapshot.", e);
        }

        // Save Search Criteria
        try {
            Map<String, SearchCriteria> criteriaMap = chatSessionStore.getStoreMap();
            String criteriaJson = objectMapper.writeValueAsString(criteriaMap);
            InMemorySnapshot criteriaSnapshot = InMemorySnapshot.builder()
                    .snapshotKey(KEY_CHAT_CRITERIA)
                    .snapshotData(criteriaJson)
                    .updatedAt(now)
                    .build();
            snapshotRepository.save(criteriaSnapshot);
            log.debug("[SnapshotService] Search criteria snapshot saved successfully.");
        } catch (Exception e) {
            log.error("[SnapshotService] Error occurred while saving search criteria snapshot.", e);
        }

        // Save Active Tokens
        try {
            Set<String> activeTokens = activeTokenRegistry.getActiveTokensSet();
            String tokensJson = objectMapper.writeValueAsString(activeTokens);
            InMemorySnapshot tokensSnapshot = InMemorySnapshot.builder()
                    .snapshotKey(KEY_ACTIVE_TOKENS)
                    .snapshotData(tokensJson)
                    .updatedAt(now)
                    .build();
            snapshotRepository.save(tokensSnapshot);
            log.debug("[SnapshotService] Active user tokens snapshot saved successfully.");
        } catch (Exception e) {
            log.error("[SnapshotService] Error occurred while saving active tokens snapshot.", e);
        }
    }

    /**
     * Periodic backup schedule (runs every 5 minutes).
     */
    @Scheduled(fixedRateString = "${app.snapshot.rate-ms:300000}")
    public void periodicSave() {
        log.info("[SnapshotService] Running periodic in-memory state backup...");
        saveSnapshots();
        log.info("[SnapshotService] Periodic backup complete.");
    }

    /**
     * Clean shutdown hook.
     */
    @PreDestroy
    public void shutdownSave() {
        log.info("[SnapshotService] Application shutting down. Performing final state snapshot...");
        saveSnapshots();
        log.info("[SnapshotService] Final state snapshot complete.");
    }
}
