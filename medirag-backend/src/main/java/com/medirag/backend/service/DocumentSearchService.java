
package com.medirag.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentSearchService {

    private final JdbcTemplate jdbcTemplate;

    public DocumentSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record SearchResult(
            Long documentId,
            String fileName,
            Integer chunkIndex,
            Integer pageNumber,
            String content
    ) {}

    public List<SearchResult> search(
            String question,
            int limit) {

        String sql = """
            SELECT
                c.document_id,
                d.file_name,
                c.chunk_index,
                c.page_number,
                c.content
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE to_tsvector('english', c.content)
                  @@ websearch_to_tsquery('english', ?)
            ORDER BY
                ts_rank(
                    to_tsvector('english', c.content),
                    websearch_to_tsquery('english', ?)
                ) DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new SearchResult(
                rs.getLong("document_id"),
                rs.getString("file_name"),
                rs.getInt("chunk_index"),
                (Integer) rs.getObject("page_number"),
                rs.getString("content")
            ),
            question,
            question,
            limit
        );
    }
}
