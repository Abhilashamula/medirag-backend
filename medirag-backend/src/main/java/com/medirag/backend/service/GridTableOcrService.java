
package com.medirag.backend.service;

import java.awt.image.BufferedImage;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medirag.backend.dto.GridCell;
import com.medirag.backend.dto.GridCellOcrResult;
import com.medirag.backend.dto.GridTableOcrResult;
import com.medirag.backend.dto.TableRegion;

@Service
public class GridTableOcrService {

    private final GridTableDetectionService
            detectionService;

    private final CellBoundaryService
            boundaryService;

    private final CellOcrService
            cellOcrService;

    @Value("${medirag.ocr.cell.max-cells-per-page:300}")
    private int maxCellsPerPage;

    public GridTableOcrService(

            GridTableDetectionService detectionService,

            CellBoundaryService boundaryService,

            CellOcrService cellOcrService) {

        this.detectionService = detectionService;

        this.boundaryService = boundaryService;

        this.cellOcrService = cellOcrService;
    }

    public List<GridTableOcrResult> extractTables(

            BufferedImage image,

            int pageNumber

    ) throws IOException {

        List<TableRegion> detectedTables =
                detectionService.detectTables(
                        image, pageNumber
                );

        long totalCells = detectedTables.stream()

                .mapToLong(table ->
                        (long) table.totalRows()
                        * table.totalColumns()
                )

                .sum();

        if (totalCells > maxCellsPerPage) {

            throw new IllegalArgumentException(
                    "Detected cells exceed configured limit"
            );
        }

        List<GridTableOcrResult> results =
                new ArrayList<>();

        for (TableRegion table : detectedTables) {

            results.add(
                    extractTable(image, table)
            );
        }

        return List.copyOf(results);
    }

    private GridTableOcrResult extractTable(

            BufferedImage image,

            TableRegion table) {

        List<GridCell> cells =
                boundaryService.generateCells(

                        table,

                        image.getWidth(),

                        image.getHeight()
                );

        List<List<GridCellOcrResult>> rows =
                new ArrayList<>();

        for (int row = 0;
                row < table.totalRows();
                row++) {

            rows.add(new ArrayList<>());
        }

        int extracted = 0;

        int empty = 0;

        int unreadable = 0;

        int failed = 0;

        double confidenceSum = 0;

        int confidenceCount = 0;

        for (GridCell cell : cells) {

            GridCellOcrResult result =
                    cellOcrService.extractCell(
                            image, cell
                    );

            rows.get(cell.rowIndex()).add(result);

            switch (result.status()) {

                case "EXTRACTED" -> extracted++;

                case "EMPTY" -> empty++;

                case "UNREADABLE" -> unreadable++;

                case "FAILED" -> failed++;

                default -> failed++;
            }

            if (result.confidence() != null) {

                confidenceSum += result.confidence();

                confidenceCount++;
            }
        }

        Double averageConfidence =
                confidenceCount == 0

                ? null

                : confidenceSum / confidenceCount;

        List<String> warnings =
                new ArrayList<>();

        if (unreadable > 0) {

            warnings.add(
                    unreadable + " unreadable cells"
            );
        }

        if (failed > 0) {

            warnings.add(
                    failed + " failed cells"
            );
        }

        warnings.add(
                "Table geometry and medical content "
                + "require verification"
        );

        warnings.add(
                "Merged cells and header semantics "
                + "are not yet resolved"
        );

        return new GridTableOcrResult(

                table.tableIndex(),

                table.pageNumber(),

                table.totalRows(),

                table.totalColumns(),

                cells.size(),

                extracted,

                empty,

                unreadable,

                failed,

                averageConfidence,

                true,

                rows.stream()
                        .map(List::copyOf)
                        .toList(),

                List.copyOf(warnings)
        );
    }
}
