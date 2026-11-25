
package com.medirag.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.medirag.backend.enums.DocumentProcessingStatus;

@Service
public class DocumentIngestionService {

	private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

	private final DocumentIndexingService indexingService;
	private final DocumentEmbeddingService embeddingService;
	private final DocumentStatusService statusService;

	public DocumentIngestionService(DocumentIndexingService indexingService, DocumentEmbeddingService embeddingService,
			DocumentStatusService statusService) {

		this.indexingService = indexingService;
		this.embeddingService = embeddingService;
		this.statusService = statusService;
	}

	@Async("documentProcessingExecutor")
	public void processDocument(Long documentId) {

		boolean claimed = statusService.transition(documentId, DocumentProcessingStatus.QUEUED,
				DocumentProcessingStatus.INDEXING, null);

		if (!claimed) {

			log.info("Document {} is not queued. Skipping.", documentId);

			return;
		}

		DocumentProcessingStatus currentStatus = DocumentProcessingStatus.INDEXING;

		try {

			// STEP 1: Extract and index document.

			log.info("Starting indexing for document {}", documentId);

			int totalChunks = indexingService.indexDocument(documentId);

			if (totalChunks <= 0) {

				throw new IllegalStateException("No document chunks were generated");
			}

			log.info("Document {} indexed into {} chunks", documentId, totalChunks);

			// STEP 2: Update status to EMBEDDING.

			boolean embeddingStarted = statusService.transition(documentId, DocumentProcessingStatus.INDEXING,
					DocumentProcessingStatus.EMBEDDING, null);

			if (!embeddingStarted) {
				return;
			}

			currentStatus = DocumentProcessingStatus.EMBEDDING;

			// STEP 3: Generate embeddings.

			log.info("Generating embeddings for document {}", documentId);

			int embeddedChunks = embeddingService.generateEmbeddings(documentId);

			if (embeddedChunks != totalChunks) {

				throw new IllegalStateException("Not all chunks received embeddings");
			}

			// STEP 4: Mark document as READY.

			boolean ready = statusService.transition(documentId, DocumentProcessingStatus.EMBEDDING,
					DocumentProcessingStatus.READY, null);

			if (ready) {

				log.info("Document {} is READY. Chunks: {}, " + "Embeddings: {}", documentId, totalChunks,
						embeddedChunks);
			}

		} catch (Exception exception) {

			String safeError = currentStatus == DocumentProcessingStatus.INDEXING

					? "Document extraction or indexing failed."

					: "Embedding generation failed. " + "Check Ollama availability.";

			log.error("Processing failed for document {}. " + "Stage: {}, Error type: {}", documentId, currentStatus,
					exception.getClass().getSimpleName());

			statusService.transition(documentId, currentStatus, DocumentProcessingStatus.FAILED, safeError);
		}
	}
}
