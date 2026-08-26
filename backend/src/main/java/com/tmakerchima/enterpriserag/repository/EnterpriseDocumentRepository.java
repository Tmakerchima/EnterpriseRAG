package com.tmakerchima.enterpriserag.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmakerchima.enterpriserag.model.EnterpriseAccessContext;
import com.tmakerchima.enterpriserag.model.EnterpriseChunkView;
import com.tmakerchima.enterpriserag.model.EnterpriseSearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * Lists original, citable chunks from the ACTIVE corpus. This inspection
     * query uses the exact same tenant/ACL boundary as online retrieval.
     */
    public List<EnterpriseChunkView> listChunks(EnterpriseAccessContext access, String query,
                                                 int limit, long offset) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return listVisibleChunks(access, limit, offset).items();
        }
        return searchVisibleChunks(access, normalizedQuery, limit, offset).items();
    }

    /**
     * Returns the requested chunk page and its total in one database round trip.
     * The previous implementation ran the same ACL/content scan twice (COUNT +
     * page query), which was especially expensive over a remote PostgreSQL
     * connection.
     */
    public ChunkSearchPage pageChunks(EnterpriseAccessContext access, String query,
                                      int limit, long offset) {
        String normalizedQuery = query == null ? "" : query.trim();
        return normalizedQuery.isEmpty()
                ? listVisibleChunks(access, limit, offset)
                : searchVisibleChunks(access, normalizedQuery, limit, offset);
    }

    private ChunkSearchPage listVisibleChunks(EnterpriseAccessContext access, int limit, long offset) {
        String sql = """
                SELECT c.chunk_id, c.document_id, d.external_id, d.source, d.source_type, d.title,
                       c.content, c.chunk_index, c.token_count, c.embedding IS NOT NULL AS embedded,
                       d.department, d.access_level, c.metadata,
                       (SELECT dataset_version FROM enterprise_corpora v
                        WHERE v.corpus_id = c.corpus_id) AS corpus_version,
                       count(*) OVER () AS total_count
                FROM enterprise_chunks c
                JOIN enterprise_documents d ON d.document_id = c.document_id
                WHERE d.deleted_at IS NULL
                  AND d.corpus_id = (SELECT corpus_id FROM enterprise_corpora
                                     WHERE state = 'ACTIVE' LIMIT 1)
                """ + ACL_FILTER + """
                ORDER BY d.title, c.chunk_index, c.chunk_id
                LIMIT ? OFFSET ?
                """;
        return mapChunkPage(jdbcTemplate.query(sql, this::mapChunkRow,
                access.role(), access.department(), access.tenantId(), limit, offset));
    }

    private ChunkSearchPage searchVisibleChunks(EnterpriseAccessContext access, String query,
                                                int limit, long offset) {
        String pattern = "%" + query + "%";
        String sql = """
                WITH active_corpus AS (
                    SELECT corpus_id FROM enterprise_corpora WHERE state = 'ACTIVE' LIMIT 1
                ), query_text AS (
                    SELECT websearch_to_tsquery('simple', ?) AS query
                ), matching AS MATERIALIZED (
                    SELECT c.chunk_id, ts_rank_cd(c.search_vector, query_text.query) AS search_score
                    FROM enterprise_chunks c
                    CROSS JOIN active_corpus a
                    CROSS JOIN query_text
                    WHERE c.corpus_id = a.corpus_id
                      AND c.search_vector @@ query_text.query
                    UNION ALL
                    SELECT c.chunk_id, 2.0::real AS search_score
                    FROM enterprise_documents d
                    JOIN enterprise_chunks c ON c.document_id = d.document_id
                    CROSS JOIN active_corpus a
                    WHERE d.deleted_at IS NULL
                      AND d.corpus_id = a.corpus_id
                      AND (d.title ILIKE ? OR d.external_id ILIKE ?)
                ), ranked AS (
                    SELECT chunk_id, max(search_score) AS search_score
                    FROM matching
                    GROUP BY chunk_id
                )
                SELECT c.chunk_id, c.document_id, d.external_id, d.source, d.source_type, d.title,
                       c.content, c.chunk_index, c.token_count, c.embedding IS NOT NULL AS embedded,
                       d.department, d.access_level, c.metadata,
                       (SELECT dataset_version FROM enterprise_corpora v
                        WHERE v.corpus_id = c.corpus_id) AS corpus_version,
                       count(*) OVER () AS total_count
                FROM ranked r
                JOIN enterprise_chunks c ON c.chunk_id = r.chunk_id
                JOIN enterprise_documents d ON d.document_id = c.document_id
                WHERE d.deleted_at IS NULL
                """ + ACL_FILTER + """
                ORDER BY r.search_score DESC, d.title, c.chunk_index, c.chunk_id
                LIMIT ? OFFSET ?
                """;
        return mapChunkPage(jdbcTemplate.query(sql, this::mapChunkRow, query, pattern, pattern,
                access.role(), access.department(), access.tenantId(), limit, offset));
    }

    public long countChunks(EnterpriseAccessContext access, String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        String pattern = "%" + normalizedQuery + "%";
        String sql = """
                SELECT count(*)
                FROM enterprise_chunks c
                JOIN enterprise_documents d ON d.document_id = c.document_id
                WHERE d.deleted_at IS NULL
                  AND d.corpus_id = (SELECT corpus_id FROM enterprise_corpora
                                     WHERE state = 'ACTIVE' LIMIT 1)
                  AND (? = '' OR d.title ILIKE ? OR d.external_id ILIKE ? OR c.content ILIKE ?)
                """ + ACL_FILTER;
        Long value = jdbcTemplate.queryForObject(sql, Long.class, normalizedQuery, pattern, pattern, pattern,
                access.role(), access.department(), access.tenantId());
        return value == null ? 0 : value;
    }

    /** Returns one full source chunk only when the caller may see it. */
    public Optional<EnterpriseChunkView> findChunk(String chunkId, EnterpriseAccessContext access) {
        String sql = """
                SELECT c.chunk_id, c.document_id, d.external_id, d.source, d.source_type, d.title,
                       c.content, c.chunk_index, c.token_count, c.embedding IS NOT NULL AS embedded,
                       d.department, d.access_level, c.metadata,
                       (SELECT dataset_version FROM enterprise_corpora v
                        WHERE v.corpus_id = c.corpus_id) AS corpus_version
                FROM enterprise_chunks c
                JOIN enterprise_documents d ON d.document_id = c.document_id
                WHERE d.deleted_at IS NULL
                  AND d.corpus_id = (SELECT corpus_id FROM enterprise_corpora
                                     WHERE state = 'ACTIVE' LIMIT 1)
                  AND c.chunk_id = ?
                """ + ACL_FILTER;
        return jdbcTemplate.query(sql, this::mapChunk, chunkId, access.role(), access.department(),
                access.tenantId()).stream().findFirst();
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

    private EnterpriseChunkView mapChunk(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new EnterpriseChunkView(
                rs.getString("chunk_id"),
                rs.getString("document_id"),
                rs.getString("external_id"),
                rs.getString("source"),
                rs.getString("source_type"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getInt("chunk_index"),
                rs.getInt("token_count"),
                rs.getBoolean("embedded"),
                rs.getString("department"),
                rs.getString("access_level"),
                rs.getString("corpus_version"),
                Collections.unmodifiableMap(new LinkedHashMap<>(readMetadata(rs.getString("metadata")))));
    }

    private ChunkRow mapChunkRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ChunkRow(mapChunk(rs, rowNum), rs.getLong("total_count"));
    }

    private ChunkSearchPage mapChunkPage(List<ChunkRow> rows) {
        long total = rows.isEmpty() ? 0 : rows.getFirst().total();
        return new ChunkSearchPage(rows.stream().map(ChunkRow::chunk).toList(), total);
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

    private record ChunkRow(EnterpriseChunkView chunk, long total) {}

    public record ChunkSearchPage(List<EnterpriseChunkView> items, long total) {}

}
