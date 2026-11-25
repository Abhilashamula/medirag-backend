
package com.medirag.backend.service;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import com.medirag.backend.entity.Document;

@Service
public class DocumentProcessingService {

    private final DocumentService documentService;

    public DocumentProcessingService(
            DocumentService documentService) {
        this.documentService = documentService;
    }

    public String extractText(Long documentId)
            throws IOException {

        // 1. Fetch PDF bytes from PostgreSQL
        Document document =
                documentService.getDocument(documentId);

        byte[] pdfBytes = document.getFileData();

        // 2. Load the PDF from its bytes
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {

            // 3. Extract text
            PDFTextStripper stripper =
                    new PDFTextStripper();

            return stripper.getText(pdf);
        }
    }
}
