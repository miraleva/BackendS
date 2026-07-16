package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.entity.ChatSession;
import com.santsg.tourvisio.entity.ChatMessage;
import com.santsg.tourvisio.repository.UserRepository;
import com.santsg.tourvisio.repository.ChatSessionRepository;
import com.santsg.tourvisio.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
        "tourvisio.api.mock-mode=true",
        "tourvisio.api.test-mode=true",
        "ai.openai.api-key="
})
public class ProfileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @Transactional
    public void testDeleteAccountSuccess() throws Exception {
        // 1. Create a test user
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email("test.delete@example.com")
                .phone("1234567890")
                .password("password")
                .build();
        user = userRepository.save(user);
        Long userId = user.getId();

        // 2. Create a chat session linked to this user
        ChatSession session = ChatSession.builder()
                .id("session-test-delete-1")
                .user(user)
                .title("Delete Test Session")
                .chatStatus("ACTIVE")
                .lastMessageTimestamp(Instant.now())
                .build();
        session = chatSessionRepository.save(session);
        String sessionId = session.getId();

        // 3. Create a chat message in the session
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sender("user")
                .text("Hello, test delete")
                .timestamp(Instant.now())
                .build();
        message = chatMessageRepository.save(message);
        Long messageId = message.getId();

        // Synchronize with database and clear first-level cache
        entityManager.flush();
        entityManager.clear();

        // Verify they exist in DB (will load fresh from DB with correct collections mapped)
        assertTrue(userRepository.findById(userId).isPresent());
        assertTrue(chatSessionRepository.findById(sessionId).isPresent());
        assertTrue(chatMessageRepository.findById(messageId).isPresent());

        // 4. Perform DELETE request with userId attribute
        mockMvc.perform(delete("/api/profile")
                .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted successfully"));

        // 5. Verify user and cascaded chat session + message are deleted
        assertFalse(userRepository.findById(userId).isPresent());
        assertFalse(chatSessionRepository.findById(sessionId).isPresent());
        assertFalse(chatMessageRepository.findById(messageId).isPresent());
    }

    @Test
    public void testDeleteAccountUnauthorized() throws Exception {
        // Perform DELETE request without userId attribute
        mockMvc.perform(delete("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    public void testDeleteAccountNotFound() throws Exception {
        // Perform DELETE request with a non-existent userId
        mockMvc.perform(delete("/api/profile")
                .requestAttr("userId", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
