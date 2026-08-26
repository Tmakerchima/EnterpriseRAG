package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.service.EnterpriseHumanReviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/enterprise/reviews")
public class EnterpriseHumanReviewController {

    private final EnterpriseHumanReviewService reviewService;
    private final String adminToken;

    public EnterpriseHumanReviewController(EnterpriseHumanReviewService reviewService,
                                           @Value("${enterprise.rag.admin-token:}") String adminToken) {
        this.reviewService = reviewService;
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    public record SubmitReviewRequest(String requestId, String question, String answer,
                                      List<Map<String, Object>> sources, String accessRole, String strategy) {}
    public record CompleteReviewRequest(String verdict, String comment) {}

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody SubmitReviewRequest request) {
        if (request == null) return ResponseEntity.badRequest().body(Map.of("error", "request must not be null"));
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.submit(
                    request.requestId(), request.question(), request.answer(), request.sources(),
                    request.accessRole(), request.strategy()));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String token,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
        try {
            return ResponseEntity.ok(Map.of("items", reviewService.list(status, limit)));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
        }
    }

    @PatchMapping("/{reviewId}")
    public ResponseEntity<?> complete(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String token,
            @PathVariable UUID reviewId,
            @RequestBody CompleteReviewRequest request) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
        if (request == null) return ResponseEntity.badRequest().body(Map.of("error", "request must not be null"));
        try {
            return ResponseEntity.ok(reviewService.review(reviewId, request.verdict(), request.comment()));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", error.getMessage()));
        }
    }

    private boolean authorized(String token) {
        return !adminToken.isBlank() && token != null && adminToken.equals(token);
    }
}
