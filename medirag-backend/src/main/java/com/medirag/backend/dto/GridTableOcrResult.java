
package com.medirag.backend.dto;

import java.util.List;

public record GridTableOcrResult(

        int tableIndex,

        int pageNumber,

        int totalRows,

        int totalColumns,

        int totalCells,

        int extractedCells,

        int emptyCells,

        int unreadableCells,

        int failedCells,

        Double averageConfidence,

        boolean requiresVerification,

        List<List<GridCellOcrResult>> rows,

        List<String> warnings

) {
}
