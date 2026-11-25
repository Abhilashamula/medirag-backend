
package com.medirag.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medirag.backend.entity.DocumentChunk;

@Repository
public interface DocumentChunkRepository
        extends JpaRepository<DocumentChunk, Long> {

    // Delete all chunks belonging to a document
    void deleteByDocumentId(Long documentId);

    // Count chunks belonging to a document
    long countByDocumentId(Long documentId);

    // Retrieve document chunks in their original order
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(
            Long documentId
    );
}
