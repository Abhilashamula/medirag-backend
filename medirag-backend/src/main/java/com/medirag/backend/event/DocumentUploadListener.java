
package com.medirag.backend.event;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.medirag.backend.enums.DocumentProcessingStatus;
import com.medirag.backend.service.DocumentIngestionService;
import com.medirag.backend.service.DocumentStatusService;

@Component
public class DocumentUploadListener {

    private final DocumentIngestionService ingestionService;
    private final DocumentStatusService statusService;

    public DocumentUploadListener(
            DocumentIngestionService ingestionService,
            DocumentStatusService statusService) {

        this.ingestionService = ingestionService;
        this.statusService = statusService;
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleDocumentUploaded(
            DocumentUploadedEvent event) {

        try {

            ingestionService.processDocument(
                    event.documentId()
            );

        } catch (TaskRejectedException exception) {

            statusService.transition(
                    event.documentId(),
                    DocumentProcessingStatus.QUEUED,
                    DocumentProcessingStatus.FAILED,
                    "Processing queue is full. Please retry."
            );
        }
    }
}
