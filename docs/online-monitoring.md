# Online monitoring

Spring Boot Actuator, Micrometer and Prometheus expose low-cardinality metrics. Request ID, trace ID, question, tenant and document IDs stay in controlled logs/traces/events, never metric tags. The request Observation spans retrieval and generation; feedback and interaction persistence are additive/asynchronous.

Suggested provisional Prometheus panels:

```promql
sum(rate(enterprise_rag_requests_total[5m])) by (outcome)
histogram_quantile(0.95, sum(rate(enterprise_rag_request_duration_seconds_bucket[5m])) by (le, strategy))
sum(rate(enterprise_rag_feedback_total{rating="negative"}[15m]))
```

Alert on error/timeout rate, p95 latency, TTFT, empty retrieval, backend mismatch/fallback, negative feedback, evaluation queue age/failure, ingestion freshness and provider/database availability. These are operational signals; correctness requires offline gold or reviewed feedback.
