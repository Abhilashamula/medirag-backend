
package com.medirag.backend.service;

import java.util.List;

import com.medirag.backend.entity.DocumentChunk;
import com.medirag.backend.repository.DocumentChunkRepository;

import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    private final DocumentChunkRepository chunkRepository;
    private final OllamaEmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public DocumentEmbeddingService(
            DocumentChunkRepository chunkRepository,
            OllamaEmbeddingService embeddingService,
            VectorStoreService vectorStoreService) {

        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    public int generateEmbeddings(Long documentId) {

        List<DocumentChunk> chunks =
                chunkRepository.findByDocumentIdOrderByChunkIndexAsc(
                        documentId);

        if (chunks.isEmpty()) {
            throw new IllegalStateException(
                    "No chunks found for document ID: "
                    + documentId
                    + ". Index the document first.");
        }

        int completed = 0;

        for (DocumentChunk chunk : chunks) {

            float[] embedding =
                    embeddingService.embed(chunk.getContent());

            vectorStoreService.saveEmbedding(
                    chunk.getId(),
                    embedding
            );

            completed++;
        }

        return completed;
    }
}
