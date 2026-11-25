
package com.medirag.backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import com.medirag.backend.dto.ParsedDocument;
import com.medirag.backend.entity.Document;

@Service
public class UniversalDocumentParserService {

    private final DocumentService documentService;
    private final OcrService ocrService;
    private final Tika tika = new Tika();

    public UniversalDocumentParserService(
            DocumentService documentService,
            OcrService ocrService) {

        this.documentService = documentService;
        this.ocrService = ocrService;
    }

    public ParsedDocument parseDocument(Long documentId)
            throws IOException {

        Document document =
                documentService.getDocument(documentId);

        byte[] fileBytes = document.getFileData();
        String contentType = document.getContentType();

        if (fileBytes == null || fileBytes.length == 0) {
            throw new IOException("Document file is empty");
        }

        if (contentType == null) {
            throw new IOException(
                    "Document content type is missing");
        }

        System.out.println("========== MEDIRAG PARSER ==========");
        System.out.println("Document ID: " + documentId);
        System.out.println("File Name: " + document.getFileName());
        System.out.println("Content Type: " + contentType);

        String extractedText;

        if (contentType.startsWith("image/")) {

            System.out.println(
                    "Image detected. Starting OCR...");

            extractedText =
                    ocrService.extractImageText(fileBytes);

        } else if ("application/pdf".equals(contentType)) {

            System.out.println(
                    "PDF detected. Extracting embedded text...");

            extractedText = extractUsingTika(fileBytes);

            if (extractedText.isBlank()) {

                System.out.println(
                        "No embedded text found. Starting OCR...");

                extractedText =
                        ocrService.extractScannedPdfText(fileBytes);
            }

        } else {

            System.out.println(
                    "Other document format. Using Apache Tika...");

            extractedText = extractUsingTika(fileBytes);
        }

        if (extractedText == null) {
            extractedText = "";
        }

        extractedText = extractedText.trim();

        System.out.println(
                "Extracted characters: " + extractedText.length());

        System.out.println(
                "====================================");

        return new ParsedDocument(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                extractedText,
                extractedText.length()
        );
    }

    private String extractUsingTika(byte[] fileBytes)
            throws IOException {

        try (ByteArrayInputStream input =
                new ByteArrayInputStream(fileBytes)) {

            String text = tika.parseToString(input);

            return text == null ? "" : text.trim();

        } catch (Exception e) {

            throw new IOException(
                    "Document text extraction failed: "
                    + e.getMessage(),
                    e
            );
        }
    }
}
