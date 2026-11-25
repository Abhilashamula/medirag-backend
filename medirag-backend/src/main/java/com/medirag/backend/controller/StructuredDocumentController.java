
package com.medirag.backend.controller;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medirag.backend.dto.StructuredDocumentResponse;
import com.medirag.backend.service.StructuredDocumentService;

@RestController
@RequestMapping("/api/documents")
public class StructuredDocumentController {

    private final StructuredDocumentService service;

    public StructuredDocumentController(
            StructuredDocumentService service) {

        this.service = service;
    }

    @GetMapping("/{id}/structured")
    public ResponseEntity<StructuredDocumentResponse>
            getStructuredDocument(@PathVariable Long id)
            throws IOException {

        return ResponseEntity.ok(service.extract(id));
    }
}
