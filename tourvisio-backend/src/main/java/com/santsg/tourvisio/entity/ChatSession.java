package com.santsg.tourvisio.entity;

import com.santsg.tourvisio.chat.SearchCriteria;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    @Column(length = 50)
    private String id; // Represents the sessionId

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String title = "New Chat Session";

    @Column(name = "chat_status", nullable = false, length = 20)
    @Builder.Default
    private String chatStatus = "ACTIVE";

    @Column(name = "last_requested_field", length = 50)
    private String lastRequestedField;

    @Column(name = "out_of_scope_count", nullable = false)
    @Builder.Default
    private int outOfScopeCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "search_criteria", columnDefinition = "jsonb")
    private SearchCriteria searchCriteria;

    @Column(name = "last_message_timestamp", nullable = false)
    private Instant lastMessageTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastMessageTimestamp = Instant.now();
        if (this.searchCriteria == null) {
            this.searchCriteria = new SearchCriteria();
        }
    }
}
