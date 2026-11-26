
package com.medirag.backend.dto;

import java.util.List;

public record TableExtractionResponse(

        Long documentId,

        String fileName,

        int pageNumber,

        int totalTables,

        double ocrConfidence,

        List<ExtractedTable> tables,

        List<String> warnings

) {

}
