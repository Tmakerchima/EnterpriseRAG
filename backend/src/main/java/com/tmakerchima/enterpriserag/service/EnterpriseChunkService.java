package com.tmakerchima.enterpriserag.service;

import com.tmakerchima.enterpriserag.model.EnterpriseAccessContext;
import com.tmakerchima.enterpriserag.model.EnterpriseChunkView;
import com.tmakerchima.enterpriserag.repository.EnterpriseDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Read-only chunk inspection used by the web chunk pool and citation viewer. */
@Service
public class EnterpriseChunkService {

    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 50;
    private static final int MAX_PAGE = 10_000;
    private static final int MAX_QUERY_LENGTH = 200;

    private final EnterpriseDocumentRepository repository;

    public EnterpriseChunkService(EnterpriseDocumentRepository repository) {
        this.repository = repository;
    }

    public ChunkPage list(EnterpriseAccessContext access, String query, Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null ? 0 : Math.max(0, Math.min(MAX_PAGE, requestedPage));
        int size = requestedSize == null ? DEFAULT_SIZE : Math.max(1, Math.min(MAX_SIZE, requestedSize));
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
            normalizedQuery = normalizedQuery.substring(0, MAX_QUERY_LENGTH);
        }
        long total = repository.countChunks(access, normalizedQuery);
        long offset = Math.multiplyExact((long) page, size);
        List<EnterpriseChunkView> items = repository.listChunks(access, normalizedQuery, size, offset);
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new ChunkPage(items, page, size, total, totalPages, normalizedQuery);
    }

    public Optional<EnterpriseChunkView> find(String chunkId, EnterpriseAccessContext access) {
        if (chunkId == null || chunkId.isBlank() || chunkId.length() > 256) return Optional.empty();
        return repository.findChunk(chunkId, access);
    }

    public record ChunkPage(List<EnterpriseChunkView> items, int page, int size, long total,
                            int totalPages, String query) {
    }
}
