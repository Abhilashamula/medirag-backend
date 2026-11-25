
package com.medirag.backend.dto;

import java.util.List;

public record ChunkingResponse(
        Long documentId,
        int totalPages,
        int totalChunks,
        List<DocumentChunk> chunks
) {
}
