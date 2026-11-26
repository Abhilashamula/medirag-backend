
package com.medirag.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medirag.backend.dto.OcrWord;
import com.medirag.backend.dto.OcrLine;
import com.medirag.backend.dto.OcrResult;

@Service
public class LayoutAwareOcrService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    LayoutAwareOcrService.class);

    @Value("${medirag.ocr.tesseract-command:tesseract}")
    private String tesseractCommand;

    @Value("${medirag.ocr.language:eng}")
    private String language;

    @Value("${medirag.ocr.timeout-seconds:60}")
    private long timeoutSeconds;

    @Value("${medirag.ocr.low-confidence-threshold:70}")
    private double lowConfidenceThreshold;

    @Value("${medirag.ocr.max-image-pixels:25000000}")
    private long maxImagePixels;

    public OcrResult extractImage(byte[] imageBytes)
            throws IOException {

        if (imageBytes == null || imageBytes.length == 0) {
            throw new IOException("Image is empty");
        }

        BufferedImage image = ImageIO.read(
                new ByteArrayInputStream(imageBytes));

        if (image == null) {
            throw new IOException(
                    "Unsupported or unreadable image");
        }

        try {
            return extractLayout(image, 1);
        } finally {
            image.flush();
        }
    }

    public OcrResult extractLayout(
            BufferedImage image,
            int pageNumber) throws IOException {

        if (image == null) {
            throw new IOException("Image is missing");
        }

        long pixels =
                (long) image.getWidth() * image.getHeight();

        if (pixels > maxImagePixels) {
            throw new IOException(
                    "Image exceeds configured OCR pixel limit");
        }

        Path tempDirectory =
                Files.createTempDirectory("medirag-layout-");

        Process process = null;

        try {

            Path inputPath =
                    tempDirectory.resolve("input.png");

            Path outputBase =
                    tempDirectory.resolve("result");

            Path outputPath =
                    tempDirectory.resolve("result.tsv");

            Path logPath =
                    tempDirectory.resolve("tesseract.log");

            boolean written = ImageIO.write(
                    image,
                    "png",
                    inputPath.toFile());

            if (!written) {
                throw new IOException(
                        "Unable to write OCR input image");
            }

            ProcessBuilder builder = new ProcessBuilder(

                    tesseractCommand,

                    inputPath.toString(),

                    outputBase.toString(),

                    "-l",

                    language,

                    "--psm",

                    "3",

                    "tsv"
            );

            builder.redirectErrorStream(true);
            builder.redirectOutput(logPath.toFile());

            process = builder.start();

            boolean finished;

            try {

                finished = process.waitFor(
                        timeoutSeconds,
                        TimeUnit.SECONDS);

            } catch (InterruptedException e) {

                process.destroyForcibly();

                Thread.currentThread().interrupt();

                throw new IOException(
                        "OCR processing interrupted", e);
            }

            if (!finished) {

                process.destroyForcibly();

                throw new IOException(
                        "Layout OCR timed out after "
                        + timeoutSeconds + " seconds");
            }

            if (process.exitValue() != 0) {

                throw new IOException(
                        "Tesseract layout OCR failed. Exit code: "
                        + process.exitValue());
            }

            if (!Files.exists(outputPath)) {

                throw new IOException(
                        "Tesseract TSV output was not generated");
            }

            List<String> tsvLines = Files.readAllLines(
                    outputPath,
                    StandardCharsets.UTF_8);

            List<OcrWord> words =
                    parseWords(tsvLines, pageNumber);

            List<OcrLine> lines =
                    groupWordsIntoLines(words);

            return buildResult(
                    image,
                    words,
                    lines);

        } finally {

            if (process != null && process.isAlive()) {

                process.destroyForcibly();

                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            deleteTempDirectory(tempDirectory);
        }
    }

    private List<OcrWord> parseWords(
            List<String> tsvLines,
            int pageNumber) throws IOException {

        List<OcrWord> words = new ArrayList<>();

        for (int i = 1; i < tsvLines.size(); i++) {

            String[] columns =
                    tsvLines.get(i).split("\t", 12);

            if (columns.length < 12) {
                continue;
            }

            try {

                int level = Integer.parseInt(columns[0]);

                // Level 5 represents an individual word.
                if (level != 5) {
                    continue;
                }

                String text = columns[11].trim();

                if (text.isBlank()) {
                    continue;
                }

                double confidence =
                        Double.parseDouble(columns[10]);

                if (!Double.isFinite(confidence)
                        || confidence < 0
                        || confidence > 100) {
                    continue;
                }

                words.add(new OcrWord(

                        text,

                        pageNumber,

                        Integer.parseInt(columns[2]),

                        Integer.parseInt(columns[3]),

                        Integer.parseInt(columns[4]),

                        Integer.parseInt(columns[5]),

                        Integer.parseInt(columns[6]),

                        Integer.parseInt(columns[7]),

                        Integer.parseInt(columns[8]),

                        Integer.parseInt(columns[9]),

                        confidence
                ));

            } catch (NumberFormatException e) {

                throw new IOException(
                        "Invalid numeric field in OCR TSV output",
                        e);
            }
        }

        return words;
    }

    private List<OcrLine> groupWordsIntoLines(
            List<OcrWord> words) {

        Map<String, List<OcrWord>> grouped =
                new LinkedHashMap<>();

        for (OcrWord word : words) {

            String key =
                    word.pageNumber() + "-"
                    + word.blockNumber() + "-"
                    + word.paragraphNumber() + "-"
                    + word.lineNumber();

            grouped.computeIfAbsent(
                    key,
                    ignored -> new ArrayList<>()
            ).add(word);
        }

        List<OcrLine> lines = new ArrayList<>();

        for (List<OcrWord> lineWords : grouped.values()) {

            lineWords.sort(
                    Comparator.comparingInt(OcrWord::left));

            OcrWord first = lineWords.get(0);

            String text = lineWords.stream()
                    .map(OcrWord::text)
                    .collect(Collectors.joining(" "));

            int left = lineWords.stream()
                    .mapToInt(OcrWord::left)
                    .min()
                    .orElse(0);

            int top = lineWords.stream()
                    .mapToInt(OcrWord::top)
                    .min()
                    .orElse(0);

            int right = lineWords.stream()
                    .mapToInt(OcrWord::right)
                    .max()
                    .orElse(0);

            int bottom = lineWords.stream()
                    .mapToInt(OcrWord::bottom)
                    .max()
                    .orElse(0);

            double confidence = lineWords.stream()
                    .mapToDouble(OcrWord::confidence)
                    .average()
                    .orElse(0);

            lines.add(new OcrLine(

                    first.pageNumber(),

                    first.blockNumber(),

                    first.paragraphNumber(),

                    first.lineNumber(),

                    text,

                    left,

                    top,

                    right - left,

                    bottom - top,

                    confidence,

                    List.copyOf(lineWords)
            ));
        }

        return lines;
    }

    private OcrResult buildResult(
            BufferedImage image,
            List<OcrWord> words,
            List<OcrLine> lines) {

        String text = lines.stream()
                .map(OcrLine::text)
                .collect(Collectors.joining("\n"));

        double averageConfidence = words.stream()
                .mapToDouble(OcrWord::confidence)
                .average()
                .orElse(0);

        int lowConfidenceWords = (int) words.stream()
                .filter(word ->
                        word.confidence()
                        < lowConfidenceThreshold)
                .count();

        List<String> warnings = new ArrayList<>();

        if (words.isEmpty()) {

            warnings.add(
                    "No readable words detected by OCR");
        }

        if (lowConfidenceWords > 0) {

            warnings.add(
                    lowConfidenceWords
                    + " words have OCR confidence below "
                    + lowConfidenceThreshold);
        }

        warnings.add(
                "OCR confidence is not a guarantee "
                + "of text accuracy");

        warnings.add(
                "Medical values and table relationships "
                + "must be verified against the original document");

        return new OcrResult(

                text,

                image.getWidth(),

                image.getHeight(),

                words.size(),

                lines.size(),

                averageConfidence,

                lowConfidenceWords,

                List.copyOf(words),

                List.copyOf(lines),

                List.copyOf(warnings)
        );
    }

    private void deleteTempDirectory(Path directory) {

        try (Stream<Path> paths = Files.walk(directory)) {

            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {

                        try {

                            Files.deleteIfExists(path);

                        } catch (IOException e) {

                            log.warn(
                                    "Unable to remove an OCR temporary file");
                        }
                    });

        } catch (IOException e) {

            log.warn(
                    "Unable to complete OCR temporary file cleanup");
        }
    }
}
