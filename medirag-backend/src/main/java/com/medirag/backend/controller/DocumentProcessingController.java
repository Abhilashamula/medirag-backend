
package com.medirag.backend.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.medirag.backend.service.DocumentProcessingService;

@RestController
@RequestMapping("/api/documents")
public class DocumentProcessingController {

    private final DocumentProcessingService processingService;

    public DocumentProcessingController(
            DocumentProcessingService processingService) {
        this.processingService = processingService;
    }

    @GetMapping("/{id}/text")
    public ResponseEntity<Map<String, String>> extractText(
            @PathVariable Long id) throws IOException {

        String text = processingService.extractText(id);

        return ResponseEntity.ok(
                Map.of(
                        "documentId", id.toString(),
                        "extractedText", text
                )
        );
    }
}
