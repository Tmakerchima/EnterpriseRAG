package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.service.EnterpriseFeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/enterprise/feedback")
public class EnterpriseFeedbackController {

    private final EnterpriseFeedbackService feedbackService;

    public EnterpriseFeedbackController(EnterpriseFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    public record FeedbackRequest(String requestId, String rating, String reason, String comment) {}

    @PostMapping
    public ResponseEntity<?> feedback(@RequestBody FeedbackRequest request) {
        if (request == null) return ResponseEntity.badRequest().body(Map.of("error", "request must not be null"));
        try {
            EnterpriseFeedbackService.FeedbackResult result = feedbackService.record(
                    request.requestId(), request.rating(), request.reason(), request.comment());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", result.status(), "queued", result.queued()));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
        }
    }
}
