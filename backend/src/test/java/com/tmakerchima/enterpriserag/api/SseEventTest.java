package com.tmakerchima.enterpriserag.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventTest {

    @Test
    void encodesVersionedEventAndStableCorrelationIds() throws Exception {
        String encoded = SseEvent.encode(new ObjectMapper(), "sources", "request-1", "trace-1",
                Map.of("sources", java.util.List.of(Map.of("document_id", "doc-1"))));

        assertThat(encoded).startsWith("event: sources\ndata: ");
        String json = encoded.substring(encoded.indexOf("data: ") + 6).trim();
        Map<?, ?> envelope = new ObjectMapper().readValue(json, Map.class);
        assertThat(envelope.get("schema_version")).isEqualTo(SseEvent.SCHEMA_VERSION);
        assertThat(envelope.get("type")).isEqualTo("sources");
        assertThat(envelope.get("request_id")).isEqualTo("request-1");
        assertThat(envelope.get("trace_id")).isEqualTo("trace-1");
    }
}
