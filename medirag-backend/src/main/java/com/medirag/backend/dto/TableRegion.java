
package com.medirag.backend.dto;

import java.util.List;

public record TableRegion(

        int tableIndex,

        int pageNumber,

        int left,

        int top,

        int width,

        int height,

        int totalRows,

        int totalColumns,

        List<Integer> horizontalLines,

        List<Integer> verticalLines

) {
}
