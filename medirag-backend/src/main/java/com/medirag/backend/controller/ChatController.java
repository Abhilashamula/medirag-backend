
package com.medirag.backend.controller;

import com.medirag.backend.service.OllamaService;
import com.medirag.backend.service.RagChatService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://localhost:5175"
})
public class ChatController {

    private final OllamaService ollamaService;
    private final RagChatService ragChatService;

    public ChatController(
            OllamaService ollamaService,
            RagChatService ragChatService) {

        this.ollamaService = ollamaService;
        this.ragChatService = ragChatService;
    }

    // Request DTO
    public record ChatRequest(

            @NotBlank(message = "Question cannot be empty")
            @Size(
                max = 4000,
                message = "Question must not exceed 4000 characters"
            )
            String question

    ) {}

    // Response DTO for normal Ollama chat
    public record ChatResponse(String answer) {}

    // =========================================
    // 1. NORMAL AI CHAT - WITHOUT DOCUMENTS
    // =========================================

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> ask(
            @Valid @RequestBody ChatRequest request) {

        String answer = ollamaService.ask(
                request.question()
        );

        return ResponseEntity.ok(
                new ChatResponse(answer)
        );
    }

    // =========================================
    // 2. RAG CHAT - WITH UPLOADED DOCUMENTS
    // =========================================

    @PostMapping("/ask-documents")
    public ResponseEntity<RagChatService.RagResponse> askDocuments(
            @Valid @RequestBody ChatRequest request) {

        RagChatService.RagResponse response =
                ragChatService.ask(
                        request.question()
                );

        return ResponseEntity.ok(response);
    }
}
