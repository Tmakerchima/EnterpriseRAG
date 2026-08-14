package com.tmakerchima.enterpriserag.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned SSE envelope. Legacy marker payloads remain parser-compatible through the event type. */
public record SseEvent(String schemaVersion, String type, String requestId, String traceId, Object payload) {

    public static final String SCHEMA_VERSION = "enterprise-rag.sse.v1";

    public static String encode(ObjectMapper objectMapper, String type, String requestId, String traceId, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schema_version", SCHEMA_VERSION);
        envelope.put("type", type);
        envelope.put("request_id", requestId);
        envelope.put("trace_id", traceId);
        envelope.put("payload", payload);
        try {
            return "event: " + type + "\ndata: " + objectMapper.writeValueAsString(envelope) + "\n\n";
        } catch (JsonProcessingException error) {
            return "event: error\ndata: {\"schema_version\":\"" + SCHEMA_VERSION
                    + "\",\"type\":\"error\",\"request_id\":\"" + requestId
                    + "\",\"trace_id\":\"" + traceId + "\",\"payload\":{\"message\":\"serialization failed\"}}\n\n";
        }
    }
}
