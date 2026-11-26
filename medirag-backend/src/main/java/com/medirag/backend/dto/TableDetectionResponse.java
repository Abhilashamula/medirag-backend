
package com.medirag.backend.dto;

import java.util.List;

public record TableDetectionResponse(

        Long documentId,

        String fileName,

        int pageNumber,

        int imageWidth,

        int imageHeight,

        int totalTables,

        String detectionMethod,

        List<TableRegion> tables,

        List<String> warnings

) {
}
