
package com.medirag.backend.dto;

import java.time.LocalDateTime;

import com.medirag.backend.entity.Document;
import com.medirag.backend.enums.DocumentProcessingStatus;

public record DocumentResponse(

        Long id,

        String fileName,

        String contentType,

        Long fileSize,

        LocalDateTime uploadedAt,

        DocumentProcessingStatus processingStatus,

        String processingError,

        LocalDateTime processedAt

) {

    public DocumentResponse(
            Long id,
            String fileName,
            String contentType,
            Long fileSize,
            LocalDateTime uploadedAt) {

        this(
            id,
            fileName,
            contentType,
            fileSize,
            uploadedAt,
            null,
            null,
            null
        );
    }

    public static DocumentResponse from(Document document) {

        return new DocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getUploadedAt(),
                document.getProcessingStatus(),
                document.getProcessingError(),
                document.getProcessedAt()
        );
    }
}
