package com.santsg.tourvisio.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Column(name = "id", length = 100)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    @Builder.Default
    private String title = "New Chat Session";

    @Column(name = "chat_status", nullable = false, length = 50)
    @Builder.Default
    private String chatStatus = "ACTIVE";

    @Column(name = "\"mode\"", length = 50)
    @Builder.Default
    private String mode = "GATHERING";

    @Column(name = "out_of_scope_count")
    private int outOfScopeCount;

    @Column(name = "last_requested_field", length = 100)
    private String lastRequestedField;

    @Column(name = "search_criteria_json", columnDefinition = "TEXT")
    private String searchCriteriaJson;

    @Column(name = "last_message_timestamp", nullable = false)
    private Instant lastMessageTimestamp;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    @OrderBy("timestamp ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    // Virtual properties for compatibility with original code and database mappings
    public Long getUserId() {
        return this.user != null ? this.user.getId() : this.userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMode() {
        return this.mode != null ? this.mode : "GATHERING";
    }
}
