package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.dto.ChatMessageRequest;
import com.santsg.tourvisio.dto.ChatMessageResponse;
import com.santsg.tourvisio.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat Controller", description = "Endpoints for the AI TourVisio Chatbot")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/message")
    @Operation(summary = "Send a message to the chatbot", description = "Processes user message, detects intent (HOTEL_SEARCH/FLIGHT_SEARCH), identifies missing fields, and returns chatbot response.")
    public ResponseEntity<ChatMessageResponse> sendMessage(@Valid @RequestBody ChatMessageRequest request) {
        ChatMessageResponse response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }
}
