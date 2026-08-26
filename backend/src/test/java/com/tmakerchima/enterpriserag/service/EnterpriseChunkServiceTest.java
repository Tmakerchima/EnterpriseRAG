package com.tmakerchima.enterpriserag.service;

import com.tmakerchima.enterpriserag.model.EnterpriseAccessContext;
import com.tmakerchima.enterpriserag.model.EnterpriseChunkView;
import com.tmakerchima.enterpriserag.repository.EnterpriseDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnterpriseChunkServiceTest {

    private final EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
    private final EnterpriseChunkService service = new EnterpriseChunkService(repository);
    private final EnterpriseAccessContext access = EnterpriseAccessContext.from("engineering", "tenant-a");

    @Test
    void listTrimsQueryAndBoundsPagination() {
        when(repository.countChunks(access, "upload limits")).thenReturn(73L);
        when(repository.listChunks(access, "upload limits", 50, 0)).thenReturn(List.of());

        EnterpriseChunkService.ChunkPage page = service.list(access, "  upload limits  ", -2, 500);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.total()).isEqualTo(73);
        assertThat(page.totalPages()).isEqualTo(2);
        verify(repository).listChunks(access, "upload limits", 50, 0);
    }

    @Test
    void detailReturnsOnlyRepositoryAuthorizedResult() {
        EnterpriseChunkView chunk = new EnterpriseChunkView("chunk-1", "doc-1", "external-1",
                "github", "github", "Upload guide", "complete source text", 0, 3,
                true, "engineering", "internal", "v2", Map.of());
        when(repository.findChunk("chunk-1", access)).thenReturn(Optional.of(chunk));

        assertThat(service.find("chunk-1", access)).contains(chunk);
        assertThat(service.find("", access)).isEmpty();
        verify(repository).findChunk("chunk-1", access);
    }

    @Test
    void listBoundsInspectionQueryLength() {
        String longQuery = "x".repeat(300);
        String bounded = "x".repeat(200);
        when(repository.countChunks(access, bounded)).thenReturn(0L);
        when(repository.listChunks(access, bounded, 12, 0)).thenReturn(List.of());

        EnterpriseChunkService.ChunkPage page = service.list(access, longQuery, 0, null);

        assertThat(page.query()).hasSize(200);
        verify(repository).countChunks(access, bounded);
    }
}
