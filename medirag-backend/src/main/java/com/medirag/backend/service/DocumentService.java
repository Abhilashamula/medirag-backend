
package com.medirag.backend.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.Tika;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.medirag.backend.entity.Document;
import com.medirag.backend.enums.DocumentProcessingStatus;
import com.medirag.backend.event.DocumentUploadedEvent;
import com.medirag.backend.repository.DocumentRepository;

@Service
public class DocumentService {

    private static final long MAX_FILE_SIZE =
            50L * 1024 * 1024;

    private static final Map<String, Set<String>>
            SUPPORTED_FORMATS = Map.ofEntries(

        Map.entry(".pdf", Set.of(
            "application/pdf"
        )),

        Map.entry(".docx", Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )),

        Map.entry(".xlsx", Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )),

        Map.entry(".pptx", Set.of(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )),

        Map.entry(".txt", Set.of(
            "text/plain"
        )),

        Map.entry(".csv", Set.of(
            "text/csv",
            "text/plain"
        )),

        Map.entry(".json", Set.of(
            "application/json",
            "text/plain"
        )),

        Map.entry(".html", Set.of(
            "text/html",
            "text/plain"
        )),

        Map.entry(".htm", Set.of(
            "text/html",
            "text/plain"
        )),

        Map.entry(".xml", Set.of(
            "application/xml",
            "text/xml",
            "text/plain"
        )),

        Map.entry(".png", Set.of(
            "image/png"
        )),

        Map.entry(".jpg", Set.of(
            "image/jpeg"
        )),

        Map.entry(".jpeg", Set.of(
            "image/jpeg"
        )),

        Map.entry(".tif", Set.of(
            "image/tiff"
        )),

        Map.entry(".tiff", Set.of(
            "image/tiff"
        ))
    );

    private final DocumentRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Tika tika;

    public DocumentService(
            DocumentRepository repository,
            ApplicationEventPublisher eventPublisher) {

        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.tika = new Tika();
    }

    @Transactional(rollbackFor = Exception.class)
    public Document uploadDocument(MultipartFile file)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Maximum file size is 50 MB");
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "File name is required");
        }

        fileName = fileName.replace('\\', '/');

        fileName = fileName.substring(
                fileName.lastIndexOf('/') + 1
        );

        if (fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid file name");
        }

        if (fileName.length() > 255) {
            throw new IllegalArgumentException(
                    "File name is too long");
        }

        String lowerName =
                fileName.toLowerCase(Locale.ROOT);

        int dotIndex = lowerName.lastIndexOf('.');

        if (dotIndex < 0) {
            throw new IllegalArgumentException(
                    "File extension is required");
        }

        String extension =
                lowerName.substring(dotIndex);

        Set<String> allowedMimeTypes =
                SUPPORTED_FORMATS.get(extension);

        if (allowedMimeTypes == null) {
            throw new IllegalArgumentException(
                    "Unsupported file extension: "
                    + extension);
        }

        byte[] bytes = file.getBytes();

        if (bytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Maximum file size is 50 MB");
        }

        String detectedType = tika.detect(bytes);

        if (!allowedMimeTypes.contains(detectedType)) {
            throw new IllegalArgumentException(
                    "File type does not match extension. "
                    + "Detected: " + detectedType
                    + ", Extension: " + extension);
        }

        Document document = new Document();

        document.setFileName(fileName);
        document.setContentType(detectedType);
        document.setFileSize((long) bytes.length);
        document.setFileData(bytes);
        document.setUploadedAt(LocalDateTime.now());

        document.setProcessingStatus(
                DocumentProcessingStatus.QUEUED
        );

        document.setProcessingError(null);
        document.setProcessedAt(null);

        Document saved = repository.save(document);

        // Trigger automatic ingestion after commit.
        eventPublisher.publishEvent(
                new DocumentUploadedEvent(saved.getId())
        );

        return saved;
    }

    public List<Document> getAllDocuments() {
        return repository.findAll();
    }

    public Document getDocument(Long id) {

        return repository.findById(id)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document not found with ID: " + id
                    )
                );
    }

    @Transactional
    public void retryProcessing(Long id) {

        Document document = getDocument(id);

        DocumentProcessingStatus status =
                document.getProcessingStatus();

        if (status != DocumentProcessingStatus.FAILED
                && status != DocumentProcessingStatus.QUEUED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Document is already processing or ready"
            );
        }

        int updated = repository.transitionStatus(
                id,
                status,
                DocumentProcessingStatus.QUEUED,
                null,
                null
        );

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Document status changed. Please refresh."
            );
        }

        eventPublisher.publishEvent(
                new DocumentUploadedEvent(id)
        );
    }

    @Transactional
    public void deleteDocument(Long id) {

        Document document = getDocument(id);

        DocumentProcessingStatus status =
                document.getProcessingStatus();

        if (status == DocumentProcessingStatus.INDEXING
                || status == DocumentProcessingStatus.EMBEDDING
                || status == DocumentProcessingStatus.QUEUED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete a document while processing"
            );
        }

        repository.delete(document);
    }
}
