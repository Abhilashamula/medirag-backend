
package com.medirag.backend.dto;

import java.util.List;

public record GridCellOcrResult(

        GridCell cell,

        String text,

        Double confidence,

        boolean empty,

        boolean requiresVerification,

        String status,

        String extractionMethod,

        List<String> warnings

) {
}
