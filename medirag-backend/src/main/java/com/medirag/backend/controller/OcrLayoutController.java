
package com.medirag.backend.controller;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.server.ResponseStatusException;

import com.medirag.backend.dto.OcrResult;
import com.medirag.backend.entity.Document;
import com.medirag.backend.service.DocumentService;
import com.medirag.backend.service.LayoutAwareOcrService;

@RestController
@RequestMapping("/api/ocr")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:5175"
})
public class OcrLayoutController {

    private final DocumentService documentService;

    private final LayoutAwareOcrService layoutOcrService;

    @Value("${medirag.ocr.layout-dpi:300}")
    private int layoutDpi;

    @Value("${medirag.ocr.max-image-pixels:25000000}")
    private long maxImagePixels;

    public OcrLayoutController(
            DocumentService documentService,
            LayoutAwareOcrService layoutOcrService) {

        this.documentService = documentService;

        this.layoutOcrService = layoutOcrService;
    }

    @GetMapping("/layout/{documentId}")
    public ResponseEntity<OcrResult> extractLayout(

            @PathVariable Long documentId,

            @RequestParam(defaultValue = "1")
            int page

    ) throws IOException {

        Document document =
                documentService.getDocument(documentId);

        String contentType = document.getContentType();

        byte[] bytes = document.getFileData();

        if (page < 1) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number must be at least 1");
        }

        if (contentType != null
                && contentType.startsWith("image/")) {

            if (page != 1) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Image OCR currently supports page 1");
            }

            return ResponseEntity.ok(
                    layoutOcrService.extractImage(bytes));
        }

        if ("application/pdf".equals(contentType)) {

            try (PDDocument pdf = Loader.loadPDF(bytes)) {

                if (page > pdf.getNumberOfPages()) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid PDF page number");
                }

                PDPage pdfPage = pdf.getPage(page - 1);

                double scale = layoutDpi / 72.0;

                double width = Math.ceil(
                        pdfPage.getCropBox().getWidth()
                        * scale);

                double height = Math.ceil(
                        pdfPage.getCropBox().getHeight()
                        * scale);

                if (width * height > maxImagePixels) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "PDF page exceeds OCR pixel limit");
                }

                PDFRenderer renderer = new PDFRenderer(pdf);

                BufferedImage image =
                        renderer.renderImageWithDPI(

                                page - 1,

                                layoutDpi,

                                ImageType.RGB
                        );

                try {

                    OcrResult result =
                            layoutOcrService.extractLayout(
                                    image,
                                    page);

                    return ResponseEntity.ok(result);

                } finally {

                    image.flush();
                }
            }
        }

        throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Layout OCR supports PDF and image documents");
    }
}
