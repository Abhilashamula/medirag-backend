
package com.medirag.backend.controller;

import com.medirag.backend.service.DocumentIndexingService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/index")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://localhost:5175"
})
public class DocumentIndexController {

    private final DocumentIndexingService indexingService;

    public DocumentIndexController(
            DocumentIndexingService indexingService) {

        this.indexingService = indexingService;
    }

    @PostMapping("/{documentId}")
    public ResponseEntity<?> index(
            @PathVariable Long documentId) throws Exception {

        int totalChunks =
                indexingService.indexDocument(documentId);

        return ResponseEntity.ok(Map.of(
            "documentId", documentId,
            "totalChunks", totalChunks,
            "message", "Document indexed successfully"
        ));
    }
}
