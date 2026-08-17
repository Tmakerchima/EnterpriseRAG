package com.tmakerchima.enterpriserag.retrieval;

import com.tmakerchima.enterpriserag.model.EnterpriseRetrievalStrategy;
import com.tmakerchima.enterpriserag.model.EnterpriseSearchHit;

import java.util.List;

public record EnterpriseRetrievalResult(
        List<EnterpriseSearchHit> hits,
        EnterpriseRetrievalStrategy strategy,
        EnterpriseRetrievalMetrics metrics,
        List<String> candidateDocumentIds,
        List<String> candidateChunkIds) {

    /** Backward-compatible constructor for tests and internal callers that only have final hits. */
    public EnterpriseRetrievalResult(List<EnterpriseSearchHit> hits,
                                     EnterpriseRetrievalStrategy strategy,
                                     EnterpriseRetrievalMetrics metrics) {
        this(hits, strategy, metrics, documentIdsOf(hits), chunkIdsOf(hits));
    }

    public EnterpriseRetrievalResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
        candidateDocumentIds = candidateDocumentIds == null ? List.of() : List.copyOf(candidateDocumentIds);
        candidateChunkIds = candidateChunkIds == null ? List.of() : List.copyOf(candidateChunkIds);
    }

    private static List<String> documentIdsOf(List<EnterpriseSearchHit> hits) {
        return hits == null ? List.of() : hits.stream().map(EnterpriseSearchHit::externalId).distinct().toList();
    }

    private static List<String> chunkIdsOf(List<EnterpriseSearchHit> hits) {
        return hits == null ? List.of() : hits.stream().map(EnterpriseSearchHit::chunkId).toList();
    }
}
