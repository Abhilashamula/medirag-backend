
package com.medirag.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VectorStoreService {

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEmbedding(
            Long chunkId,
            float[] embedding) {

        String sql = """
            UPDATE document_chunks
            SET embedding = CAST(? AS vector)
            WHERE id = ?
            """;

        int updated = jdbcTemplate.update(
                sql,
                toVectorString(embedding),
                chunkId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Chunk not found: " + chunkId);
        }
    }

    public String toVectorString(float[] vector) {

        if (vector == null || vector.length != 768) {
            throw new IllegalArgumentException(
                    "Vector must contain 768 values");
        }

        StringBuilder builder = new StringBuilder("[");

        for (int i = 0; i < vector.length; i++) {

            if (!Float.isFinite(vector[i])) {
                throw new IllegalArgumentException(
                        "Vector contains non-finite values");
            }

            if (i > 0) {
                builder.append(",");
            }

            builder.append(vector[i]);
        }

        builder.append("]");

        return builder.toString();
    }
}
