
package com.medirag.backend.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medirag.backend.entity.Document;
import com.medirag.backend.enums.DocumentProcessingStatus;

public interface DocumentRepository
        extends JpaRepository<Document, Long> {

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Document d
        SET d.processingStatus = :nextStatus,
            d.processingError = :error,
            d.processedAt = :processedAt
        WHERE d.id = :id
        AND d.processingStatus = :expectedStatus
        """)
    int transitionStatus(
            @Param("id") Long id,
            @Param("expectedStatus")
            DocumentProcessingStatus expectedStatus,
            @Param("nextStatus")
            DocumentProcessingStatus nextStatus,
            @Param("error") String error,
            @Param("processedAt")
            LocalDateTime processedAt
    );
}
