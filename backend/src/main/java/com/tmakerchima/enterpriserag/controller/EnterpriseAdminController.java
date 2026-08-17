package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.service.EnterpriseCorpusService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Enterprise RAG 的管理接口入口。
 *
 * <p>文档由 canonical Python worker 导入；这里仅管理 corpus 的创建和生命周期。</p>
 */
@RestController
@RequestMapping("/api/enterprise/admin")
public class EnterpriseAdminController {

    private final EnterpriseCorpusService corpusService;
    /** 服务端保存的管理共享密钥；为空时表示主动关闭所有管理接口。 */
    private final String adminToken;

    public EnterpriseAdminController(EnterpriseCorpusService corpusService,
                                     @Value("${enterprise.rag.admin-token:}") String adminToken) {
        this.corpusService = corpusService;
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    public record CorpusRequest(String datasetName, String datasetVersion, Long expectedDocuments,
                                 String embeddingProvider, String embeddingModel, Integer dimension,
                                 String chunkerVersion) {}

    @PostMapping("/corpora")
    public ResponseEntity<?> createCorpus(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String requestToken,
            @RequestBody CorpusRequest request) {
        if (!authorized(requestToken)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized");
        if (request == null || request.datasetName() == null || request.datasetVersion() == null) {
            return ResponseEntity.badRequest().body("datasetName and datasetVersion are required");
        }
        UUID corpusId = corpusService.create(request.datasetName(), request.datasetVersion(),
                request.expectedDocuments() == null ? 0 : request.expectedDocuments(),
                request.embeddingProvider(), request.embeddingModel(),
                request.dimension() == null ? 1024 : request.dimension(), request.chunkerVersion());
        return ResponseEntity.ok(Map.of("corpus_id", corpusId, "state", "STAGING"));
    }

    @PostMapping("/corpora/{corpusId}/activate")
    public ResponseEntity<?> activate(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String requestToken,
            @PathVariable UUID corpusId) {
        if (!authorized(requestToken)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized");
        corpusService.activate(corpusId);
        return ResponseEntity.ok(Map.of("corpus_id", corpusId, "state", "ACTIVE"));
    }

    @PostMapping("/corpora/{corpusId}/rollback")
    public ResponseEntity<?> rollback(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String requestToken,
            @PathVariable UUID corpusId) {
        if (!authorized(requestToken)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized");
        corpusService.rollback(corpusId);
        return ResponseEntity.ok(Map.of("corpus_id", corpusId, "state", "ACTIVE"));
    }

    private boolean authorized(String requestToken) {
        return !adminToken.isBlank() && requestToken != null && adminToken.equals(requestToken);
    }
}
