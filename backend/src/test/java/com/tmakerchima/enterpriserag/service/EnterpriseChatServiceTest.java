package com.tmakerchima.enterpriserag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmakerchima.enterpriserag.controller.EnterpriseChatController.ChatRequest;
import com.tmakerchima.enterpriserag.observability.EnterpriseInteractionService;
import com.tmakerchima.enterpriserag.observability.EnterpriseTelemetry;
import com.tmakerchima.enterpriserag.retrieval.EnterpriseRetrievalMetrics;
import com.tmakerchima.enterpriserag.retrieval.EnterpriseRetrievalResult;
import com.tmakerchima.enterpriserag.retrieval.EnterpriseRetrievalService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnterpriseChatServiceTest {

    @Test
    void emptyAuthorizedContextAbstainsWithoutCallingTheModel() {
        ChatClient chatClient = mock(ChatClient.class);
        EnterpriseRetrievalService retrievalService = mock(EnterpriseRetrievalService.class);
        EnterpriseInteractionService interactionService = mock(EnterpriseInteractionService.class);
        EnterpriseRetrievalResult empty = new EnterpriseRetrievalResult(List.of(),
                com.tmakerchima.enterpriserag.model.EnterpriseRetrievalStrategy.HYBRID,
                new EnterpriseRetrievalMetrics(0, 0, 0, 0, 0, 0));
        when(retrievalService.retrieve(any(), any(), any())).thenReturn(empty);
        EnterpriseChatService service = new EnterpriseChatService(chatClient, retrievalService,
                new ObjectMapper(), "HYBRID", true,
                new EnterpriseTelemetry(new SimpleMeterRegistry(), ObservationRegistry.create()),
                interactionService);

        String stream = service.streamAnswer(
                new ChatRequest("What is missing?", "public", "tenant-a", "HYBRID"),
                "request-123", "trace-123").collectList().block().stream()
                .map(ServerSentEvent::data)
                .collect(Collectors.joining());

        assertThat(stream).contains("Insufficient evidence", "\"type\":\"sources\"", "\"type\":\"done\"");
        verify(retrievalService).retrieve(any(), any(), any());
        verifyNoInteractions(chatClient);
    }

    @Test
    void oversizedQuestionIsRejectedBeforeRetrievalOrModelCalls() {
        ChatClient chatClient = mock(ChatClient.class);
        EnterpriseRetrievalService retrievalService = mock(EnterpriseRetrievalService.class);
        EnterpriseInteractionService interactionService = mock(EnterpriseInteractionService.class);
        EnterpriseChatService service = new EnterpriseChatService(chatClient, retrievalService,
                new ObjectMapper(), "HYBRID", true,
                new EnterpriseTelemetry(new SimpleMeterRegistry(), ObservationRegistry.create()),
                interactionService);

        String stream = service.streamAnswer(
                new ChatRequest("x".repeat(4_001), "public", "tenant-a", "HYBRID"),
                "request-123", "trace-123").collectList().block().stream()
                .map(ServerSentEvent::data)
                .collect(Collectors.joining());

        assertThat(stream).contains("question must not exceed 4000 characters");
        verifyNoInteractions(retrievalService, chatClient, interactionService);
    }
}
