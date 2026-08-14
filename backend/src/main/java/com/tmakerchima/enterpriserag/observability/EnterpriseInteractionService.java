package com.tmakerchima.enterpriserag.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmakerchima.enterpriserag.model.EnterpriseAccessContext;
import com.tmakerchima.enterpriserag.retrieval.EnterpriseRetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/** Async, redacted interaction persistence. Full prompts and document content are intentionally not stored. */
@Service
public class EnterpriseInteractionService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseInteractionService.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EnterpriseInteractionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Async("ragTelemetryExecutor")
    public void record(String requestId, String traceId, EnterpriseAccessContext access, String strategy,
                       String outcome, EnterpriseRetrievalResult retrieval) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO rag_interactions
                      (request_id, trace_id, tenant_id, role, strategy, lexical_backend,
                       source_ids, stage_timings, outcome)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
                    ON CONFLICT (request_id) DO NOTHING
                    """, requestId, traceId, pseudonymous(access.tenantId()), access.role(), strategy,
                    retrieval.metrics().lexicalBackend(), json(sourceIds(retrieval)), json(stageTimings(retrieval)), outcome);
        } catch (DataAccessException error) {
            log.debug("interaction persistence skipped: {}", error.getMostSpecificCause().getMessage());
        }
    }

    private List<String> sourceIds(EnterpriseRetrievalResult retrieval) {
        return retrieval.hits().stream().map(hit -> hit.externalId() + ":" + hit.chunkId()).toList();
    }

    private Object stageTimings(EnterpriseRetrievalResult retrieval) {
        return java.util.Map.of("vector_ms", retrieval.metrics().vectorMs(), "lexical_ms", retrieval.metrics().ftsMs(),
                "rrf_ms", retrieval.metrics().rrfMs(), "rerank_ms", retrieval.metrics().rerankMs());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            return "[]";
        }
    }

    private String pseudonymous(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.toHexString(value.hashCode());
    }
}
