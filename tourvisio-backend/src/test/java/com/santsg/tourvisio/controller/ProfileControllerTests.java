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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.http.MediaType;

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

    @Test
    @Transactional
    public void testChangePasswordSuccess() throws Exception {
        // 1. Create a user
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email("testchangepassword@example.com")
                .phone("905554443322")
                .password(org.mindrot.jbcrypt.BCrypt.hashpw("oldpassword", org.mindrot.jbcrypt.BCrypt.gensalt()))
                .build();
        user = userRepository.save(user);
        Long userId = user.getId();

        // 2. Perform POST request to change password
        mockMvc.perform(post("/api/profile/change-password")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"newsecurepassword\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        // 3. Verify in database
        User updatedUser = userRepository.findById(userId).orElseThrow();
        assertTrue(org.mindrot.jbcrypt.BCrypt.checkpw("newsecurepassword", updatedUser.getPassword()));
    }

    @Test
    public void testChangePasswordUnauthorized() throws Exception {
        mockMvc.perform(post("/api/profile/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"newsecurepassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Transactional
    public void testChangePasswordInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/profile/change-password")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @Transactional
    public void testDeleteChatSessionSuccess() throws Exception {
        // 1. Create User
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email("testdeletesession@example.com")
                .phone("905554443325")
                .password("password")
                .build();
        user = userRepository.save(user);
        Long userId = user.getId();

        // 2. Create ChatSession
        ChatSession session = ChatSession.builder()
                .id("session-delete-success-123")
                .user(user)
                .title("Delete Test Session")
                .chatStatus("ACTIVE")
                .lastMessageTimestamp(Instant.now())
                .build();
        session = chatSessionRepository.save(session);
        String sessionId = session.getId();

        // 3. Create ChatMessage
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sender("user")
                .text("Hello delete test")
                .timestamp(Instant.now())
                .build();
        message = chatMessageRepository.save(message);
        Long messageId = message.getId();
        
        session.setMessages(new java.util.ArrayList<>(java.util.List.of(message)));
        chatSessionRepository.save(session);

        // 4. Perform DELETE request
        mockMvc.perform(delete("/api/chat/sessions/" + sessionId)
                .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Session deleted successfully"));

        // 5. Verify deletion
        assertFalse(chatSessionRepository.findById(sessionId).isPresent());
        assertFalse(chatMessageRepository.findById(messageId).isPresent());
    }

    @Test
    public void testDeleteChatSessionUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/chat/sessions/some-session-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Transactional
    public void testDeleteChatSessionForbidden() throws Exception {
        // 1. Create User A and User B
        User userA = User.builder()
                .firstName("User")
                .lastName("A")
                .email("usera@example.com")
                .phone("905554443326")
                .password("password")
                .build();
        userA = userRepository.save(userA);

        User userB = User.builder()
                .firstName("User")
                .lastName("B")
                .email("userb@example.com")
                .phone("905554443327")
                .password("password")
                .build();
        userB = userRepository.save(userB);

        // 2. Create ChatSession for User A
        ChatSession session = ChatSession.builder()
                .id("session-forbidden-test-123")
                .user(userA)
                .title("A's Session")
                .chatStatus("ACTIVE")
                .lastMessageTimestamp(Instant.now())
                .build();
        session = chatSessionRepository.save(session);
        String sessionId = session.getId();

        // 3. Perform DELETE request by User B
        mockMvc.perform(delete("/api/chat/sessions/" + sessionId)
                .requestAttr("userId", userB.getId()))
                .andExpect(status().isForbidden());
    }
}
