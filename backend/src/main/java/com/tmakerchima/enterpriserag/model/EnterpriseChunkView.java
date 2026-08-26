package com.tmakerchima.enterpriserag.model;

import java.util.Map;

/**
 * A citable chunk that is safe to show to the current tenant/role.
 *
 * <p>Retrieval-only fields such as embeddings and contextual prefixes are
 * deliberately excluded. The {@code content} field is the original source
 * text that may be cited in an answer.</p>
 */
public record EnterpriseChunkView(
        String chunkId,
        String documentId,
        String externalId,
        String source,
        String sourceType,
        String title,
        String content,
        int chunkIndex,
        int tokenCount,
        boolean embedded,
        String department,
        String accessLevel,
        String corpusVersion,
        Map<String, Object> metadata) {
}
