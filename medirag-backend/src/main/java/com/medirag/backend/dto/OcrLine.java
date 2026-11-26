
package com.medirag.backend.dto;

import java.util.List;

public record OcrLine(

        int pageNumber,

        int blockNumber,

        int paragraphNumber,

        int lineNumber,

        String text,

        int left,

        int top,

        int width,

        int height,

        double averageConfidence,

        List<OcrWord> words

) {

}
