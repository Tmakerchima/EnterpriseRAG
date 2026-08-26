package com.tmakerchima.enterpriserag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmakerchima.enterpriserag.api.SseEvent;
import com.tmakerchima.enterpriserag.service.EnterpriseChatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnterpriseChatControllerTest {

    @Test
    void writesOneSseFrameWithoutEncodingTheProtocolAsData() {
        EnterpriseChatService service = mock(EnterpriseChatService.class);
        when(service.streamAnswer(any(), isNull(), isNull())).thenReturn(Flux.just(
                SseEvent.encode(new ObjectMapper(), "token", "request-1", "trace-1", Map.of("text", "hello"))));

        WebTestClient.bindToController(new EnterpriseChatController(service)).build()
                .post()
                .uri("/api/enterprise/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(Map.of("question", "hello", "role", "public", "strategy", "HYBRID"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("event:token")
                        .contains("data:{\"schema_version\":\"enterprise-rag.sse.v1\"")
                        .doesNotContain("data:event:"));
    }
}
