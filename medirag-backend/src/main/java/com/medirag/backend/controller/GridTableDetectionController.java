
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

import com.medirag.backend.dto.TableDetectionResponse;
import com.medirag.backend.dto.TableRegion;

import com.medirag.backend.entity.Document;

import com.medirag.backend.service.DocumentService;
import com.medirag.backend.service.GridTableDetectionService;

@RestController
@RequestMapping("/api/tables")
@CrossOrigin(origins = {

        "http://localhost:5173",

        "http://localhost:5174",

        "http://localhost:5175"
})
public class GridTableDetectionController {

    private final DocumentService documentService;

    private final GridTableDetectionService
            detectionService;

    @Value("${medirag.ocr.layout-dpi:300}")
    private int layoutDpi;

    @Value("${medirag.ocr.max-image-pixels:25000000}")
    private long maxImagePixels;

    public GridTableDetectionController(

            DocumentService documentService,

            GridTableDetectionService detectionService) {

        this.documentService = documentService;

        this.detectionService = detectionService;
    }

    @GetMapping("/detect/{documentId}")
    public ResponseEntity<TableDetectionResponse> detect(

            @PathVariable Long documentId,

            @RequestParam(defaultValue = "1") int page

    ) throws IOException {

        if (page < 1) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "Page number must be greater than zero"
            );
        }

        Document document =
                documentService.getDocument(documentId);

        BufferedImage image =
                loadImage(document, page);

        try {

            validateImageSize(image);

            List<TableRegion> tables =
                    detectionService.detectTables(
                            image,
                            page
                    );

            List<String> warnings =
                    new ArrayList<>();

            if (tables.isEmpty()) {

                warnings.add(
                        "No supported bordered tables detected"
                );
            }

            warnings.add(
                    "Detected table boundaries require verification"
            );

            warnings.add(
                    "Row counts include header rows where present"
            );

            warnings.add(
                    "Cell content extraction is not yet implemented"
            );

            TableDetectionResponse response =
                    new TableDetectionResponse(

                            document.getId(),

                            document.getFileName(),

                            page,

                            image.getWidth(),

                            image.getHeight(),

                            tables.size(),

                            "OPENCV_GRID_DETECTION",

                            tables,

                            List.copyOf(warnings)
                    );

            return ResponseEntity.ok(response);

        } finally {

            image.flush();
        }
    }

    // ==========================================
    // PDF OR IMAGE LOADING
    // ==========================================

    private BufferedImage loadImage(

            Document document,

            int page

    ) throws IOException {

        byte[] bytes = document.getFileData();

        String mimeType = document.getContentType();

        if (bytes == null || bytes.length == 0) {

            throw new IOException(
                    "Document contains no file data"
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

                            "PDF page exceeds image pixel limit"
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

                        "Image documents support page 1 only"
                );
            }

            BufferedImage image = ImageIO.read(
                    new ByteArrayInputStream(bytes)
            );

            if (image == null) {

                throw new IOException(
                        "Unsupported or invalid image"
                );
            }

            return image;
        }

        throw new ResponseStatusException(

                HttpStatus.UNSUPPORTED_MEDIA_TYPE,

                "Grid detection supports PDF and image documents"
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

                    "Image exceeds configured pixel limit"
            );
        }
    }
}
