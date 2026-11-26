
package com.medirag.backend.dto;

public record GridCell(

        int tableIndex,
        int pageNumber,
        int rowIndex,
        int columnIndex,
        int left,
        int top,
        int width,
        int height

) {
}
