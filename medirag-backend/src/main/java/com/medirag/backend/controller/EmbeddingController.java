
package com.medirag.backend.controller;

import java.util.List;
import java.util.Map;

import com.medirag.backend.service.DocumentEmbeddingService;
import com.medirag.backend.service.VectorSearchService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/embeddings")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://localhost:5175"
})
public class EmbeddingController {

    private final DocumentEmbeddingService documentEmbeddingService;
    private final VectorSearchService vectorSearchService;

    public EmbeddingController(
            DocumentEmbeddingService documentEmbeddingService,
            VectorSearchService vectorSearchService) {

        this.documentEmbeddingService =
                documentEmbeddingService;

        this.vectorSearchService =
                vectorSearchService;
    }

    public record SearchRequest(
            @NotBlank
            @Size(max = 4000)
            String question
    ) {}

    @PostMapping("/document/{documentId}")
    public ResponseEntity<Map<String, Object>> generate(
            @PathVariable Long documentId) {

        int count =
                documentEmbeddingService.generateEmbeddings(
                        documentId);

        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "embeddedChunks", count,
                "message", "Embeddings generated successfully"
        ));
    }

    @PostMapping("/search")
    public ResponseEntity<List<VectorSearchService.VectorSearchResult>>
            search(@Valid @RequestBody SearchRequest request) {

        return ResponseEntity.ok(
                vectorSearchService.search(
                        request.question(),
                        5
                )
        );
    }
}
