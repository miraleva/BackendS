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

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "last_message_timestamp", nullable = false)
    private Instant lastMessageTimestamp;

    @Column(name = "chat_status", nullable = false, length = 50)
    private String chatStatus;

    @Column(nullable = false, length = 50)
    private String mode;

    @Column(name = "out_of_scope_count")
    private int outOfScopeCount;

    @Column(name = "last_requested_field", length = 100)
    private String lastRequestedField;

    @Column(name = "search_criteria_json", columnDefinition = "TEXT")
    private String searchCriteriaJson;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    @OrderBy("timestamp ASC")
    private List<ChatMessage> messages = new ArrayList<>();
}
