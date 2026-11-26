
package com.medirag.backend.dto;

public record OcrWord(

        String text,

        int pageNumber,

        int blockNumber,

        int paragraphNumber,

        int lineNumber,

        int wordNumber,

        int left,

        int top,

        int width,

        int height,

        double confidence

) {

    public int right() {
        return left + width;
    }

    public int bottom() {
        return top + height;
    }

    public double centerY() {
        return top + height / 2.0;
    }
}
