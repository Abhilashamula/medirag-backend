
package com.medirag.backend.service;

import java.awt.image.BufferedImage;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medirag.backend.dto.GridCell;
import com.medirag.backend.dto.GridCellOcrResult;

@Service
public class CellOcrService {

    private final CellImagePreprocessingService
            preprocessingService;

    @Value("${medirag.ocr.tesseract-command:tesseract}")
    private String tesseractCommand;

    @Value("${medirag.ocr.language:eng}")
    private String language;

    @Value("${medirag.ocr.cell.timeout-seconds:15}")
    private long timeoutSeconds;

    @Value("${medirag.ocr.low-confidence-threshold:70}")
    private double confidenceThreshold;

    public CellOcrService(
            CellImagePreprocessingService
                    preprocessingService) {

        this.preprocessingService =
                preprocessingService;
    }

    public GridCellOcrResult extractCell(
            BufferedImage source,
            GridCell cell) {

        BufferedImage prepared = null;

        try {

            prepared =
                    preprocessingService.prepareCell(
                            source, cell
                    );

            if (preprocessingService
                    .isVisuallyBlank(prepared)) {

                return new GridCellOcrResult(

                        cell,

                        "",

                        null,

                        true,

                        true,

                        "EMPTY",

                        "VISUAL_BLANK_DETECTION",

                        List.of(
                            "Cell appears blank; verify original"
                        )
                );
            }

            OcrCandidate best =
                    recognize(prepared, 6);

            List<String> warnings =
                    new ArrayList<>();

            if (best.text().isBlank()
                    || best.confidence()
                            < confidenceThreshold) {

                OcrCandidate alternative =
                        recognize(prepared, 7);

                if (!best.text().isBlank()
                        && !alternative.text().isBlank()
                        && !best.text().equals(
                                alternative.text())) {

                    warnings.add(
                        "OCR modes produced different text"
                    );
                }

                if (!alternative.text().isBlank()
                        && (best.text().isBlank()
                        || alternative.confidence()
                                > best.confidence())) {

                    best = alternative;
                }
            }

            if (best.text().isBlank()) {

                return new GridCellOcrResult(

                        cell,

                        "",

                        null,

                        false,

                        true,

                        "UNREADABLE",

                        "TESSERACT_CELL_OCR",

                        List.of(
                            "Visible content could not be read"
                        )
                );
            }

            if (best.confidence()
                    < confidenceThreshold) {

                warnings.add(
                        "Low OCR confidence"
                );
            }

            warnings.add(
                    "Medical content requires source verification"
            );

            return new GridCellOcrResult(

                    cell,

                    best.text(),

                    best.confidence(),

                    false,

                    true,

                    "EXTRACTED",

                    "TESSERACT_PSM_" + best.psm(),

                    List.copyOf(warnings)
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Cell OCR interrupted", e
            );

        } catch (Exception e) {

            return new GridCellOcrResult(

                    cell,

                    "",

                    null,

                    false,

                    true,

                    "FAILED",

                    "TESSERACT_CELL_OCR",

                    List.of(
                        "Cell OCR processing failed: "
                        + e.getClass().getSimpleName()
                    )
            );

        } finally {

            if (prepared != null) {

                prepared.flush();
            }
        }
    }

    private OcrCandidate recognize(
            BufferedImage image,
            int psm
    ) throws IOException, InterruptedException {

        Path directory =
                Files.createTempDirectory(
                        "medirag-cell-ocr-"
                );

        Process process = null;

        try {

            Path input =
                    directory.resolve("input.png");

            Path outputBase =
                    directory.resolve("result");

            Path outputTsv =
                    directory.resolve("result.tsv");

            Path log =
                    directory.resolve("tesseract.log");

            if (!ImageIO.write(
                    image, "png", input.toFile())) {

                throw new IOException(
                        "Unable to write OCR input"
                );
            }

            ProcessBuilder builder =
                    new ProcessBuilder(

                            tesseractCommand,

                            input.toString(),

                            outputBase.toString(),

                            "-l",

                            language,

                            "--psm",

                            String.valueOf(psm),

                            "tsv"
                    );

            builder.redirectErrorStream(true);

            builder.redirectOutput(log.toFile());

            process = builder.start();

            boolean completed = process.waitFor(

                    timeoutSeconds,

                    TimeUnit.SECONDS
            );

            if (!completed) {

                process.destroyForcibly();

                process.waitFor(
                        2, TimeUnit.SECONDS
                );

                throw new IOException(
                        "Cell OCR timed out"
                );
            }

            if (process.exitValue() != 0
                    || !Files.exists(outputTsv)) {

                throw new IOException(
                        "Tesseract execution failed"
                );
            }

            return parseTsv(outputTsv, psm);

        } finally {

            if (process != null
                    && process.isAlive()) {

                process.destroyForcibly();
            }

            deleteDirectory(directory);
        }
    }

    private OcrCandidate parseTsv(
            Path file,
            int psm
    ) throws IOException {

        List<String> lines =
                Files.readAllLines(
                        file,
                        StandardCharsets.UTF_8
                );

        StringBuilder text =
                new StringBuilder();

        double confidenceSum = 0;

        int wordCount = 0;

        String previousLine = null;

        for (String line : lines) {

            String[] fields =
                    line.split("\t", 12);

            if (fields.length < 12
                    || !"5".equals(fields[0])) {

                continue;
            }

            String word = fields[11].trim();

            if (word.isBlank()) {

                continue;
            }

            double confidence;

            try {

                confidence =
                        Double.parseDouble(fields[10]);

            } catch (NumberFormatException e) {

                continue;
            }

            if (!Double.isFinite(confidence)
                    || confidence < 0
                    || confidence > 100) {

                continue;
            }

            String lineKey =

                    fields[1] + ":"
                    + fields[2] + ":"
                    + fields[3] + ":"
                    + fields[4];

            if (text.length() > 0) {

                if (lineKey.equals(previousLine)) {

                    text.append(" ");

                } else {

                    text.append("\n");
                }
            }

            text.append(word);

            previousLine = lineKey;

            confidenceSum += confidence;

            wordCount++;
        }

        double average = wordCount == 0
                ? 0
                : confidenceSum / wordCount;

        return new OcrCandidate(

                text.toString().trim(),

                average,

                psm
        );
    }

    private void deleteDirectory(Path directory) {

        try (Stream<Path> paths =
                Files.walk(directory)) {

            paths.sorted(
                    Comparator.reverseOrder()
            ).forEach(path -> {

                try {

                    Files.deleteIfExists(path);

                } catch (IOException ignored) {

                    // Temporary-file cleanup is best effort.
                }
            });

        } catch (IOException ignored) {

            // Cleanup must not hide the OCR result.
        }
    }

    private record OcrCandidate(

            String text,

            double confidence,

            int psm

    ) {
    }
}
