
package com.medirag.backend.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medirag.backend.enums.DocumentProcessingStatus;
import com.medirag.backend.repository.DocumentRepository;

@Service
public class DocumentStatusService {

    private final DocumentRepository repository;

    public DocumentStatusService(
            DocumentRepository repository) {

        this.repository = repository;
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public boolean transition(
            Long documentId,
            DocumentProcessingStatus expectedStatus,
            DocumentProcessingStatus nextStatus,
            String error) {

        LocalDateTime processedAt =
                nextStatus == DocumentProcessingStatus.READY
                ? LocalDateTime.now()
                : null;

        return repository.transitionStatus(
                documentId,
                expectedStatus,
                nextStatus,
                error,
                processedAt
        ) == 1;
    }
}
