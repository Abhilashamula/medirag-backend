package com.medirag.backend.service;


import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OllamaService {

    private final RestClient restClient;
    private final String chatModel;

    public OllamaService(
            @Value("${medirag.ollama.base-url:http://localhost:11434}")
            String baseUrl,
            @Value("${medirag.ollama.chat-model:llama3.2}")
            String chatModel) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.chatModel = chatModel;
    }

    @SuppressWarnings("unchecked")
    public String ask(String question) {

        Map<String, Object> request = Map.of(
                "model", chatModel,
                "prompt", question,
                "stream", false
        );

        Map<String, Object> response = restClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("response") == null) {
            throw new IllegalStateException(
                    "Ollama returned an empty response"
            );
        }

        return response.get("response").toString();
    }
}
