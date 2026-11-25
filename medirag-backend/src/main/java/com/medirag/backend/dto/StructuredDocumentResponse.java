
package com.medirag.backend.dto;

import java.util.List;

public record StructuredDocumentResponse(
        Long documentId,
        String fileName,
        String contentType,
        int totalBlocks,
        List<ContentBlock> blocks,
        List<String> warnings
) {
}
