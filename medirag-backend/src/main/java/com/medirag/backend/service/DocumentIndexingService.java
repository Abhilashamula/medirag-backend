
package com.medirag.backend.service;

import com.medirag.backend.dto.ContentBlock;
import com.medirag.backend.dto.StructuredDocumentResponse;
import com.medirag.backend.entity.DocumentChunk;
import com.medirag.backend.repository.DocumentChunkRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentIndexingService {

    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 200;

    private final StructuredDocumentService structuredDocumentService;
    private final DocumentChunkRepository chunkRepository;

    public DocumentIndexingService(
            StructuredDocumentService structuredDocumentService,
            DocumentChunkRepository chunkRepository) {

        this.structuredDocumentService = structuredDocumentService;
        this.chunkRepository = chunkRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public int indexDocument(Long documentId) throws IOException {

        // Reuse the existing PDF/OCR/Excel/CSV extraction service.
        StructuredDocumentResponse structured =
                structuredDocumentService.extract(documentId);

        List<DocumentChunk> newChunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ContentBlock block : structured.blocks()) {

            String content = block.content();

            if (content == null || content.isBlank()) {
                continue;
            }

            // Preserve source location and extraction information.
            String sourcePrefix =
                    "Location: " + (
                            block.location() == null
                                    ? "Unknown"
                                    : block.location()
                    )
                    + "\nExtraction method: "
                    + block.extractionMethod()
                    + "\n";

            List<String> parts = splitText(content);

            for (String part : parts) {

                String chunkContent = sourcePrefix + part;

                DocumentChunk chunk = new DocumentChunk(
                        documentId,
                        chunkIndex++,
                        block.pageNumber(),
                        chunkContent
                );

                newChunks.add(chunk);
            }
        }

        if (newChunks.isEmpty()) {
            throw new IllegalStateException(
                    "No readable content found for document "
                    + documentId
                    + " after structured extraction and OCR."
            );
        }

        // Replace previous chunks only after extraction succeeds.
        chunkRepository.deleteByDocumentId(documentId);
        chunkRepository.flush();

        chunkRepository.saveAll(newChunks);

        return newChunks.size();
    }

    private List<String> splitText(String text) {

        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();

        List<String> chunks = new ArrayList<>();

        int start = 0;

        while (start < normalized.length()) {

            int end = Math.min(
                    start + CHUNK_SIZE,
                    normalized.length()
            );

            if (end < normalized.length()) {

                int boundary =
                        normalized.lastIndexOf(' ', end);

                if (boundary > start + CHUNK_SIZE / 2) {
                    end = boundary;
                }
            }

            String part = normalized
                    .substring(start, end)
                    .trim();

            if (!part.isBlank()) {
                chunks.add(part);
            }

            if (end >= normalized.length()) {
                break;
            }

            start = Math.max(
                    start + 1,
                    end - CHUNK_OVERLAP
            );
        }

        return chunks;
    }
}
