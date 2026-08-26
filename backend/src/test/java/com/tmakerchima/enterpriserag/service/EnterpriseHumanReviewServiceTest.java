package com.tmakerchima.enterpriserag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EnterpriseHumanReviewServiceTest {

    @Test
    void rejectsInvalidOrEmptyReviewPayloadBeforePersistence() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EnterpriseHumanReviewService service = new EnterpriseHumanReviewService(jdbcTemplate, new ObjectMapper());

        assertThatThrownBy(() -> service.submit("short", "question", "answer", null, "public", "HYBRID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request_id");
        assertThatThrownBy(() -> service.submit("request-123", " ", "answer", null, "public", "HYBRID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question");
        assertThatThrownBy(() -> service.review(java.util.UUID.randomUUID(), "maybe", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verdict");

        verifyNoInteractions(jdbcTemplate);
    }
}
