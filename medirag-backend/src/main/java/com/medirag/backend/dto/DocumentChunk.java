
package com.medirag.backend.dto;

public record DocumentChunk(
        Long documentId,
        int chunkIndex,
        String sectionTitle,
        String sectionPath,
        int headingLevel,
        int startPage,
        int endPage,
        String content
) {
}
