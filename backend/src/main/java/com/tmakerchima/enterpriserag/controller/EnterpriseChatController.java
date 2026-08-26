package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.service.EnterpriseChatService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/enterprise/chat")
public class EnterpriseChatController {

    private final EnterpriseChatService chatService;

    public EnterpriseChatController(EnterpriseChatService chatService) {
        this.chatService = chatService;
    }

    public record ChatRequest(String question, String role, String tenantId, String strategy) {}

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request,
                                              @RequestHeader(name = "X-Request-Id", required = false) String requestId,
                                              @RequestHeader(name = "X-Trace-Id", required = false) String traceId) {
        return chatService.streamAnswer(request, requestId, traceId);
    }
}
