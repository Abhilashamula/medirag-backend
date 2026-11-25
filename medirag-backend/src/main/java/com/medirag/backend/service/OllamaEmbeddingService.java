
package com.medirag.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OllamaEmbeddingService {

    private static final int DIMENSIONS = 768;

    private final RestClient restClient;
    private final String model;

    public OllamaEmbeddingService(
            @Value("${medirag.ollama.base-url:http://localhost:11434}")
            String baseUrl,
            @Value("${medirag.ollama.embedding-model:nomic-embed-text}")
            String model) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.model = model;
    }

    public record EmbedResponse(
            List<List<Double>> embeddings
    ) {}

    public float[] embed(String text) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Embedding text cannot be empty");
        }

        Map<String, Object> request = Map.of(
                "model", model,
                "input", text
        );

        EmbedResponse response = restClient.post()
                .uri("/api/embed")
                .body(request)
                .retrieve()
                .body(EmbedResponse.class);

        if (response == null
                || response.embeddings() == null
                || response.embeddings().isEmpty()) {

            throw new IllegalStateException(
                    "Ollama returned no embeddings");
        }

        List<Double> values =
                response.embeddings().get(0);

        if (values == null
                || values.size() != DIMENSIONS) {

            throw new IllegalStateException(
                    "Expected 768 embedding dimensions");
        }

        float[] vector = new float[DIMENSIONS];

        for (int i = 0; i < DIMENSIONS; i++) {

            Double value = values.get(i);

            if (value == null || !Double.isFinite(value)) {
                throw new IllegalStateException(
                        "Invalid embedding value at " + i);
            }

            vector[i] = value.floatValue();
        }

        return vector;
    }
}
