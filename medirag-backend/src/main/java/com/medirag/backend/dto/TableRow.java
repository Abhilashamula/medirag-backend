
package com.medirag.backend.dto;

import java.util.List;

public record TableRow(

        int rowIndex,

        int pageNumber,

        int top,

        int bottom,

        Double averageConfidence,

        boolean requiresVerification,

        List<TableCell> cells

) {

}
