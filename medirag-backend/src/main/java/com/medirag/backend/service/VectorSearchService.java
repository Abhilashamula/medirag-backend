
package com.medirag.backend.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VectorSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final OllamaEmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public VectorSearchService(
            JdbcTemplate jdbcTemplate,
            OllamaEmbeddingService embeddingService,
            VectorStoreService vectorStoreService) {

        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    public record VectorSearchResult(
            Long documentId,
            String fileName,
            Integer chunkIndex,
            Integer pageNumber,
            String content,
            Double similarity
    ) {}

    public List<VectorSearchResult> search(
            String question,
            int limit) {

        float[] questionEmbedding =
                embeddingService.embed(question);

        String vector =
                vectorStoreService.toVectorString(
                        questionEmbedding);

        String sql = """
            SELECT
                c.document_id,
                d.file_name,
                c.chunk_index,
                c.page_number,
                c.content,
                1 - (
                    c.embedding <=> CAST(? AS vector)
                ) AS similarity
            FROM document_chunks c
            JOIN documents d
                ON d.id = c.document_id
           WHERE c.embedding IS NOT NULL
        		AND d.processing_status = 'READY'
            ORDER BY
                c.embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new VectorSearchResult(
                        rs.getLong("document_id"),
                        rs.getString("file_name"),
                        rs.getInt("chunk_index"),
                        (Integer) rs.getObject("page_number"),
                        rs.getString("content"),
                        rs.getDouble("similarity")
                ),
                vector,
                vector,
                limit
        );
    }
}
