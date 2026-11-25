
package com.medirag.backend.dto;

public record ParsedDocument(
        Long documentId,
        String fileName,
        String contentType,
        String extractedText,
        int characterCount
) {
}
