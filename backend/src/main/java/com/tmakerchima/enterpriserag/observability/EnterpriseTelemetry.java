package com.tmakerchima.enterpriserag.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/** Low-cardinality metrics and Observation spans for the online RAG path. */
@Component
public class EnterpriseTelemetry {

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public EnterpriseTelemetry(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    public Observation startRequest(String strategy, String backend) {
        return Observation.createNotStarted("enterprise.rag.request", observationRegistry)
                .lowCardinalityKeyValue("strategy", bounded(strategy, "unknown"))
                .lowCardinalityKeyValue("lexical_backend", bounded(backend, "unknown"))
                .start();
    }

    public void recordRequest(String strategy, String backend, String outcome, long elapsedMs) {
        String safeStrategy = bounded(strategy, "unknown");
        String safeBackend = bounded(backend, "unknown");
        String safeOutcome = bounded(outcome, "unknown");
        meterRegistry.counter("enterprise_rag_requests_total", "strategy", safeStrategy,
                "lexical_backend", safeBackend, "outcome", safeOutcome).increment();
        Timer.builder("enterprise_rag_request_duration")
                .description("EnterpriseRAG end-to-end request latency")
                .tags("strategy", safeStrategy, "lexical_backend", safeBackend, "outcome", safeOutcome)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0, elapsedMs)));
    }

    public void recordFeedback(String rating) {
        meterRegistry.counter("enterprise_rag_feedback_total", "rating", bounded(rating, "unknown")).increment();
    }

    private String bounded(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > 40 ? fallback : normalized;
    }
}
