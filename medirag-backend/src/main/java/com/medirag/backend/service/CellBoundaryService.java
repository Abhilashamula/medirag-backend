
package com.medirag.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.medirag.backend.dto.GridCell;
import com.medirag.backend.dto.TableRegion;

@Service
public class CellBoundaryService {

    public List<GridCell> generateCells(
            TableRegion table,
            int imageWidth,
            int imageHeight) {

        List<Integer> horizontal =
                table.horizontalLines();

        List<Integer> vertical =
                table.verticalLines();

        validateBoundaries(
                horizontal, imageHeight
        );

        validateBoundaries(
                vertical, imageWidth
        );

        if (horizontal.size() - 1 != table.totalRows()
                || vertical.size() - 1
                        != table.totalColumns()) {

            throw new IllegalArgumentException(
                    "Table dimensions do not match boundaries"
            );
        }

        List<GridCell> cells = new ArrayList<>();

        for (int row = 0;
                row < horizontal.size() - 1;
                row++) {

            for (int col = 0;
                    col < vertical.size() - 1;
                    col++) {

                int left = vertical.get(col);

                int right = vertical.get(col + 1);

                int top = horizontal.get(row);

                int bottom = horizontal.get(row + 1);

                cells.add(new GridCell(

                        table.tableIndex(),

                        table.pageNumber(),

                        row,

                        col,

                        left,

                        top,

                        right - left,

                        bottom - top
                ));
            }
        }

        return List.copyOf(cells);
    }

    private void validateBoundaries(
            List<Integer> lines,
            int imageLimit) {

        if (lines == null || lines.size() < 2) {

            throw new IllegalArgumentException(
                    "Insufficient cell boundaries"
            );
        }

        int previous = -1;

        for (Integer line : lines) {

            if (line == null
                    || line < 0
                    || line > imageLimit
                    || line <= previous) {

                throw new IllegalArgumentException(
                        "Invalid or unordered cell boundaries"
                );
            }

            previous = line;
        }
    }
}
