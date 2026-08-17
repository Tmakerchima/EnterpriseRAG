package com.tmakerchima.enterpriserag.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmakerchima.enterpriserag.model.EnterpriseAccessContext;
import com.tmakerchima.enterpriserag.model.EnterpriseSearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/**
 * Enterprise 文档与 Chunk 的 PostgreSQL 持久化层。
 * 读取 ACTIVE corpus，并在 SQL 层完成租户和 ACL 过滤。
 */
@Repository
public class EnterpriseDocumentRepository {

    private static final String ACL_FILTER = """
            AND (? = 'admin' OR d.access_level = 'public' OR d.department = ?)
            AND d.tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EnterpriseDocumentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<EnterpriseSearchHit> searchKeyword(String query, EnterpriseAccessContext access, int topK) {
        String sql = """
                WITH query_text AS (
                    SELECT websearch_to_tsquery('simple', ?) AS query
                )
                SELECT c.chunk_id, c.document_id, d.external_id, d.source, d.source_type, d.title,
                       c.content, d.tenant_id, d.department, d.access_level, c.chunk_index,
                       ts_rank_cd(c.search_vector, query_text.query) AS score, c.metadata,
                       c.corpus_id, (SELECT dataset_version FROM enterprise_corpora v
                                     WHERE v.corpus_id = c.corpus_id) AS corpus_version
                FROM enterprise_chunks c
                JOIN enterprise_documents d ON d.document_id = c.document_id
                CROSS JOIN query_text
                WHERE d.deleted_at IS NULL
                  AND d.corpus_id = (SELECT corpus_id FROM enterprise_corpora
                                     WHERE state = 'ACTIVE' LIMIT 1)
                  AND c.search_vector @@ query_text.query
                """ + ACL_FILTER + """
                ORDER BY score DESC, c.chunk_id
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, this::mapHit, query, access.role(), access.department(),
                access.tenantId(), topK).stream()
                .map(hit -> hit.withScore(hit.score(), hit.rank()))
                .toList();
    }

    public List<EnterpriseSearchHit> searchVector(float[] embedding, EnterpriseAccessContext access,
                                                  int topK, double threshold) {
        String sql = """
                WITH query_embedding AS (
                    SELECT ?::vector AS embedding
                )
                SELECT c.chunk_id, c.document_id, d.external_id, d.source, d.source_type, d.title,
                       c.content, d.tenant_id, d.department, d.access_level, c.chunk_index,
                       1 - (c.embedding <=> query_embedding.embedding) AS score, c.metadata,
                       c.corpus_id, (SELECT dataset_version FROM enterprise_corpora v
                                     WHERE v.corpus_id = c.corpus_id) AS corpus_version
                FROM enterprise_chunks c
                JOIN enterprise_documents d ON d.document_id = c.document_id
                CROSS JOIN query_embedding
                WHERE d.deleted_at IS NULL
                  AND d.corpus_id = (SELECT corpus_id FROM enterprise_corpora
                                     WHERE state = 'ACTIVE' LIMIT 1)
                  AND c.embedding IS NOT NULL
                  AND 1 - (c.embedding <=> query_embedding.embedding) >= ?
                """ + ACL_FILTER + """
                ORDER BY c.embedding <=> query_embedding.embedding, c.chunk_id
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, this::mapHit, vectorLiteral(embedding), threshold, access.role(),
                access.department(), access.tenantId(), topK).stream()
                .map(hit -> hit.withScore(hit.score(), hit.rank()))
                .toList();
    }

    /**
     * 统一把 Supabase/ParadeDB 两个 lexical backend 的行映射成同一份安全结果。
     * BM25 数据源只负责执行搜索，原始 content 和 ACL 元数据仍使用同一映射规则。
     */
    public EnterpriseSearchHit mapHit(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>(readMetadata(rs.getString("metadata")));
        Object corpusId = rs.getObject("corpus_id");
        String corpusVersion = rs.getString("corpus_version");
        if (corpusId != null) metadata.put("corpus_id", corpusId.toString());
        if (corpusVersion != null && !corpusVersion.isBlank()) metadata.put("corpus_version", corpusVersion);
        return new EnterpriseSearchHit(
                rs.getString("chunk_id"),
                rs.getString("document_id"),
                rs.getString("external_id"),
                rs.getString("source"),
                rs.getString("source_type"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("tenant_id"),
                rs.getString("department"),
                rs.getString("access_level"),
                rs.getInt("chunk_index"),
                rs.getDouble("score"),
                rowNum + 1,
                Collections.unmodifiableMap(metadata));
    }

    private Map<String, Object> readMetadata(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    static String vectorLiteral(float[] embedding) {
        // pgvector JDBC 参数使用 [0.1,0.2,...] 文本格式后再由 SQL 转成 vector。
        if (embedding == null || embedding.length == 0) throw new IllegalArgumentException("embedding is empty");
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) result.append(',');
            result.append(Float.toString(embedding[i]));
        }
        return result.append(']').toString();
    }

}
