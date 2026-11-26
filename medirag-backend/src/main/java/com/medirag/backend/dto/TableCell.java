
package com.medirag.backend.dto;

import java.util.List;

public record TableCell(

        int columnIndex,

        String columnName,

        String value,

        Double confidence,

        boolean empty,

        boolean requiresVerification,

        List<OcrWord> words

) {

}
