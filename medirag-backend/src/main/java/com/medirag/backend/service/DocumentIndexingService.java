
package com.medirag.backend.service;

import com.medirag.backend.dto.ContentBlock;
import com.medirag.backend.dto.ExtractedTable;
import com.medirag.backend.dto.StructuredDocumentResponse;
import com.medirag.backend.dto.TableCell;
import com.medirag.backend.dto.TableRow;

import com.medirag.backend.entity.DocumentChunk;
import com.medirag.backend.repository.DocumentChunkRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentIndexingService {

    private static final int CHUNK_SIZE = 1200;

    private static final int CHUNK_OVERLAP = 200;

    private final StructuredDocumentService
            structuredDocumentService;

    private final DocumentChunkRepository chunkRepository;

    public DocumentIndexingService(

            StructuredDocumentService structuredDocumentService,

            DocumentChunkRepository chunkRepository) {

        this.structuredDocumentService =
                structuredDocumentService;

        this.chunkRepository = chunkRepository;
    }

    // ==========================================
    // MAIN INDEXING METHOD
    // ==========================================

    @Transactional(rollbackFor = Exception.class)
    public int indexDocument(Long documentId)
            throws IOException {

        StructuredDocumentResponse structured =
                structuredDocumentService.extract(documentId);

        List<DocumentChunk> newChunks = new ArrayList<>();

        for (ContentBlock block : structured.blocks()) {

            if (block.table() != null) {

                indexReconstructedTable(
                        documentId,
                        block,
                        newChunks
                );

            } else if ("TABLE".equals(block.type())
                    && block.rows() != null
                    && !block.rows().isEmpty()) {

                indexNativeTable(
                        documentId,
                        block,
                        newChunks
                );

            } else {

                indexTextBlock(
                        documentId,
                        block,
                        newChunks
                );
            }
        }

        if (newChunks.isEmpty()) {

            throw new IllegalStateException(
                    "No readable content found after "
                    + "structured extraction");
        }

        // Replace previous chunks only after extraction succeeds.
        chunkRepository.deleteByDocumentId(documentId);

        chunkRepository.flush();

        chunkRepository.saveAll(newChunks);

        return newChunks.size();
    }

    // ==========================================
    // RECONSTRUCTED OCR TABLES
    // ==========================================

    private void indexReconstructedTable(

            Long documentId,

            ContentBlock block,

            List<DocumentChunk> chunks) {

        ExtractedTable table = block.table();

        for (TableRow row : table.rows()) {

            StringBuilder content = new StringBuilder();

            content.append(
                    "Content Type: TABLE_ROW\n");

            content.append(
                    createSourcePrefix(block));

            content.append("Table Type: ")
                   .append(table.tableType())
                   .append("\n");

            content.append("Table Index: ")
                   .append(table.tableIndex())
                   .append("\n");

            content.append("Row Index: ")
                   .append(row.rowIndex())
                   .append("\n");

            content.append(
                    "Clinical Verification: NOT VERIFIED\n");

            content.append(
                    "Row Requires Verification: ")
                   .append(row.requiresVerification())
                   .append("\n\n");

            for (TableCell cell : row.cells()) {

                content.append(cell.columnName())
                       .append(": ");

                if (cell.empty()
                        || cell.value() == null
                        || cell.value().isBlank()) {

                    content.append(
                            "[MISSING - NOT EXTRACTED]");

                } else {

                    content.append(cell.value());
                }

                content.append("\n");

                if (cell.confidence() != null) {

                    content.append("  OCR Confidence: ")
                           .append(String.format(
                                   Locale.ROOT,
                                   "%.2f",
                                   cell.confidence()))
                           .append("%\n");
                }

                content.append(
                        "  Extraction Issue Detected: ")
                       .append(cell.requiresVerification())
                       .append("\n");
            }

            content.append(
                    "\nEvidence rule: Missing values must "
                    + "not be inferred from another row. "
                    + "OCR values are not clinically verified."
            );

            addChunk(
                    documentId,
                    block.pageNumber(),
                    content.toString(),
                    chunks
            );
        }
    }

    // ==========================================
    // EXCEL AND CSV TABLES
    // ==========================================

    private void indexNativeTable(

            Long documentId,

            ContentBlock block,

            List<DocumentChunk> chunks) {

        List<List<String>> rows = block.rows();

        if (rows == null || rows.isEmpty()) {
            return;
        }

        // Header detection is not yet implemented.
        // Preserve every row without assuming the first
        // row is a header.
        for (int rowIndex = 0;
                rowIndex < rows.size();
                rowIndex++) {

            List<String> row = rows.get(rowIndex);

            boolean hasContent = row.stream()
                    .anyMatch(value ->
                            value != null
                            && !value.isBlank());

            if (!hasContent) {
                continue;
            }

            StringBuilder content = new StringBuilder();

            content.append(
                    "Content Type: TABLE_ROW\n");

            content.append(
                    createSourcePrefix(block));

            content.append("Extracted Row Index: ")
                   .append(rowIndex)
                   .append("\n");

            content.append(
                    "Column Headers: NOT VERIFIED\n\n");

            for (int c = 0; c < row.size(); c++) {

                String value = row.get(c);

                content.append("Column ")
                       .append(c + 1)
                       .append(": ")
                       .append(value == null || value.isBlank()
                               ? "[MISSING]"
                               : value)
                       .append("\n");
            }

            content.append(
                    "\nColumn meanings must not be assumed."
            );

            addChunk(
                    documentId,
                    block.pageNumber(),
                    content.toString(),
                    chunks
            );
        }
    }

    // ==========================================
    // ORDINARY TEXT BLOCKS
    // ==========================================

    private void indexTextBlock(

            Long documentId,

            ContentBlock block,

            List<DocumentChunk> chunks) {

        String content = block.content();

        if (content == null || content.isBlank()) {
            return;
        }

        String prefix =
                "Content Type: TEXT\n"
                + createSourcePrefix(block)
                + "\n";

        List<String> parts = splitText(content);

        for (String part : parts) {

            addChunk(
                    documentId,
                    block.pageNumber(),
                    prefix + part,
                    chunks
            );
        }
    }

    // ==========================================
    // SOURCE METADATA
    // ==========================================

    private String createSourcePrefix(
            ContentBlock block) {

        StringBuilder prefix = new StringBuilder();

        prefix.append("Location: ")
              .append(block.location() == null
                      ? "Unknown"
                      : block.location())
              .append("\n");

        prefix.append("Extraction Method: ")
              .append(block.extractionMethod())
              .append("\n");

        if (block.pageNumber() != null) {

            prefix.append("Page: ")
                  .append(block.pageNumber())
                  .append("\n");
        }

        prefix.append("Requires Verification: ")
              .append(block.requiresVerification())
              .append("\n");

        if (block.warnings() != null) {

            for (String warning : block.warnings()) {

                prefix.append("Extraction Warning: ")
                      .append(warning)
                      .append("\n");
            }
        }

        return prefix.toString();
    }

    // ==========================================
    // CREATE DATABASE CHUNK
    // ==========================================

    private void addChunk(

            Long documentId,

            Integer pageNumber,

            String content,

            List<DocumentChunk> chunks) {

        if (content == null || content.isBlank()) {
            return;
        }

        DocumentChunk chunk = new DocumentChunk(

                documentId,

                chunks.size(),

                pageNumber,

                content.trim()
        );

        chunks.add(chunk);
    }

    // ==========================================
    // TEXT CHUNKING
    // ==========================================

    private List<String> splitText(String text) {

        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();

        List<String> chunks = new ArrayList<>();

        int start = 0;

        while (start < normalized.length()) {

            int end = Math.min(
                    start + CHUNK_SIZE,
                    normalized.length()
            );

            if (end < normalized.length()) {

                int paragraphBoundary =
                        normalized.lastIndexOf("\n\n", end);

                int lineBoundary =
                        normalized.lastIndexOf('\n', end);

                int wordBoundary =
                        normalized.lastIndexOf(' ', end);

                int minimumBoundary =
                        start + CHUNK_SIZE / 2;

                if (paragraphBoundary > minimumBoundary) {

                    end = paragraphBoundary;

                } else if (lineBoundary > minimumBoundary) {

                    end = lineBoundary;

                } else if (wordBoundary > minimumBoundary) {

                    end = wordBoundary;
                }
            }

            String part = normalized
                    .substring(start, end)
                    .trim();

            if (!part.isBlank()) {

                chunks.add(part);
            }

            if (end >= normalized.length()) {
                break;
            }

            start = Math.max(
                    start + 1,
                    end - CHUNK_OVERLAP
            );
        }

        return chunks;
    }
}
