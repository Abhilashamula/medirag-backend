
package com.medirag.backend.controller;

import java.awt.image.BufferedImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

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

import com.medirag.backend.dto.GridOcrResponse;
import com.medirag.backend.dto.GridTableOcrResult;

import com.medirag.backend.entity.Document;

import com.medirag.backend.service.DocumentService;
import com.medirag.backend.service.GridTableOcrService;

@RestController
@RequestMapping("/api/tables")
@CrossOrigin(origins = {

        "http://localhost:5173",

        "http://localhost:5174",

        "http://localhost:5175"
})
public class GridTableOcrController {

    private final DocumentService documentService;

    private final GridTableOcrService gridOcrService;

    @Value("${medirag.ocr.layout-dpi:300}")
    private int layoutDpi;

    @Value("${medirag.ocr.max-image-pixels:25000000}")
    private long maxImagePixels;

    public GridTableOcrController(

            DocumentService documentService,

            GridTableOcrService gridOcrService) {

        this.documentService = documentService;

        this.gridOcrService = gridOcrService;
    }

    @GetMapping("/ocr/{documentId}")
    public ResponseEntity<GridOcrResponse> extract(

            @PathVariable Long documentId,

            @RequestParam(defaultValue = "1") int page

    ) throws IOException {

        if (page < 1) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "Page number must be positive"
            );
        }

        long startTime = System.nanoTime();

        Document document =
                documentService.getDocument(documentId);

        BufferedImage image =
                loadImage(document, page);

        try {

            validateImageSize(image);

            List<GridTableOcrResult> tables;

            try {

                tables = gridOcrService.extractTables(

                        image,

                        page
                );

            } catch (IllegalArgumentException e) {

                throw new ResponseStatusException(

                        HttpStatus.UNPROCESSABLE_ENTITY,

                        e.getMessage(),

                        e
                );
            }

         

int totalCells = 0;
int extracted = 0;
int empty = 0;
int unreadable = 0;
int failed = 0;

for (GridTableOcrResult table : tables) {

    totalCells += table.totalCells();

    extracted += table.extractedCells();

    empty += table.emptyCells();

    unreadable += table.unreadableCells();

    failed += table.failedCells();
}

            long processingTime =
                    (System.nanoTime() - startTime)
                    / 1_000_000;

            List<String> warnings =
                    new ArrayList<>();

            if (tables.isEmpty()) {

                warnings.add(
                        "No bordered tables detected"
                );
            }

            warnings.add(
                    "Extracted medical content is not "
                    + "clinically verified"
            );

            warnings.add(
                    "Results are not yet indexed "
                    + "into the RAG knowledge base"
            );

            return ResponseEntity.ok(

                    new GridOcrResponse(

                            document.getId(),

                            document.getFileName(),

                            page,

                            image.getWidth(),

                            image.getHeight(),

                            tables.size(),

                            totalCells,

                            extracted,

                            empty,

                            unreadable,

                            failed,

                            processingTime,

                            "OPENCV_TESSERACT_CELL_OCR",

                            tables,

                            List.copyOf(warnings)
                    )
            );

        } finally {

            image.flush();
        }
    }

    private BufferedImage loadImage(

            Document document,

            int page

    ) throws IOException {

        byte[] bytes = document.getFileData();

        String mimeType =
                document.getContentType();

        if (bytes == null || bytes.length == 0) {

            throw new IOException(
                    "Document contains no data"
            );
        }

        if ("application/pdf".equals(mimeType)) {

            try (PDDocument pdf =
                    Loader.loadPDF(bytes)) {

                if (page > pdf.getNumberOfPages()) {

                    throw new ResponseStatusException(

                            HttpStatus.BAD_REQUEST,

                            "Requested page does not exist"
                    );
                }

                PDPage pdfPage =
                        pdf.getPage(page - 1);

                double scale = layoutDpi / 72.0;

                double width = Math.ceil(

                        pdfPage.getCropBox().getWidth()
                        * scale
                );

                double height = Math.ceil(

                        pdfPage.getCropBox().getHeight()
                        * scale
                );

                if (width * height > maxImagePixels) {

                    throw new ResponseStatusException(

                            HttpStatus.BAD_REQUEST,

                            "PDF exceeds image pixel limit"
                    );
                }

                PDFRenderer renderer =
                        new PDFRenderer(pdf);

                return renderer.renderImageWithDPI(

                        page - 1,

                        layoutDpi,

                        ImageType.RGB
                );
            }
        }

        if (mimeType != null
                && mimeType.startsWith("image/")) {

            if (page != 1) {

                throw new ResponseStatusException(

                        HttpStatus.BAD_REQUEST,

                        "Images support page 1 only"
                );
            }

            BufferedImage image =
                    ImageIO.read(

                            new ByteArrayInputStream(bytes)
                    );

            if (image == null) {

                throw new IOException(
                        "Invalid image document"
                );
            }

            return image;
        }

        throw new ResponseStatusException(

                HttpStatus.UNSUPPORTED_MEDIA_TYPE,

                "Only PDF and images are supported"
        );
    }

    private void validateImageSize(
            BufferedImage image) {

        long pixels =
                (long) image.getWidth()
                * image.getHeight();

        if (pixels > maxImagePixels) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "Image exceeds pixel limit"
            );
        }
    }
}
