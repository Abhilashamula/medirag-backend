
package com.medirag.backend.dto;

import java.util.List;

public record OcrResult(

        String text,

        int imageWidth,

        int imageHeight,

        int totalWords,

        int totalLines,

        double averageConfidence,

        int lowConfidenceWords,

        List<OcrWord> words,

        List<OcrLine> lines,

        List<String> warnings

) {

}
