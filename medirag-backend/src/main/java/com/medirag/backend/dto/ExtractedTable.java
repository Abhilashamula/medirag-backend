
package com.medirag.backend.dto;

import java.util.List;

public record ExtractedTable(

        int tableIndex,

        int pageNumber,

        String tableType,

        List<String> headers,

        int totalColumns,

        int totalRows,

        int top,

        int bottom,

        List<TableRow> rows,

        List<String> warnings

) {

}
