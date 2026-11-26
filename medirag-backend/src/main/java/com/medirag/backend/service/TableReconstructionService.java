
package com.medirag.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medirag.backend.dto.OcrWord;
import com.medirag.backend.dto.OcrResult;
import com.medirag.backend.dto.TableCell;
import com.medirag.backend.dto.TableRow;
import com.medirag.backend.dto.ExtractedTable;

@Service
public class TableReconstructionService {

    @Value("${medirag.ocr.low-confidence-threshold:70}")
    private double confidenceThreshold;

    @Value("${medirag.table.max-row-gap-factor:6}")
    private double maxRowGapFactor;

    @Value("${medirag.table.header-gap-factor:1.5}")
    private double headerGapFactor;

    private static final Set<String> HEADER_KEYWORDS = Set.of(
            "name",
            "test",
            "test name",
            "investigation",
            "parameter",
            "result",
            "value",
            "unit",
            "units",
            "reference",
            "reference value",
            "reference range",
            "normal range",
            "method",
            "description",
            "item",
            "quantity",
            "price",
            "amount",
            "date",
            "status",
            "remarks",
            "code",
            "dose",
            "frequency",
            "duration"
    );

    private record PhysicalRow(
            int pageNumber,
            int top,
            int bottom,
            List<OcrWord> words
    ) {

        double centerY() {
            return (top + bottom) / 2.0;
        }
    }

    private record HeaderColumn(
            String name,
            int left,
            int right
    ) {
    }

    public List<ExtractedTable> reconstruct(
            OcrResult ocrResult) {

        if (ocrResult == null
                || ocrResult.words() == null
                || ocrResult.words().isEmpty()) {

            return List.of();
        }

        List<OcrWord> words = ocrResult.words()
                .stream()
                .filter(word -> word.text() != null
                        && !word.text().isBlank()
                        && word.height() > 0
                        && word.width() > 0)
                .toList();

        if (words.isEmpty()) {
            return List.of();
        }

        double medianHeight = calculateMedianHeight(words);

        List<PhysicalRow> physicalRows =
                groupIntoPhysicalRows(words, medianHeight);

        List<ExtractedTable> tables = new ArrayList<>();

        int index = 0;

        while (index < physicalRows.size()) {

            PhysicalRow headerRow = physicalRows.get(index);

            List<HeaderColumn> columns =
                    detectHeaderColumns(headerRow);

            if (!isValidHeader(columns)) {

                index++;
                continue;
            }

            List<TableRow> tableRows = new ArrayList<>();

            List<String> warnings = new ArrayList<>();

            int previousBottom = headerRow.bottom();

            int nextIndex = index + 1;

            while (nextIndex < physicalRows.size()) {

                PhysicalRow currentRow =
                        physicalRows.get(nextIndex);

                if (currentRow.pageNumber()
                        != headerRow.pageNumber()) {
                    break;
                }

                if (isFooter(currentRow)) {
                    break;
                }

                if (isValidHeader(
                        detectHeaderColumns(currentRow))) {
                    break;
                }

                double gap =
                        currentRow.top() - previousBottom;

                if (gap > medianHeight * maxRowGapFactor) {
                    break;
                }

                TableRow tableRow = buildTableRow(
                        currentRow,
                        columns,
                        tableRows.size()
                );

                boolean hasContent = tableRow.cells()
                        .stream()
                        .anyMatch(cell -> !cell.empty());

                if (hasContent) {

                    tableRows.add(tableRow);

                    if (tableRow.cells().stream()
                            .anyMatch(TableCell::empty)) {

                        warnings.add(
                                "Row "
                                + (tableRow.rowIndex() + 1)
                                + " contains missing cells"
                        );
                    }

                    if (tableRow.requiresVerification()) {

                        warnings.add(
                                "Row "
                                + (tableRow.rowIndex() + 1)
                                + " requires verification"
                        );
                    }
                }

                previousBottom = currentRow.bottom();

                nextIndex++;
            }

            if (!tableRows.isEmpty()) {

                warnings.add(
                        "Table reconstructed using OCR "
                        + "coordinates and header heuristics"
                );

                warnings.add(
                        "Table structure and values "
                        + "must be verified against "
                        + "the original document"
                );

                List<String> headers = columns.stream()
                        .map(HeaderColumn::name)
                        .toList();

                tables.add(new ExtractedTable(

                        tables.size(),

                        headerRow.pageNumber(),

                        detectTableType(headers),

                        headers,

                        headers.size(),

                        tableRows.size(),

                        headerRow.top(),

                        tableRows.get(
                                tableRows.size() - 1
                        ).bottom(),

                        List.copyOf(tableRows),

                        List.copyOf(warnings)
                ));

                index = nextIndex;

            } else {

                index++;
            }
        }

        return List.copyOf(tables);
    }

    // ==========================================
    // GROUP WORDS INTO PHYSICAL ROWS
    // ==========================================

    private List<PhysicalRow> groupIntoPhysicalRows(
            List<OcrWord> words,
            double medianHeight) {

        List<OcrWord> sorted = new ArrayList<>(words);

        sorted.sort(
                Comparator
                        .comparingInt(OcrWord::pageNumber)
                        .thenComparingDouble(OcrWord::centerY)
                        .thenComparingInt(OcrWord::left)
        );

        List<PhysicalRow> rows = new ArrayList<>();

        List<OcrWord> currentWords = new ArrayList<>();

        int currentPage = -1;

        double anchorY = 0;

        double tolerance =
                Math.max(5, medianHeight * 0.55);

        for (OcrWord word : sorted) {

            if (currentWords.isEmpty()) {

                currentWords.add(word);

                currentPage = word.pageNumber();

                anchorY = word.centerY();

                continue;
            }

            boolean samePage =
                    word.pageNumber() == currentPage;

            boolean sameRow =
                    Math.abs(word.centerY() - anchorY)
                    <= tolerance;

            if (samePage && sameRow) {

                currentWords.add(word);

            } else {

                rows.add(createPhysicalRow(currentWords));

                currentWords = new ArrayList<>();

                currentWords.add(word);

                currentPage = word.pageNumber();

                anchorY = word.centerY();
            }
        }

        if (!currentWords.isEmpty()) {

            rows.add(createPhysicalRow(currentWords));
        }

        return rows;
    }

    private PhysicalRow createPhysicalRow(
            List<OcrWord> words) {

        List<OcrWord> sorted = new ArrayList<>(words);

        sorted.sort(
                Comparator.comparingInt(OcrWord::left)
        );

        int top = sorted.stream()
                .mapToInt(OcrWord::top)
                .min()
                .orElse(0);

        int bottom = sorted.stream()
                .mapToInt(OcrWord::bottom)
                .max()
                .orElse(0);

        return new PhysicalRow(

                sorted.get(0).pageNumber(),

                top,

                bottom,

                List.copyOf(sorted)
        );
    }

    // ==========================================
    // DETECT TABLE HEADERS
    // ==========================================

    private List<HeaderColumn> detectHeaderColumns(
            PhysicalRow row) {

        List<OcrWord> words = row.words();

        if (words.isEmpty()) {
            return List.of();
        }

        double averageHeight = words.stream()
                .mapToInt(OcrWord::height)
                .average()
                .orElse(20);

        double gapThreshold = Math.max(
                12,
                averageHeight * headerGapFactor
        );

        List<HeaderColumn> columns = new ArrayList<>();

        List<OcrWord> currentColumn = new ArrayList<>();

        for (OcrWord word : words) {

            if (currentColumn.isEmpty()) {

                currentColumn.add(word);

                continue;
            }

            int previousRight = currentColumn.stream()
                    .mapToInt(OcrWord::right)
                    .max()
                    .orElse(0);

            int gap = word.left() - previousRight;

            if (gap > gapThreshold) {

                columns.add(
                        createHeaderColumn(currentColumn)
                );

                currentColumn = new ArrayList<>();
            }

            currentColumn.add(word);
        }

        if (!currentColumn.isEmpty()) {

            columns.add(
                    createHeaderColumn(currentColumn)
            );
        }

        return columns;
    }

    private HeaderColumn createHeaderColumn(
            List<OcrWord> words) {

        String name = words.stream()
                .map(OcrWord::text)
                .collect(Collectors.joining(" "));

        int left = words.stream()
                .mapToInt(OcrWord::left)
                .min()
                .orElse(0);

        int right = words.stream()
                .mapToInt(OcrWord::right)
                .max()
                .orElse(0);

        return new HeaderColumn(name, left, right);
    }

    private boolean isValidHeader(
            List<HeaderColumn> columns) {

        if (columns.size() < 2) {
            return false;
        }

        long recognizedHeaders = columns.stream()
                .map(HeaderColumn::name)
                .map(this::normalize)
                .filter(HEADER_KEYWORDS::contains)
                .count();

        return recognizedHeaders >= 2
                && recognizedHeaders * 2 >= columns.size();
    }

    // ==========================================
    // BUILD TABLE ROW AND CELLS
    // ==========================================

    private TableRow buildTableRow(
            PhysicalRow row,
            List<HeaderColumn> columns,
            int rowIndex) {

        List<List<OcrWord>> groupedWords =
                new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {

            groupedWords.add(new ArrayList<>());
        }

        boolean ambiguousAssignment = false;

        for (OcrWord word : row.words()) {

            int columnIndex = findColumn(word, columns);

            groupedWords.get(columnIndex).add(word);

            if (crossesColumnBoundary(word, columns)) {

                ambiguousAssignment = true;
            }
        }

        List<TableCell> cells = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {

            List<OcrWord> cellWords = groupedWords.get(i);

            cellWords.sort(
                    Comparator.comparingInt(OcrWord::left)
            );

            boolean empty = cellWords.isEmpty();

            String value = empty
                    ? null
                    : cellWords.stream()
                            .map(OcrWord::text)
                            .collect(Collectors.joining(" "));

            Double confidence = empty
                    ? null
                    : cellWords.stream()
                            .mapToDouble(OcrWord::confidence)
                            .average()
                            .orElse(0);

            boolean lowConfidence = cellWords.stream()
                    .anyMatch(word ->
                            word.confidence()
                            < confidenceThreshold);

            boolean requiresVerification =
                    empty
                    || lowConfidence
                    || ambiguousAssignment;

            cells.add(new TableCell(

                    i,

                    columns.get(i).name(),

                    value,

                    confidence,

                    empty,

                    requiresVerification,

                    List.copyOf(cellWords)
            ));
        }

        Double averageConfidence = row.words()
                .stream()
                .mapToDouble(OcrWord::confidence)
                .average()
                .orElse(0);

        boolean requiresVerification =
                ambiguousAssignment
                || cells.stream().anyMatch(
                        TableCell::requiresVerification
                );

        return new TableRow(

                rowIndex,

                row.pageNumber(),

                row.top(),

                row.bottom(),

                averageConfidence,

                requiresVerification,

                List.copyOf(cells)
        );
    }

    // ==========================================
    // ASSIGN WORDS TO COLUMNS
    // ==========================================

    private int findColumn(
            OcrWord word,
            List<HeaderColumn> columns) {

        double centerX =
                word.left() + word.width() / 2.0;

        for (int i = 0; i < columns.size() - 1; i++) {

            double boundary =
                    columnBoundary(columns, i);

            if (centerX < boundary) {

                return i;
            }
        }

        return columns.size() - 1;
    }

    private double columnBoundary(
            List<HeaderColumn> columns,
            int index) {

        HeaderColumn current = columns.get(index);

        HeaderColumn next = columns.get(index + 1);

        return (current.right() + next.left()) / 2.0;
    }

    private boolean crossesColumnBoundary(
            OcrWord word,
            List<HeaderColumn> columns) {

        for (int i = 0; i < columns.size() - 1; i++) {

            double boundary =
                    columnBoundary(columns, i);

            if (word.left() < boundary
                    && word.right() > boundary) {

                return true;
            }
        }

        return false;
    }

    // ==========================================
    // TABLE CLASSIFICATION
    // ==========================================

    private String detectTableType(
            List<String> headers) {

        Set<String> normalized = headers.stream()
                .map(this::normalize)
                .collect(Collectors.toSet());

        boolean hasResult =
                normalized.contains("result");

        boolean hasReference =
                normalized.contains("reference value")
                || normalized.contains("reference range")
                || normalized.contains("normal range");

        if (hasResult && hasReference) {

            return "LABORATORY_RESULTS";
        }

        if (normalized.contains("quantity")
                && normalized.contains("amount")) {

            return "FINANCIAL_TABLE";
        }

        if (normalized.contains("dose")
                && normalized.contains("frequency")) {

            return "MEDICATION_TABLE";
        }

        return "GENERAL_TABLE";
    }

    // ==========================================
    // SUPPORTING METHODS
    // ==========================================

    private boolean isFooter(PhysicalRow row) {

        String text = normalize(
                row.words()
                        .stream()
                        .map(OcrWord::text)
                        .collect(Collectors.joining(" "))
        );

        return text.startsWith("entered by")
                || text.startsWith("approved by")
                || text.startsWith("verified by")
                || text.startsWith("authorized by")
                || text.startsWith("signature")
                || text.startsWith("end of report")
                || text.matches("page \\d+( of \\d+)?");
    }

    private double calculateMedianHeight(
            List<OcrWord> words) {

        List<Integer> heights = words.stream()
                .map(OcrWord::height)
                .filter(height -> height > 0)
                .sorted()
                .toList();

        if (heights.isEmpty()) {
            return 20;
        }

        int middle = heights.size() / 2;

        if (heights.size() % 2 == 0) {

            return (
                    heights.get(middle - 1)
                    + heights.get(middle)
            ) / 2.0;
        }

        return heights.get(middle);
    }

    private String normalize(String value) {

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
