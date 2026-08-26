package com.tmakerchima.enterpriserag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class EnterpriseHumanReviewService {

    private static final Set<String> VERDICTS = Set.of("RELIABLE", "UNRELIABLE", "INSUFFICIENT_EVIDENCE");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EnterpriseHumanReviewService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ReviewItem submit(String requestId, String question, String answer,
                             List<Map<String, Object>> sources, String accessRole, String strategy) {
        validateRequestId(requestId);
        String safeQuestion = requiredText(question, "question", 4_000);
        String safeAnswer = requiredText(answer, "answer", 12_000);
        String sourceJson = json(sanitizeSources(sources));
        jdbcTemplate.update("""
                INSERT INTO rag_human_reviews
                  (request_id, question, answer, sources, access_role, strategy)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)
                ON CONFLICT (request_id) DO UPDATE SET
                  question = EXCLUDED.question,
                  answer = EXCLUDED.answer,
                  sources = EXCLUDED.sources,
                  access_role = EXCLUDED.access_role,
                  strategy = EXCLUDED.strategy
                WHERE rag_human_reviews.status = 'PENDING'
                """, requestId, safeQuestion, safeAnswer, sourceJson,
                limited(accessRole, 80), limited(strategy, 80));
        return findByRequestId(requestId);
    }

    public List<ReviewItem> list(String status, int requestedLimit) {
        String normalized = status == null || status.isBlank() ? null : status.strip().toUpperCase();
        if (normalized != null && !Set.of("PENDING", "REVIEWED").contains(normalized)) {
            throw new IllegalArgumentException("status must be PENDING or REVIEWED");
        }
        int limit = Math.max(1, Math.min(200, requestedLimit));
        if (normalized == null) {
            return jdbcTemplate.query(selectSql() + " ORDER BY created_at DESC LIMIT ?", this::map, limit);
        }
        return jdbcTemplate.query(selectSql() + " WHERE status = ? ORDER BY created_at DESC LIMIT ?",
                this::map, normalized, limit);
    }

    public ReviewItem review(UUID reviewId, String verdict, String comment) {
        String normalized = verdict == null ? "" : verdict.strip().toUpperCase();
        if (!VERDICTS.contains(normalized)) {
            throw new IllegalArgumentException("verdict must be RELIABLE, UNRELIABLE or INSUFFICIENT_EVIDENCE");
        }
        int updated = jdbcTemplate.update("""
                UPDATE rag_human_reviews
                SET status = 'REVIEWED', verdict = ?, reviewer_comment = ?, reviewed_at = now()
                WHERE review_id = ?
                """, normalized, limited(redact(comment), 1_000), reviewId);
        if (updated == 0) throw new NoSuchElementException("review not found");
        return find(reviewId);
    }

    public ReviewItem get(UUID reviewId) {
        return find(reviewId);
    }

    public ReviewItem saveJudge(UUID reviewId, EnterpriseReviewJudgeService.JudgeSuggestion suggestion) {
        if (suggestion == null) throw new IllegalArgumentException("judge suggestion must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE rag_human_reviews
                SET judge_status = 'COMPLETED', judge_verdict = ?, judge_score = ?,
                    judge_reason = ?, judge_model = ?, judged_at = now()
                WHERE review_id = ?
                """, suggestion.verdict(), suggestion.score(), limited(suggestion.reason(), 2_000),
                limited(suggestion.model(), 160), reviewId);
        if (updated == 0) throw new NoSuchElementException("review not found");
        return find(reviewId);
    }

    private ReviewItem find(UUID reviewId) {
        List<ReviewItem> rows = jdbcTemplate.query(selectSql() + " WHERE review_id = ?", this::map, reviewId);
        if (rows.isEmpty()) throw new NoSuchElementException("review not found");
        return rows.getFirst();
    }

    private ReviewItem findByRequestId(String requestId) {
        List<ReviewItem> rows = jdbcTemplate.query(selectSql() + " WHERE request_id = ?", this::map, requestId);
        if (rows.isEmpty()) throw new NoSuchElementException("review not found");
        return rows.getFirst();
    }

    private String selectSql() {
        return """
                SELECT review_id, request_id, question, answer, sources::text, access_role,
                       strategy, status, verdict, reviewer_comment,
                       judge_status, judge_verdict, judge_score, judge_reason, judge_model, judged_at,
                       created_at, reviewed_at
                FROM rag_human_reviews
                """;
    }

    private ReviewItem map(java.sql.ResultSet result, int rowNumber) throws java.sql.SQLException {
        Timestamp reviewed = result.getTimestamp("reviewed_at");
        Timestamp judged = result.getTimestamp("judged_at");
        return new ReviewItem(
                result.getObject("review_id", UUID.class), result.getString("request_id"),
                result.getString("question"), result.getString("answer"),
                parseSources(result.getString("sources")), result.getString("access_role"),
                result.getString("strategy"), result.getString("status"), result.getString("verdict"),
                result.getString("reviewer_comment"), result.getString("judge_status"),
                result.getString("judge_verdict"), result.getObject("judge_score", Double.class),
                result.getString("judge_reason"), result.getString("judge_model"),
                judged == null ? null : judged.toInstant(), result.getTimestamp("created_at").toInstant(),
                reviewed == null ? null : reviewed.toInstant());
    }

    private List<Map<String, Object>> sanitizeSources(List<Map<String, Object>> sources) {
        if (sources == null) return List.of();
        return sources.stream().limit(10).map(source -> {
            Map<String, Object> safe = new LinkedHashMap<>();
            copy(safe, source, "rank", 20);
            copy(safe, source, "title", 300);
            copy(safe, source, "document_id", 160);
            copy(safe, source, "chunk_id", 160);
            copy(safe, source, "chunk", 1_000);
            return safe;
        }).toList();
    }

    private void copy(Map<String, Object> target, Map<String, Object> source, String key, int limit) {
        if (source == null || source.get(key) == null) return;
        target.put(key, limited(redact(String.valueOf(source.get(key))), limit));
    }

    private String requiredText(String value, String field, int limit) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return limited(redact(value.strip()), limit);
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._-]{8,100}")) {
            throw new IllegalArgumentException("request_id is invalid");
        }
    }

    private String redact(String value) {
        if (value == null) return null;
        return value.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[REDACTED_EMAIL]")
                .replaceAll("(?i)(api[_-]?key|token|password)\\s*[:=]\\s*\\S+", "$1=[REDACTED]");
    }

    private String limited(String value, int limit) {
        if (value == null || value.isBlank()) return null;
        String stripped = value.strip();
        return stripped.length() <= limit ? stripped : stripped.substring(0, limit);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("sources are not serializable", error);
        }
    }

    private List<Map<String, Object>> parseSources(String value) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<>() {});
        } catch (JsonProcessingException error) {
            return List.of();
        }
    }

    public record ReviewItem(UUID reviewId, String requestId, String question, String answer,
                             List<Map<String, Object>> sources, String accessRole, String strategy,
                             String status, String verdict, String reviewerComment,
                             String judgeStatus, String judgeVerdict, Double judgeScore,
                             String judgeReason, String judgeModel, Instant judgedAt,
                             Instant createdAt, Instant reviewedAt) {}
}
