package com.santsg.tourvisio.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(nullable = false, length = 10)
    private String sender; // "user" or "bot"

    @Column(columnDefinition = "TEXT")
    private String text;

    private Instant timestamp;

    @Column(name = "results_json", columnDefinition = "TEXT")
    private String resultsJson;
}
