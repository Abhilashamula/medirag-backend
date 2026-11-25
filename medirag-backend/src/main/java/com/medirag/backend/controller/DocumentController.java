
package com.medirag.backend.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.medirag.backend.dto.DocumentResponse;
import com.medirag.backend.entity.Document;
import com.medirag.backend.service.DocumentService;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(
            DocumentService documentService) {

        this.documentService = documentService;
    }

    // 1. Upload document.
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file)
            throws IOException {

        try {

            Document saved =
                    documentService.uploadDocument(file);

            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "fileName", saved.getFileName(),
                    "fileSize", saved.getFileSize(),
                    "processingStatus",
                    saved.getProcessingStatus(),
                    "message",
                    "Document uploaded. Processing submitted."
            ));

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", e.getMessage()
                    ));
        }
    }

    // 2. Get all documents.
    @GetMapping
    public ResponseEntity<List<DocumentResponse>>
            getAllDocuments() {

        List<DocumentResponse> documents =
                documentService.getAllDocuments()
                        .stream()
                        .map(DocumentResponse::from)
                        .toList();

        return ResponseEntity.ok(documents);
    }

    // 3. Get document metadata.
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable Long id) {

        Document document =
                documentService.getDocument(id);

        return ResponseEntity.ok(
                DocumentResponse.from(document)
        );
    }

    // 4. Download original document.
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id) {

        Document document =
                documentService.getDocument(id);

        return ResponseEntity.ok()
                .contentType(
                    MediaType.parseMediaType(
                        document.getContentType()
                    )
                )
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment()
                        .filename(document.getFileName())
                        .build()
                        .toString()
                )
                .body(document.getFileData());
    }

    // 5. Delete document.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id) {

        documentService.deleteDocument(id);

        return ResponseEntity.noContent().build();
    }

    // 6. Get processing status.
    @GetMapping("/{id}/status")
    public ResponseEntity<DocumentResponse>
            getProcessingStatus(
                @PathVariable Long id) {

        Document document =
                documentService.getDocument(id);

        return ResponseEntity.ok(
                DocumentResponse.from(document)
        );
    }

    // 7. Retry processing.
    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryProcessing(
            @PathVariable Long id) {

        documentService.retryProcessing(id);

        return ResponseEntity.accepted()
                .body(Map.of(
                    "documentId", id,
                    "message",
                    "Document processing submitted"
                ));
    }
}
