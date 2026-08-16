package com.tmakerchima.enterpriserag.retrieval;

import com.tmakerchima.enterpriserag.model.EnterpriseRetrievalStrategy;
import com.tmakerchima.enterpriserag.model.EnterpriseSearchHit;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** Domain name used by the pipeline diagram: retrieved chunks are the evidence set. */
    public List<EnterpriseSearchHit> chunks() {
        return hits;
    }

    /** Stable external document IDs are what API clients and evaluation datasets compare. */
    public List<String> documentIds() {
        return hits.stream().map(EnterpriseSearchHit::externalId).distinct().toList();
    }

    public List<String> chunkIds() {
        return hits.stream().map(EnterpriseSearchHit::chunkId).toList();
    }

    /** Source metadata stays attached to the evidence rather than being reconstructed by Chat. */
    public List<Map<String, Object>> sourceMetadata() {
        return hits.stream().map(hit -> Map.of(
                "document_id", hit.externalId(),
                "chunk_id", hit.chunkId(),
                "source", hit.source(),
                "source_type", hit.sourceType(),
                "title", hit.title(),
                "rank", hit.rank(),
                "score", hit.score(),
                "metadata", hit.metadata())).collect(Collectors.toUnmodifiableList());
    }

    private static List<String> documentIdsOf(List<EnterpriseSearchHit> hits) {
        return hits == null ? List.of() : hits.stream().map(EnterpriseSearchHit::externalId).distinct().toList();
    }

    private static List<String> chunkIdsOf(List<EnterpriseSearchHit> hits) {
        return hits == null ? List.of() : hits.stream().map(EnterpriseSearchHit::chunkId).toList();
    }
}
