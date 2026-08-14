package com.tmakerchima.enterpriserag.service;

import com.tmakerchima.enterpriserag.observability.EnterpriseTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EnterpriseFeedbackServiceTest {

    @Test
    void validatesEnumsAndRedactsCommentBeforePersistence() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EnterpriseFeedbackService service = new EnterpriseFeedbackService(jdbcTemplate,
                new EnterpriseTelemetry(new SimpleMeterRegistry(), ObservationRegistry.create()), true, 0);

        service.record("request-123", "negative", "wrong_answer", "Email me at user@example.com token=super-secret");

        verify(jdbcTemplate).update(eq("INSERT INTO rag_feedback (request_id, rating, reason, redacted_comment) VALUES (?, ?, ?, ?)"),
                eq("request-123"), eq("negative"), eq("wrong_answer"), eq("Email me at [REDACTED_EMAIL] token=[REDACTED]"));
        verify(jdbcTemplate).update(eq("INSERT INTO rag_evaluation_queue (request_id, sampling_reason) VALUES (?, ?)"),
                eq("request-123"), eq("negative_feedback"));
        assertThatThrownBy(() -> service.record("request-123", "other", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
