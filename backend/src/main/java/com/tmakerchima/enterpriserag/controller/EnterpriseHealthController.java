package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.service.EnterpriseCorpusService;
import com.tmakerchima.enterpriserag.retrieval.EnterpriseLexicalHealth;
import com.tmakerchima.enterpriserag.retrieval.EnterpriseLexicalRetriever;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/enterprise")
public class EnterpriseHealthController {

    private final EnterpriseCorpusService corpusService;
    private final EnterpriseLexicalRetriever lexicalRetriever;

    public EnterpriseHealthController(EnterpriseCorpusService corpusService,
                                      EnterpriseLexicalRetriever lexicalRetriever) {
        this.corpusService = corpusService;
        this.lexicalRetriever = lexicalRetriever;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        addLexicalHealth(body);
        try {
            body.putAll(corpusService.stats());
            body.put("database", "UP");
            body.put("vector", "UP");
            body.put("documents", body.getOrDefault("document_count", 0L));
            body.put("chunks", body.getOrDefault("chunk_count", 0L));
            body.putIfAbsent("message", "Enterprise corpus status is available");
            return ResponseEntity.ok(body);
        } catch (DataAccessException error) {
            body.put("status", "MIGRATION_REQUIRED");
            body.put("database", "DOWN");
            body.put("vector", "UNKNOWN");
            body.put("documents", 0);
            body.put("chunks", 0);
            body.put("message", "Apply V1__enterprise_rag.sql and V2__enterprise_rag_generations.sql before reading the enterprise corpus");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }

    private void addLexicalHealth(Map<String, Object> body) {
        // 健康接口只暴露 UP/DOWN 和后端名字，不返回 BM25 JDBC URL 或密码。
        EnterpriseLexicalHealth health = lexicalRetriever.health();
        body.put("lexicalBackend", health.activeBackend());
        body.put("configuredLexicalBackend", health.configuredBackend());
        body.put("lexical_backend", health.activeBackend());
        body.put("bm25", "PARADEDB_BM25".equals(health.configuredBackend())
                ? (health.healthy() ? "UP" : "DOWN") : "NOT_CONFIGURED");
        if (health.reason() != null && !health.reason().isBlank()) body.put("lexicalReason", health.reason());
    }
}
