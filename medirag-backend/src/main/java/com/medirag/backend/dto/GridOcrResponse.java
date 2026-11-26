
package com.medirag.backend.dto;

import java.util.List;

public record GridOcrResponse(

        Long documentId,

        String fileName,

        int pageNumber,

        int imageWidth,

        int imageHeight,

        int totalTables,

        int totalCells,

        int extractedCells,

        int emptyCells,

        int unreadableCells,

        int failedCells,

        long processingTimeMs,

        String extractionMethod,

        List<GridTableOcrResult> tables,

        List<String> warnings

) {
}
