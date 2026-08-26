package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.service.EnterpriseHumanReviewService;
import com.tmakerchima.enterpriserag.service.EnterpriseReviewJudgeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final EnterpriseReviewJudgeService judgeService;

    public EnterpriseHumanReviewController(EnterpriseHumanReviewService reviewService,
                                           EnterpriseReviewJudgeService judgeService) {
        this.reviewService = reviewService;
        this.judgeService = judgeService;
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
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            return ResponseEntity.ok(Map.of("items", reviewService.list(status, limit)));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
        }
    }

    @PatchMapping("/{reviewId}")
    public ResponseEntity<?> complete(
            @PathVariable UUID reviewId,
            @RequestBody CompleteReviewRequest request) {
        if (request == null) return ResponseEntity.badRequest().body(Map.of("error", "request must not be null"));
        try {
            return ResponseEntity.ok(reviewService.review(reviewId, request.verdict(), request.comment()));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", error.getMessage()));
        }
    }

    @PostMapping("/{reviewId}/judge")
    public ResponseEntity<?> judge(@PathVariable UUID reviewId) {
        try {
            EnterpriseHumanReviewService.ReviewItem item = reviewService.get(reviewId);
            return ResponseEntity.ok(reviewService.saveJudge(reviewId, judgeService.judge(item)));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", error.getMessage()));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
        } catch (RuntimeException error) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "LLM judge request failed"));
        }
    }
}
