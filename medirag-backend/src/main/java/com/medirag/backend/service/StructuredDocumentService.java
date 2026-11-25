
package com.medirag.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medirag.backend.dto.ContentBlock;
import com.medirag.backend.dto.ParsedDocument;
import com.medirag.backend.dto.StructuredDocumentResponse;
import com.medirag.backend.entity.Document;

@Service
public class StructuredDocumentService {

    private final DocumentService documentService;
    private final UniversalDocumentParserService parserService;
    private final OcrService ocrService;

    @Value("${medirag.ocr.max-pdf-pages:30}")
    private int maxPdfPages;

    public StructuredDocumentService(
            DocumentService documentService,
            UniversalDocumentParserService parserService,
            OcrService ocrService) {

        this.documentService = documentService;
        this.parserService = parserService;
        this.ocrService = ocrService;
    }

    public StructuredDocumentResponse extract(Long documentId)
            throws IOException {

        Document document = documentService.getDocument(documentId);

        String fileName = document.getFileName();
        String mimeType = document.getContentType();
        byte[] bytes = document.getFileData();

        if (bytes == null || bytes.length == 0) {
            throw new IOException("Document contains no file data");
        }

        List<ContentBlock> blocks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String lowerName = fileName.toLowerCase(Locale.ROOT);

        if ("application/pdf".equals(mimeType)) {

            extractPdf(bytes, blocks, warnings);

        } else if (lowerName.endsWith(".xlsx")) {

            extractExcel(bytes, blocks, warnings);

        } else if (lowerName.endsWith(".csv")) {

            extractCsv(bytes, blocks);

        } else {

            ParsedDocument parsed =
                    parserService.parseDocument(documentId);

            String text = parsed.extractedText();

            if (text != null && !text.isBlank()) {
                addBlock(
                    blocks,
                    "TEXT",
                    null,
                    fileName,
                    text,
                    List.of(),
                    mimeType != null && mimeType.startsWith("image/")
                        ? "OCR"
                        : "TIKA"
                );
            } else {
                warnings.add(
                    "No readable text extracted from document");
            }

            if (lowerName.endsWith(".docx")
                    || lowerName.endsWith(".pptx")) {
                warnings.add(
                    "Embedded images and complex tables "
                    + "are not yet extracted as separate blocks");
            }
        }

        return new StructuredDocumentResponse(
                document.getId(),
                fileName,
                mimeType,
                blocks.size(),
                blocks,
                warnings
        );
    }

    private void extractPdf(
            byte[] bytes,
            List<ContentBlock> blocks,
            List<String> warnings) throws IOException {

        try (PDDocument pdf = Loader.loadPDF(bytes)) {

            int pageCount = pdf.getNumberOfPages();

            if (pageCount > maxPdfPages) {
                throw new IOException(
                    "PDF exceeds configured processing limit of "
                    + maxPdfPages + " pages");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(pdf);

            for (int i = 0; i < pageCount; i++) {

                int pageNumber = i + 1;

                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);

                String text = stripper.getText(pdf).trim();
                String method = "PDF_TEXT";

                if (text.isBlank()) {

                    BufferedImage image =
                            renderer.renderImageWithDPI(
                                i, 200, ImageType.RGB);

                    try {
                        ByteArrayOutputStream output =
                                new ByteArrayOutputStream();

                        ImageIO.write(image, "png", output);

                        text = ocrService.extractImageText(
                                output.toByteArray());

                        method = "OCR";

                    } finally {
                        image.flush();
                    }
                }

                if (!text.isBlank()) {
                    addBlock(
                        blocks,
                        "TEXT",
                        pageNumber,
                        "Page " + pageNumber,
                        text,
                        List.of(),
                        method
                    );
                } else {
                    warnings.add(
                        "No readable text on page " + pageNumber);
                }

                if ("OCR".equals(method)) {
                    warnings.add(
                        "Page " + pageNumber
                        + " used OCR; table layout and "
                        + "individual cell values are unverified");
                }
            }

            warnings.add(
                "PDF table structure is not yet reconstructed. "
                + "Pages containing both embedded and scanned "
                + "content may require additional OCR.");
        }
    }

    private void extractExcel(
            byte[] bytes,
            List<ContentBlock> blocks,
            List<String> warnings) throws IOException {

        try (Workbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(bytes))) {

            DataFormatter formatter = new DataFormatter();

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {

                Sheet sheet = workbook.getSheetAt(s);
                List<List<String>> rows = new ArrayList<>();

                for (Row row : sheet) {

                    List<String> cells = new ArrayList<>();

                    int cellCount = Math.max(
                        0, row.getLastCellNum());

                    for (int c = 0; c < cellCount; c++) {

                        Cell cell = row.getCell(
                            c,
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                        );

                        String value = cell == null
                            ? ""
                            : formatter.formatCellValue(cell);

                        cells.add(value);
                    }

                    rows.add(cells);
                }

                String content = rowsToText(rows);

                addBlock(
                    blocks,
                    "TABLE",
                    null,
                    "Sheet: " + sheet.getSheetName(),
                    content,
                    rows,
                    "APACHE_POI"
                );
            }

            warnings.add(
                "Excel formulas use stored or formatted values; "
                + "formula recalculation and embedded images "
                + "are not included.");
        }
    }

    private void extractCsv(
            byte[] bytes,
            List<ContentBlock> blocks) throws IOException {

        List<List<String>> rows = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(
                new String(bytes, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT)) {

            for (CSVRecord record : parser) {

                List<String> row = new ArrayList<>();

                record.forEach(row::add);

                rows.add(row);
            }
        }

        addBlock(
            blocks,
            "TABLE",
            null,
            "CSV",
            rowsToText(rows),
            rows,
            "CSV_PARSER"
        );
    }

    private String rowsToText(List<List<String>> rows) {

        StringBuilder builder = new StringBuilder();

        for (List<String> row : rows) {
            builder.append(
                String.join(" | ", row)
            ).append("\n");
        }

        return builder.toString().trim();
    }

    private void addBlock(
            List<ContentBlock> blocks,
            String type,
            Integer pageNumber,
            String location,
            String content,
            List<List<String>> rows,
            String method) {

        if ((content == null || content.isBlank())
                && rows.isEmpty()) {
            return;
        }

        blocks.add(new ContentBlock(
                blocks.size(),
                type,
                pageNumber,
                location,
                content,
                rows,
                method
        ));
    }
}
