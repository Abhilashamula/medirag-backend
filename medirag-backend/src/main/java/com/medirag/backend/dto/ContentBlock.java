
package com.medirag.backend.dto;

import java.util.List;

public record ContentBlock(
        int blockIndex,
        String type,
        Integer pageNumber,
        String location,
        String content,
        List<List<String>> rows,
        String extractionMethod
) {
}
