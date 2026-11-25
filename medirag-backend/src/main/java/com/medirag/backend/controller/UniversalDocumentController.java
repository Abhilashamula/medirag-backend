
package com.medirag.backend.controller;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.medirag.backend.dto.ParsedDocument;
import com.medirag.backend.service.UniversalDocumentParserService;

@RestController
@RequestMapping("/api/documents")
public class UniversalDocumentController {

    private final UniversalDocumentParserService parserService;

    public UniversalDocumentController(
            UniversalDocumentParserService parserService) {
        this.parserService = parserService;
    }

    @GetMapping("/{id}/parse")
    public ResponseEntity<ParsedDocument> parseDocument(
            @PathVariable Long id) throws IOException {

        return ResponseEntity.ok(
                parserService.parseDocument(id)
        );
    }
}
