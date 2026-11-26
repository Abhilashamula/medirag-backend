
package com.medirag.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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
import com.medirag.backend.dto.ExtractedTable;
import com.medirag.backend.dto.OcrResult;
import com.medirag.backend.dto.OcrWord;
import com.medirag.backend.dto.ParsedDocument;
import com.medirag.backend.dto.StructuredDocumentResponse;
import com.medirag.backend.dto.TableCell;
import com.medirag.backend.dto.TableRow;
import com.medirag.backend.entity.Document;

@Service
public class StructuredDocumentService {

    private final DocumentService documentService;

    private final UniversalDocumentParserService parserService;

    private final LayoutAwareOcrService layoutOcrService;

    private final TableReconstructionService tableService;

    @Value("${medirag.ocr.max-pdf-pages:30}")
    private int maxPdfPages;

    @Value("${medirag.ocr.layout-dpi:300}")
    private int layoutDpi;

    @Value("${medirag.ocr.max-image-pixels:25000000}")
    private long maxImagePixels;

    @Value("${medirag.extraction.advanced-pdf-ocr:true}")
    private boolean advancedPdfOcr;

    public StructuredDocumentService(

            DocumentService documentService,

            UniversalDocumentParserService parserService,

            LayoutAwareOcrService layoutOcrService,

            TableReconstructionService tableService) {

        this.documentService = documentService;
        this.parserService = parserService;
        this.layoutOcrService = layoutOcrService;
        this.tableService = tableService;
    }

    // ==========================================
    // MAIN DOCUMENT EXTRACTION
    // ==========================================

    public StructuredDocumentResponse extract(
            Long documentId) throws IOException {

        Document document =
                documentService.getDocument(documentId);

        String fileName = document.getFileName();

        String mimeType = document.getContentType();

        byte[] bytes = document.getFileData();

        if (bytes == null || bytes.length == 0) {

            throw new IOException(
                    "Document contains no file data");
        }

        List<ContentBlock> blocks = new ArrayList<>();

        List<String> warnings = new ArrayList<>();

        String lowerName = fileName == null
                ? ""
                : fileName.toLowerCase(Locale.ROOT);

        if ("application/pdf".equals(mimeType)) {

            extractPdf(bytes, blocks, warnings);

        } else if (mimeType != null
                && mimeType.startsWith("image/")) {

            OcrResult result =
                    layoutOcrService.extractImage(bytes);

            processOcrResult(
                    result,
                    1,
                    "Image",
                    blocks,
                    warnings
            );

        } else if (lowerName.endsWith(".xlsx")) {

            extractExcel(bytes, blocks, warnings);

        } else if (lowerName.endsWith(".csv")) {

            extractCsv(bytes, blocks);

        } else {

            extractOtherDocument(
                    documentId,
                    fileName,
                    lowerName,
                    blocks,
                    warnings
            );
        }

        if (blocks.isEmpty()) {

            warnings.add(
                    "No readable content blocks were extracted");
        }

        return new StructuredDocumentResponse(

                document.getId(),

                fileName,

                mimeType,

                blocks.size(),

                List.copyOf(blocks),

                List.copyOf(warnings)
        );
    }

    // ==========================================
    // PDF EXTRACTION
    // ==========================================

    private void extractPdf(

            byte[] bytes,

            List<ContentBlock> blocks,

            List<String> warnings

    ) throws IOException {

        try (PDDocument pdf = Loader.loadPDF(bytes)) {

            int pageCount = pdf.getNumberOfPages();

            if (pageCount > maxPdfPages) {

                throw new IOException(
                        "PDF exceeds configured processing "
                        + "limit of " + maxPdfPages + " pages");
            }

            PDFTextStripper stripper =
                    new PDFTextStripper();

            stripper.setSortByPosition(true);

            PDFRenderer renderer =
                    new PDFRenderer(pdf);

            for (int i = 0; i < pageCount; i++) {

                int pageNumber = i + 1;

                stripper.setStartPage(pageNumber);

                stripper.setEndPage(pageNumber);

                String embeddedText =
                        stripper.getText(pdf).trim();

                boolean useLayoutOcr =
                        advancedPdfOcr
                        || embeddedText.isBlank();

                if (useLayoutOcr) {

                    validatePdfPageSize(pdf.getPage(i));

                    BufferedImage image =
                            renderer.renderImageWithDPI(
                                    i,
                                    layoutDpi,
                                    ImageType.RGB
                            );

                    try {

                        OcrResult result =
                                layoutOcrService.extractLayout(
                                        image,
                                        pageNumber
                                );

                        if (result.words().isEmpty()
                                && !embeddedText.isBlank()) {

                            addTextBlock(
                                    blocks,
                                    pageNumber,
                                    "Page " + pageNumber,
                                    embeddedText,
                                    "PDF_TEXT_FALLBACK",
                                    null,
                                    List.of(
                                        "Layout OCR found no words. "
                                        + "Embedded text used instead. "
                                        + "Table relationships are unknown."
                                    )
                            );

                            warnings.add(
                                    "Page " + pageNumber
                                    + " used embedded text fallback");

                        } else {

                            processOcrResult(
                                    result,
                                    pageNumber,
                                    "Page " + pageNumber,
                                    blocks,
                                    warnings
                            );
                        }

                    } finally {

                        image.flush();
                    }

                } else {

                    addTextBlock(
                            blocks,
                            pageNumber,
                            "Page " + pageNumber,
                            embeddedText,
                            "PDF_TEXT",
                            null,
                            List.of(
                                "Embedded text does not guarantee "
                                + "correct table relationships"
                            )
                    );

                    warnings.add(
                            "Page " + pageNumber
                            + " used embedded PDF text");
                }
            }
        }
    }

    private void validatePdfPageSize(PDPage page)
            throws IOException {

        if (layoutDpi <= 0 || maxImagePixels <= 0) {

            throw new IOException(
                    "Invalid OCR rendering configuration");
        }

        double scale = layoutDpi / 72.0;

        double width = Math.ceil(
                page.getCropBox().getWidth() * scale);

        double height = Math.ceil(
                page.getCropBox().getHeight() * scale);

        if (width * height > maxImagePixels) {

            throw new IOException(
                    "PDF page exceeds configured OCR pixel limit");
        }
    }

    // ==========================================
    // ADVANCED OCR AND TABLE PROCESSING
    // ==========================================

    private void processOcrResult(

            OcrResult result,

            int pageNumber,

            String location,

            List<ContentBlock> blocks,

            List<String> warnings) {

        for (String warning : result.warnings()) {

            warnings.add(
                    location + ": " + warning);
        }

        List<ExtractedTable> tables =
                tableService.reconstruct(result);

        if (tables.isEmpty()) {

            addTextBlock(
                    blocks,
                    pageNumber,
                    location,
                    result.text(),
                    "LAYOUT_OCR",
                    result.averageConfidence(),
                    List.of(
                        "No supported table structure detected. "
                        + "Do not infer relationships between "
                        + "medical values from this text."
                    )
            );

            warnings.add(
                    location
                    + ": No supported table structure detected");

            return;
        }

        // Add reconstructed tables as separate blocks.
        for (ExtractedTable table : tables) {

            addTableBlock(
                    blocks,
                    table,
                    location
            );

            for (String warning : table.warnings()) {

                warnings.add(
                        location + ": " + warning);
            }
        }

        // Remove OCR words within reconstructed table regions.
        // This avoids indexing the same table twice.
        StringBuilder remainingText = new StringBuilder();

        for (var line : result.lines()) {

            List<OcrWord> remainingWords =
                    line.words()
                            .stream()
                            .filter(word ->
                                    !isInsideTable(word, tables))
                            .toList();

            String text = remainingWords.stream()
                    .map(OcrWord::text)
                    .collect(Collectors.joining(" "));

            if (!text.isBlank()) {

                remainingText.append(text)
                             .append("\n");
            }
        }

        if (!remainingText.toString().isBlank()) {

            addTextBlock(
                    blocks,
                    pageNumber,
                    location + " - Non-table text",
                    remainingText.toString().trim(),
                    "LAYOUT_OCR",
                    result.averageConfidence(),
                    List.of(
                        "OCR text outside detected table regions. "
                        + "Content remains unverified."
                    )
            );
        }

        warnings.add(
                location
                + ": Detected table regions were excluded "
                + "from non-table OCR text. Nearby text "
                + "may require manual review."
        );
    }

    private boolean isInsideTable(

            OcrWord word,

            List<ExtractedTable> tables) {

        for (ExtractedTable table : tables) {

            if (word.pageNumber() != table.pageNumber()) {
                continue;
            }

            boolean overlaps =
                    word.top() < table.bottom()
                    && word.bottom() > table.top();

            if (overlaps) {
                return true;
            }
        }

        return false;
    }

    // ==========================================
    // RECONSTRUCTED TABLE BLOCK
    // ==========================================

    private void addTableBlock(

            List<ContentBlock> blocks,

            ExtractedTable table,

            String location) {

        List<List<String>> rows = new ArrayList<>();

        rows.add(List.copyOf(table.headers()));

        StringBuilder content = new StringBuilder();

        content.append("Table type: ")
               .append(table.tableType())
               .append("\n");

        for (TableRow row : table.rows()) {

            List<String> values = new ArrayList<>();

            content.append("\nRow ")
                   .append(row.rowIndex() + 1)
                   .append(":\n");

            for (TableCell cell : row.cells()) {

                String value = cell.empty()
                        || cell.value() == null
                        ? ""
                        : cell.value();

                values.add(value);

                content.append(cell.columnName())
                       .append(": ")
                       .append(value.isBlank()
                               ? "[MISSING]"
                               : value);

                if (cell.requiresVerification()) {

                    content.append(
                            " [REQUIRES VERIFICATION]");
                }

                content.append("\n");
            }

            rows.add(List.copyOf(values));
        }

        Double confidence = table.rows()
                .stream()
                .filter(row ->
                        row.averageConfidence() != null)
                .mapToDouble(TableRow::averageConfidence)
                .average()
                .orElse(0);

        blocks.add(new ContentBlock(

                blocks.size(),

                "TABLE",

                table.pageNumber(),

                location + " - Table "
                        + (table.tableIndex() + 1),

                content.toString().trim(),

                List.copyOf(rows),

                "LAYOUT_OCR_TABLE",

                confidence,

                true,

                table.warnings(),

                table
        ));
    }

    // ==========================================
    // EXCEL EXTRACTION
    // ==========================================

    private void extractExcel(

            byte[] bytes,

            List<ContentBlock> blocks,

            List<String> warnings

    ) throws IOException {

        try (Workbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(bytes))) {

            DataFormatter formatter = new DataFormatter();

            formatter.setUseCachedValuesForFormulaCells(true);

            for (int s = 0;
                    s < workbook.getNumberOfSheets();
                    s++) {

                Sheet sheet = workbook.getSheetAt(s);

                List<List<String>> rows = new ArrayList<>();

                int maxColumns = 0;

                for (Row row : sheet) {

                    maxColumns = Math.max(
                            maxColumns,
                            row.getLastCellNum()
                    );
                }

                for (Row row : sheet) {

                    List<String> cells = new ArrayList<>();

                    for (int c = 0; c < maxColumns; c++) {

                        Cell cell = row.getCell(
                                c,
                                Row.MissingCellPolicy
                                    .RETURN_BLANK_AS_NULL
                        );

                        String value = cell == null
                                ? ""
                                : formatter.formatCellValue(cell);

                        cells.add(value);
                    }

                    rows.add(List.copyOf(cells));
                }

                addNativeTable(
                        blocks,
                        "Sheet: " + sheet.getSheetName(),
                        rows,
                        "APACHE_POI"
                );
            }

            warnings.add(
                    "Excel formulas use cached values. "
                    + "Formula recalculation, merged-cell "
                    + "relationships and embedded images "
                    + "are not included."
            );
        }
    }

    // ==========================================
    // CSV EXTRACTION
    // ==========================================

    private void extractCsv(

            byte[] bytes,

            List<ContentBlock> blocks

    ) throws IOException {

        List<List<String>> rows = new ArrayList<>();

        String csv = new String(
                bytes,
                StandardCharsets.UTF_8);

        if (csv.startsWith("\uFEFF")) {

            csv = csv.substring(1);
        }

        try (CSVParser parser = CSVParser.parse(
                csv,
                CSVFormat.DEFAULT)) {

            for (CSVRecord record : parser) {

                List<String> row = new ArrayList<>();

                record.forEach(row::add);

                rows.add(List.copyOf(row));
            }
        }

        addNativeTable(
                blocks,
                "CSV",
                rows,
                "CSV_PARSER"
        );
    }

    private void addNativeTable(

            List<ContentBlock> blocks,

            String location,

            List<List<String>> rows,

            String method) {

        if (rows.isEmpty()) {
            return;
        }

        String content = rows.stream()
                .map(row -> String.join(" | ", row))
                .collect(Collectors.joining("\n"));

        blocks.add(new ContentBlock(

                blocks.size(),

                "TABLE",

                null,

                location,

                content,

                List.copyOf(rows),

                method,

                null,

                true,

                List.of(
                    "Column header semantics and source "
                    + "values have not been verified"
                ),

                null
        ));
    }

    // ==========================================
    // OTHER DOCUMENT FORMATS
    // ==========================================

    private void extractOtherDocument(

            Long documentId,

            String fileName,

            String lowerName,

            List<ContentBlock> blocks,

            List<String> warnings

    ) throws IOException {

        ParsedDocument parsed =
                parserService.parseDocument(documentId);

        addTextBlock(
                blocks,
                null,
                fileName,
                parsed.extractedText(),
                "TIKA",
                null,
                List.of(
                    "Extracted text may not preserve "
                    + "complex document relationships"
                )
        );

        if (lowerName.endsWith(".docx")
                || lowerName.endsWith(".pptx")) {

            warnings.add(
                    "Embedded images and complex tables "
                    + "are not yet extracted separately");
        }
    }

    // ==========================================
    // COMMON TEXT BLOCK
    // ==========================================

    private void addTextBlock(

            List<ContentBlock> blocks,

            Integer pageNumber,

            String location,

            String content,

            String method,

            Double confidence,

            List<String> warnings) {

        if (content == null || content.isBlank()) {
            return;
        }

        blocks.add(new ContentBlock(

                blocks.size(),

                "TEXT",

                pageNumber,

                location,

                content,

                List.of(),

                method,

                confidence,

                true,

                List.copyOf(warnings),

                null
        ));
    }
}
