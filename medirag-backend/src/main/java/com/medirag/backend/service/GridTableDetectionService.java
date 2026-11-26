
package com.medirag.backend.service;

import java.awt.image.BufferedImage;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Size;

import org.opencv.imgproc.Imgproc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medirag.backend.dto.TableRegion;

@Service
public class GridTableDetectionService {

    private final ImagePreprocessingService
            preprocessingService;

    @Value("${medirag.table.grid.horizontal-scale:40}")
    private int horizontalScale;

    @Value("${medirag.table.grid.vertical-scale:100}")
    private int verticalScale;

    @Value("${medirag.table.grid.min-width-ratio:0.15}")
    private double minWidthRatio;

    @Value("${medirag.table.grid.min-height:30}")
    private int minHeight;

    @Value("${medirag.table.grid.line-coverage:0.65}")
    private double lineCoverage;

    @Value("${medirag.table.grid.merge-distance:6}")
    private int mergeDistance;

    public GridTableDetectionService(
            ImagePreprocessingService preprocessingService) {

        this.preprocessingService =
                preprocessingService;
    }

    // ==========================================
    // MAIN TABLE DETECTION
    // ==========================================

    public List<TableRegion> detectTables(

            BufferedImage image,

            int pageNumber

    ) throws IOException {

        Mat source = preprocessingService.toMat(image);

        Mat binary = null;

        Mat horizontal = new Mat();

        Mat vertical = new Mat();

        Mat gridMask = new Mat();

        Mat contourImage = new Mat();

        Mat hierarchy = new Mat();

        Mat horizontalKernel = null;

        Mat verticalKernel = null;

        List<MatOfPoint> contours = new ArrayList<>();

        try {

            binary = preprocessingService.preprocess(source);

            int horizontalSize = Math.max(
                    15,
                    binary.cols() / Math.max(1, horizontalScale)
            );

            int verticalSize = Math.max(
                    10,
                    binary.rows() / Math.max(1, verticalScale)
            );

            // Horizontal table borders.
            horizontalKernel =
                    Imgproc.getStructuringElement(

                            Imgproc.MORPH_RECT,

                            new Size(horizontalSize, 1)
                    );

            Imgproc.morphologyEx(

                    binary,

                    horizontal,

                    Imgproc.MORPH_OPEN,

                    horizontalKernel
            );

            // Vertical table borders.
            verticalKernel =
                    Imgproc.getStructuringElement(

                            Imgproc.MORPH_RECT,

                            new Size(1, verticalSize)
                    );

            Imgproc.morphologyEx(

                    binary,

                    vertical,

                    Imgproc.MORPH_OPEN,

                    verticalKernel
            );

            // Combine both masks.
            Core.bitwise_or(
                    horizontal,
                    vertical,
                    gridMask
            );

            gridMask.copyTo(contourImage);

            // Find independent connected table regions.
            Imgproc.findContours(

                    contourImage,

                    contours,

                    hierarchy,

                    Imgproc.RETR_EXTERNAL,

                    Imgproc.CHAIN_APPROX_SIMPLE
            );

            List<Rect> candidates = new ArrayList<>();

            int minimumWidth = (int) (
                    source.cols() * minWidthRatio
            );

            for (MatOfPoint contour : contours) {

                Rect rect =
                        Imgproc.boundingRect(contour);

                if (rect.width < minimumWidth) {
                    continue;
                }

                if (rect.height < minHeight) {
                    continue;
                }

                candidates.add(rect);
            }

            candidates.sort(

                    Comparator
                            .comparingInt((Rect r) -> r.y)
                            .thenComparingInt(r -> r.x)
            );

            List<TableRegion> tables = new ArrayList<>();

            for (Rect rect : candidates) {

                List<Integer> horizontalLines =
                        detectHorizontalLines(
                                horizontal,
                                rect
                        );

                List<Integer> verticalLines =
                        detectVerticalLines(
                                vertical,
                                rect
                        );

                // A grid needs at least two boundaries
                // in each direction.
                if (horizontalLines.size() < 2
                        || verticalLines.size() < 2) {

                    continue;
                }

                int rows =
                        horizontalLines.size() - 1;

                int columns =
                        verticalLines.size() - 1;

                // Ignore simple decorative rectangles.
                if (rows == 1 && columns == 1) {

                    continue;
                }

                tables.add(new TableRegion(

                        tables.size(),

                        pageNumber,

                        rect.x,

                        rect.y,

                        rect.width,

                        rect.height,

                        rows,

                        columns,

                        List.copyOf(horizontalLines),

                        List.copyOf(verticalLines)
                ));
            }

            return List.copyOf(tables);

        } finally {

            source.release();

            if (binary != null) {
                binary.release();
            }

            horizontal.release();

            vertical.release();

            gridMask.release();

            contourImage.release();

            hierarchy.release();

            if (horizontalKernel != null) {
                horizontalKernel.release();
            }

            if (verticalKernel != null) {
                verticalKernel.release();
            }

            for (MatOfPoint contour : contours) {
                contour.release();
            }
        }
    }

    // ==========================================
    // HORIZONTAL BOUNDARIES
    // ==========================================

    private List<Integer> detectHorizontalLines(

            Mat horizontalMask,

            Rect rect) {

        Mat region = horizontalMask.submat(rect);

        List<Integer> positions = new ArrayList<>();

        try {

            int requiredPixels = (int) Math.ceil(
                    rect.width * lineCoverage
            );

            for (int y = 0; y < region.rows(); y++) {

                Mat row = region.row(y);

                try {

                    int count = Core.countNonZero(row);

                    if (count >= requiredPixels) {

                        positions.add(rect.y + y);
                    }

                } finally {

                    row.release();
                }
            }

            return mergeNearbyPositions(positions);

        } finally {

            region.release();
        }
    }

    // ==========================================
    // VERTICAL BOUNDARIES
    // ==========================================

    private List<Integer> detectVerticalLines(

            Mat verticalMask,

            Rect rect) {

        Mat region = verticalMask.submat(rect);

        List<Integer> positions = new ArrayList<>();

        try {

            int requiredPixels = (int) Math.ceil(
                    rect.height * lineCoverage
            );

            for (int x = 0; x < region.cols(); x++) {

                Mat column = region.col(x);

                try {

                    int count = Core.countNonZero(column);

                    if (count >= requiredPixels) {

                        positions.add(rect.x + x);
                    }

                } finally {

                    column.release();
                }
            }

            return mergeNearbyPositions(positions);

        } finally {

            region.release();
        }
    }

    // ==========================================
    // MERGE THICK OR DUPLICATE LINES
    // ==========================================

    private List<Integer> mergeNearbyPositions(

            List<Integer> positions) {

        if (positions.isEmpty()) {

            return List.of();
        }

        List<Integer> merged = new ArrayList<>();

        int groupStart = positions.get(0);

        int previous = groupStart;

        for (int i = 1; i < positions.size(); i++) {

            int current = positions.get(i);

            if (current - previous > mergeDistance) {

                merged.add(
                        (groupStart + previous) / 2
                );

                groupStart = current;
            }

            previous = current;
        }

        merged.add(
                (groupStart + previous) / 2
        );

        return merged;
    }
}
