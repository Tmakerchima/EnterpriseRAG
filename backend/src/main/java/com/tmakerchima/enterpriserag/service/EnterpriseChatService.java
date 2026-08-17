package com.tmakerchima.enterpriserag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmakerchima.enterpriserag.api.SseEvent;
import com.tmakerchima.enterpriserag.controller.EnterpriseChatController.ChatRequest;
import com.tmakerchima.enterpriserag.model.EnterpriseAccessContext;
import com.tmakerchima.enterpriserag.model.EnterpriseRetrievalStrategy;
import com.tmakerchima.enterpriserag.model.EnterpriseSearchHit;
import com.tmakerchima.enterpriserag.retrieval.EnterpriseRetrievalMetrics;
import com.tmakerchima.enterpriserag.retrieval.EnterpriseRetrievalResult;
import com.tmakerchima.enterpriserag.retrieval.EnterpriseRetrievalService;
import com.tmakerchima.enterpriserag.observability.EnterpriseInteractionService;
import com.tmakerchima.enterpriserag.observability.EnterpriseTelemetry;
import io.micrometer.observation.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EnterpriseChatService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseChatService.class);
    private static final String SYSTEM_PROMPT = """
            You are Enterprise Knowledge Assistant. Answer only from the retrieved enterprise context.
            If the context does not contain enough evidence, say that there is insufficient evidence and
            explain what is missing. Never invent company facts, permissions, people, dates, or metrics.
            Keep answers concise and cite the relevant source title or document when possible.
            """;

    private final ChatClient chatClient;
    private final EnterpriseRetrievalService retrievalService;
    private final ObjectMapper objectMapper;
    private final EnterpriseRetrievalStrategy defaultStrategy;
    private final boolean enabled;
    private final EnterpriseTelemetry telemetry;
    private final EnterpriseInteractionService interactionService;

    public EnterpriseChatService(ChatClient chatClient,
                                 EnterpriseRetrievalService retrievalService,
                                 ObjectMapper objectMapper,
                                 @Value("${enterprise.rag.strategy:HYBRID}") String defaultStrategy,
                                 @Value("${enterprise.rag.enabled:true}") boolean enabled,
                                 EnterpriseTelemetry telemetry,
                                 EnterpriseInteractionService interactionService) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
        this.defaultStrategy = EnterpriseRetrievalStrategy.parse(defaultStrategy, EnterpriseRetrievalStrategy.HYBRID);
        this.enabled = enabled;
        this.telemetry = telemetry;
        this.interactionService = interactionService;
    }

    /**
     * 处理一次 EnterpriseRAG 对话请求，并以 Server-Sent Events（SSE）返回结果。
     *
     * <p>该方法只负责在线问答编排，不负责文档入库。入库由 Python worker 完成；
     * 这里读取已经处于 ACTIVE 状态的 corpus，执行权限约束下的检索，再把有限的
     * 原始证据交给 Chat LLM。返回流的顺序固定为：来源帧、答案文本、指标帧。</p>
     *
     * @param request 前端提交的问题、角色、租户和检索策略
     * @return SSE 字符串流；错误也会被编码为 {@code @@ERROR@@} 帧
     */
    public Flux<String> streamAnswer(ChatRequest request, String requestedRequestId, String requestedTraceId) {
        return Flux.defer(() -> {
            // defer 使每次订阅都重新创建 requestId、计时器和检索结果，避免多个订阅共享状态。
            String requestId = validId(requestedRequestId);
            String traceId = validId(requestedTraceId);
            long started = System.nanoTime();

            // enabled 是服务级总开关；关闭时不访问数据库、Embedding 或 Chat LLM。
            if (!enabled) return Flux.just(errorFrame(requestId, "EnterpriseRAG is disabled"));
            if (request == null) return Flux.just(errorFrame(requestId, "request must not be null"));

            // 在调用任何外部模型前拒绝空问题，避免无意义的向量请求和生成请求。
            String question = request.question() == null ? "" : request.question().trim();
            if (question.isBlank()) return Flux.just(errorFrame(requestId, "question must not be blank"));

            // 将前端角色转换为服务端权限上下文；后续每一路检索都必须复用同一 access。
            EnterpriseAccessContext access = EnterpriseAccessContext.from(
                    request.role(), request.tenantId());

            // 非法或缺失策略会回落到配置中的默认值，防止客户端传入未知模式破坏检索链路。
            EnterpriseRetrievalStrategy strategy = EnterpriseRetrievalStrategy.parse(request.strategy(), defaultStrategy);

            // 唯一的在线检索路径：每一路 SQL 都复用同一个 tenant/ACL context，
            // 不让 query rewrite 或 agent 产生第二套权限和融合控制流。
            EnterpriseRetrievalResult retrieval = retrievalService.retrieve(question, access, strategy);
            Observation observation = telemetry.startRequest(strategy.name(), retrieval.metrics().lexicalBackend());

            // 来源帧先于答案发送，使前端可以在模型仍生成时展示可追溯证据。
            // groundedPrompt 只拼接最终可访问的原始 content，不把 contextual_prefix 当作引用证据。
            String groundedPrompt = groundedPrompt(question, access, retrieval.hits());

            // 没有授权证据时直接拒答，避免把“请依据空 context 回答”交给模型后仍产生幻觉。
            Flux<String> answer = retrieval.hits().isEmpty()
                    ? Flux.just(tokenFrame(requestId, traceId,
                            "Insufficient evidence in the authorized corpus to answer this question."))
                    // 有证据时才调用 ChatClient；模型只能看到已完成 ACL 与 context budget 的原文。
                    : chatClient.prompt()
                            .system(SYSTEM_PROMPT)
                            .user(groundedPrompt)
                            .stream()
                            .content()
                            .map(token -> SseEvent.encode(objectMapper, "token", requestId, traceId,
                                    Map.of("text", token)))
                            .onErrorResume(error -> Flux.just(errorFrame(requestId, traceId, "LLM request failed")));

            // SSE 输出顺序：@@SOURCES@@ → 多个答案片段 → @@METRICS@@。
            return Flux.concat(Mono.just(sourcesFrame(requestId, traceId, retrieval)), answer,
                            Mono.fromSupplier(() -> metricsFrame(requestId, traceId, retrieval, started)),
                            Mono.just(doneFrame(requestId, traceId)))
                    .doOnComplete(() -> log.info(
                            "enterprise_request request_id={} strategy={} lexical_backend={} vector_latency={}ms " +
                                    "bm25_or_fts_latency={}ms rerank_latency={}ms candidate_count={} " +
                            "context_count={} query_count={} fallback={} total_latency={}ms",
                            requestId, strategy, retrieval.metrics().lexicalBackend(), retrieval.metrics().vectorMs(),
                            retrieval.metrics().ftsMs(), retrieval.metrics().rerankMs(), retrieval.metrics().candidateCount(),
                            retrieval.metrics().finalContextCount(), retrieval.metrics().queryCount(),
                            retrieval.metrics().fallback(), elapsedMs(started)))
                    .doFinally(signal -> {
                        observation.stop();
                        String outcome = signal.name().equals("ON_COMPLETE") ? "success" : "cancelled";
                        telemetry.recordRequest(strategy.name(), retrieval.metrics().lexicalBackend(), outcome, elapsedMs(started));
                        interactionService.record(requestId, traceId, access, strategy.name(), outcome, retrieval);
                    });
        // 兜底处理检索、数据库或序列化阶段未被内部捕获的异常。
        }).onErrorResume(error -> Flux.just(errorFrame(validId(requestedRequestId), "Enterprise request failed")))
                // JDBC 和兼容模式模型调用可能阻塞，放入 boundedElastic 避免占用 WebFlux 网络线程。
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String groundedPrompt(String question, EnterpriseAccessContext access, List<EnterpriseSearchHit> hits) {
        String context = hits.isEmpty() ? "INSUFFICIENT EVIDENCE: no authorized documents were retrieved."
                : hits.stream().map(hit -> "[" + hit.sourceType() + " | " + hit.title() + " | " + hit.chunkId() + "]\n"
                        + hit.content()).reduce((left, right) -> left + "\n\n" + right).orElse("");
        return """
                Authorization role: %s
                Retrieved context:
                <context>
                %s
                </context>

                User question: %s
                """.formatted(access.role(), context, question);
    }

    private String sourcesFrame(String requestId, String traceId, EnterpriseRetrievalResult retrieval) {
        List<Map<String, Object>> sources = retrieval.hits().stream().map(hit -> {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("source_type", hit.sourceType());
            source.put("source", hit.source());
            source.put("title", hit.title());
            // Keep the existing external document_id contract and add the
            // internal key for audit/debugging without changing clients.
            source.put("document_id", hit.externalId());
            source.put("external_id", hit.externalId());
            source.put("internal_document_id", hit.documentId());
            source.put("chunk_id", hit.chunkId());
            source.put("chunk", snippet(hit.content()));
            source.put("metadata", hit.metadata());
            source.put("rank", hit.rank());
            source.put("score", round(hit.score()));
            return source;
        }).toList();
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("request_id", requestId);
        frame.put("strategy", retrieval.strategy().name());
        frame.put("sources", sources);
        return SseEvent.encode(objectMapper, "sources", requestId, traceId, frame);
    }

    private String metricsFrame(String requestId, String traceId, EnterpriseRetrievalResult retrieval, long started) {
        EnterpriseRetrievalMetrics values = retrieval.metrics();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("request_id", requestId);
        metrics.put("strategy", retrieval.strategy().name());
        metrics.put("vector_ms", values.vectorMs());
        metrics.put("fts_ms", values.ftsMs());
        metrics.put("lexical_ms", values.ftsMs());
        metrics.put("bm25_query_latency_ms", values.lexicalBackend().contains("BM25") ? values.ftsMs() : 0);
        metrics.put("lexical_backend", values.lexicalBackend());
        metrics.put("bm25_fallback", values.fallback() != null && values.fallback().contains("BM25"));
        metrics.put("rrf_ms", values.rrfMs());
        metrics.put("rerank_ms", values.rerankMs());
        metrics.put("llm_ms", Math.max(0, elapsedMs(started) - values.vectorMs() - values.ftsMs() - values.rrfMs() - values.rerankMs()));
        metrics.put("total_ms", elapsedMs(started));
        metrics.put("candidate_count", values.candidateCount());
        metrics.put("candidate_document_ids", retrieval.candidateDocumentIds());
        metrics.put("candidate_chunk_ids", retrieval.candidateChunkIds());
        metrics.put("final_context_count", values.finalContextCount());
        metrics.put("context_token_count", values.contextTokenCount());
        metrics.put("unique_document_count", values.uniqueDocumentCount());
        metrics.put("query_count", values.queryCount());
        metrics.put("fallback", values.fallback());
        return SseEvent.encode(objectMapper, "metrics", requestId, traceId, metrics);
    }

    private String errorFrame(String requestId, String message) {
        return errorFrame(requestId, validId(null), message);
    }

    private String errorFrame(String requestId, String traceId, String message) {
        return SseEvent.encode(objectMapper, "error", requestId, traceId, Map.of("request_id", requestId, "message", message));
    }

    private String doneFrame(String requestId, String traceId) {
        return SseEvent.encode(objectMapper, "done", requestId, traceId, Map.of("status", "complete"));
    }

    private String tokenFrame(String requestId, String traceId, String text) {
        return SseEvent.encode(objectMapper, "token", requestId, traceId, Map.of("text", text));
    }

    private String snippet(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 180 ? oneLine.substring(0, 180) + "…" : oneLine;
    }

    private double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String validId(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{8,100}") ? value : UUID.randomUUID().toString();
    }
}
