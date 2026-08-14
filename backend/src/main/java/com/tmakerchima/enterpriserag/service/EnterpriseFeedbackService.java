package com.tmakerchima.enterpriserag.service;

import com.tmakerchima.enterpriserag.observability.EnterpriseTelemetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EnterpriseFeedbackService {

    private static final Set<String> RATINGS = Set.of("positive", "negative");
    private static final Set<String> REASONS = Set.of("wrong_answer", "missing_evidence", "unsafe", "acl", "latency", "other");
    private final JdbcTemplate jdbcTemplate;
    private final EnterpriseTelemetry telemetry;
    private final boolean enabled;
    private final double sampleRate;

    public EnterpriseFeedbackService(JdbcTemplate jdbcTemplate, EnterpriseTelemetry telemetry,
                                     @Value("${enterprise.feedback.enabled:true}") boolean enabled,
                                     @Value("${enterprise.feedback.async-sample-rate:0.02}") double sampleRate) {
        this.jdbcTemplate = jdbcTemplate;
        this.telemetry = telemetry;
        this.enabled = enabled;
        this.sampleRate = Math.max(0, Math.min(1, sampleRate));
    }

    public FeedbackResult record(String requestId, String rating, String reason, String comment) {
        if (!enabled) return new FeedbackResult("DISABLED", false);
        if (requestId == null || !requestId.matches("[A-Za-z0-9._-]{8,100}")) {
            throw new IllegalArgumentException("request_id is invalid");
        }
        if (!RATINGS.contains(rating)) throw new IllegalArgumentException("rating must be positive or negative");
        if (reason != null && !REASONS.contains(reason)) throw new IllegalArgumentException("reason is invalid");
        String redacted = redact(comment);
        jdbcTemplate.update("INSERT INTO rag_feedback (request_id, rating, reason, redacted_comment) VALUES (?, ?, ?, ?)",
                requestId, rating, reason, redacted);
        telemetry.recordFeedback(rating);
        boolean queued = "negative".equals(rating) || ThreadLocalRandom.current().nextDouble() < sampleRate;
        if (queued) {
            jdbcTemplate.update("INSERT INTO rag_evaluation_queue (request_id, sampling_reason) VALUES (?, ?)",
                    requestId, "negative".equals(rating) ? "negative_feedback" : "async_sample");
        }
        return new FeedbackResult("ACCEPTED", queued);
    }

    private String redact(String comment) {
        if (comment == null || comment.isBlank()) return null;
        String value = comment.strip().replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[REDACTED_EMAIL]")
                .replaceAll("(?i)(api[_-]?key|token|password)\\s*[:=]\\s*\\S+", "$1=[REDACTED]");
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    public record FeedbackResult(String status, boolean queued) {}
}
