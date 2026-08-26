package com.tmakerchima.enterpriserag.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds one standards-compliant, versioned Server-Sent Event. */
public record SseEvent(String schemaVersion, String type, String requestId, String traceId, Object payload) {

    public static final String SCHEMA_VERSION = "enterprise-rag.sse.v1";

    public static ServerSentEvent<String> encode(ObjectMapper objectMapper, String type, String requestId,
                                                  String traceId, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schema_version", SCHEMA_VERSION);
        envelope.put("type", type);
        envelope.put("request_id", requestId);
        envelope.put("trace_id", traceId);
        envelope.put("payload", payload);
        try {
            return ServerSentEvent.<String>builder()
                    .event(type)
                    .data(objectMapper.writeValueAsString(envelope))
                    .build();
        } catch (JsonProcessingException error) {
            String fallback = "{\"schema_version\":\"" + SCHEMA_VERSION
                    + "\",\"type\":\"error\",\"request_id\":\"" + requestId
                    + "\",\"trace_id\":\"" + traceId + "\",\"payload\":{\"message\":\"serialization failed\"}}";
            return ServerSentEvent.<String>builder().event("error").data(fallback).build();
        }
    }
}
