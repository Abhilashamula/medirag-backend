
package com.medirag.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class TextChunkingService {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;

    public List<String> splitText(String text) {

        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        text = text.replaceAll("\\s+", " ").trim();

        int start = 0;

        while (start < text.length()) {

            int end = Math.min(
                    start + CHUNK_SIZE,
                    text.length()
            );

            String chunk = text.substring(start, end);
            chunks.add(chunk);

            if (end == text.length()) {
                break;
            }

            start = end - CHUNK_OVERLAP;
        }

        return chunks;
    }
}
