
package com.medirag.backend.dto;

import java.util.List;

public record ContentBlock(

        int blockIndex,

        String type,

        Integer pageNumber,

        String location,

        String content,

        List<List<String>> rows,

        String extractionMethod,

        Double confidence,

        boolean requiresVerification,

        List<String> warnings,

        ExtractedTable table

) {

    // Backward compatibility with existing services.
    public ContentBlock(
            int blockIndex,
            String type,
            Integer pageNumber,
            String location,
            String content,
            List<List<String>> rows,
            String extractionMethod) {

        this(
                blockIndex,
                type,
                pageNumber,
                location,
                content,
                rows,
                extractionMethod,
                null,
                true,
                List.of(),
                null
        );
    }
}
