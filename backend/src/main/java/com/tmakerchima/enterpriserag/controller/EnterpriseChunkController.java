package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.model.EnterpriseAccessContext;
import com.tmakerchima.enterpriserag.model.EnterpriseChunkView;
import com.tmakerchima.enterpriserag.service.EnterpriseChunkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only API for inspecting the citable chunks in the ACTIVE corpus.
 *
 * <p>The demo accepts role and tenant as request parameters so the ACL behavior
 * is easy to explore. A production deployment must replace them with claims
 * from its authenticated identity provider.</p>
 */
@RestController
@RequestMapping("/api/enterprise/chunks")
public class EnterpriseChunkController {

    private final EnterpriseChunkService chunkService;

    public EnterpriseChunkController(EnterpriseChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @GetMapping
    public EnterpriseChunkService.ChunkPage list(
            @RequestParam(defaultValue = "public") String role,
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return chunkService.list(EnterpriseAccessContext.from(role, tenantId), q, page, size);
    }

    @GetMapping("/{chunkId}")
    public ResponseEntity<EnterpriseChunkView> find(
            @PathVariable String chunkId,
            @RequestParam(defaultValue = "public") String role,
            @RequestParam(defaultValue = "default") String tenantId) {
        return chunkService.find(chunkId, EnterpriseAccessContext.from(role, tenantId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
