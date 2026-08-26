package com.tmakerchima.enterpriserag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnterpriseReviewJudgeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesStrictJudgeJsonAndMarkdownFence() {
        EnterpriseReviewJudgeService.JudgeSuggestion suggestion = EnterpriseReviewJudgeService.parse(
                "```json\n{\"verdict\":\"reliable\",\"score\":0.91,\"reason\":\"证据支持回答\"}\n```",
                "judge-model", objectMapper);

        assertThat(suggestion.verdict()).isEqualTo("RELIABLE");
        assertThat(suggestion.score()).isEqualTo(0.91);
        assertThat(suggestion.reason()).isEqualTo("证据支持回答");
        assertThat(suggestion.model()).isEqualTo("judge-model");
    }

    @Test
    void rejectsOutOfRangeOrUnknownJudgeResults() {
        assertThatThrownBy(() -> EnterpriseReviewJudgeService.parse(
                "{\"verdict\":\"MAYBE\",\"score\":2,\"reason\":\"x\"}", "m", objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
