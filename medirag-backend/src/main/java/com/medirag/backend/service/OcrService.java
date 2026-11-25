
package com.medirag.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OcrService {

    @Value("${medirag.ocr.tesseract-command:tesseract}")
    private String tesseractCommand;

    @Value("${medirag.ocr.language:eng}")
    private String language;

    @Value("${medirag.ocr.timeout-seconds:60}")
    private long timeoutSeconds;

    @Value("${medirag.ocr.max-pdf-pages:30}")
    private int maxPdfPages;

    // OCR for JPG, PNG and supported image formats
    public String extractImageText(byte[] imageBytes)
            throws IOException {

        if (imageBytes == null || imageBytes.length == 0) {
            throw new IOException("Image is empty");
        }

        try (InputStream input =
                new ByteArrayInputStream(imageBytes)) {

            BufferedImage image = ImageIO.read(input);

            if (image == null) {
                throw new IOException(
                    "Unsupported or unreadable image format");
            }

            try {
                return recognizeImage(image);
            } finally {
                image.flush();
            }
        }
    }

    // OCR for scanned PDF documents
    public String extractScannedPdfText(byte[] pdfBytes)
            throws IOException {

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IOException("PDF is empty");
        }

        StringBuilder result = new StringBuilder();

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {

            int totalPages = pdf.getNumberOfPages();

            if (totalPages > maxPdfPages) {
                throw new IOException(
                    "PDF has " + totalPages
                    + " pages. OCR limit is "
                    + maxPdfPages + " pages.");
            }

            PDFRenderer renderer = new PDFRenderer(pdf);

            for (int page = 0; page < totalPages; page++) {

                System.out.println(
                    "OCR processing PDF page "
                    + (page + 1) + "/" + totalPages);

                BufferedImage image =
                    renderer.renderImageWithDPI(
                        page,
                        200,
                        ImageType.RGB
                    );

                try {
                    String pageText = recognizeImage(image);

                    result.append("\n--- Page ")
                          .append(page + 1)
                          .append(" ---\n")
                          .append(pageText)
                          .append("\n");

                } finally {
                    image.flush();
                }
            }
        }

        return result.toString().trim();
    }

    // Run local Tesseract OCR
    private String recognizeImage(BufferedImage image)
            throws IOException {

        Path tempDirectory =
                Files.createTempDirectory("medirag-ocr-");

        try {
            Path inputPath =
                    tempDirectory.resolve("input.png");

            Path outputBase =
                    tempDirectory.resolve("result");

            Path errorPath =
                    tempDirectory.resolve("ocr-error.log");

            boolean imageWritten = ImageIO.write(
                image,
                "png",
                inputPath.toFile()
            );

            if (!imageWritten) {
                throw new IOException(
                    "Unable to create OCR input image");
            }

            ProcessBuilder builder = new ProcessBuilder(
                tesseractCommand,
                inputPath.toString(),
                outputBase.toString(),
                "-l",
                language,
                "--psm",
                "3"
            );

            builder.redirectError(errorPath.toFile());

            Process process = builder.start();

            boolean finished;

            try {
                finished = process.waitFor(
                    timeoutSeconds,
                    TimeUnit.SECONDS
                );

            } catch (InterruptedException e) {

                process.destroyForcibly();
                Thread.currentThread().interrupt();

                throw new IOException(
                    "OCR process interrupted", e);
            }

            if (!finished) {
                process.destroyForcibly();

                throw new IOException(
                    "OCR processing timed out after "
                    + timeoutSeconds + " seconds");
            }

            if (process.exitValue() != 0) {

                String error = Files.exists(errorPath)
                    ? Files.readString(errorPath)
                    : "Unknown OCR error";

                throw new IOException(
                    "Tesseract OCR failed: " + error);
            }

            Path outputFile =
                    tempDirectory.resolve("result.txt");

            if (!Files.exists(outputFile)) {
                throw new IOException(
                    "OCR output file was not generated");
            }

            String text = Files.readString(
                outputFile,
                StandardCharsets.UTF_8
            );

            return text.trim();

        } finally {
            deleteTempDirectory(tempDirectory);
        }
    }

    // Delete temporary OCR files
    private void deleteTempDirectory(Path directory) {

        try (Stream<Path> paths = Files.walk(directory)) {

            paths.sorted(Comparator.reverseOrder())
                 .forEach(path -> {
                     try {
                         Files.deleteIfExists(path);
                     } catch (IOException ignored) {
                         // Best-effort temporary file cleanup
                     }
                 });

        } catch (IOException ignored) {
            // Best-effort cleanup
        }
    }
}
