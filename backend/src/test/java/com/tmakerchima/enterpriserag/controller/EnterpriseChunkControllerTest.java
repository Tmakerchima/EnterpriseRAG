package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.model.EnterpriseAccessContext;
import com.tmakerchima.enterpriserag.model.EnterpriseChunkView;
import com.tmakerchima.enterpriserag.service.EnterpriseChunkService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnterpriseChunkControllerTest {

    private final EnterpriseChunkService service = mock(EnterpriseChunkService.class);
    private final EnterpriseChunkController controller = new EnterpriseChunkController(service);

    @Test
    void listBuildsTheSameNormalizedAclContextAsChat() {
        EnterpriseAccessContext access = EnterpriseAccessContext.from("engineering", "tenant-a");
        EnterpriseChunkService.ChunkPage page = new EnterpriseChunkService.ChunkPage(
                List.of(), 0, 12, 0, 0, "upload");
        when(service.list(access, "upload", 0, 12)).thenReturn(page);

        assertThat(controller.list("engineering", "tenant-a", "upload", 0, 12)).isSameAs(page);
        verify(service).list(access, "upload", 0, 12);
    }

    @Test
    void hiddenOrUnknownChunkUsesNotFoundWithoutLeakingItsExistence() {
        EnterpriseAccessContext access = EnterpriseAccessContext.from("public", "tenant-a");
        when(service.find("private-chunk", access)).thenReturn(Optional.empty());

        assertThat(controller.find("private-chunk", "public", "tenant-a").getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void visibleChunkReturnsOriginalCitableContent() {
        EnterpriseAccessContext access = EnterpriseAccessContext.from("engineering", "tenant-a");
        EnterpriseChunkView chunk = new EnterpriseChunkView("chunk-1", "doc-1", "external-1",
                "github", "github", "Upload guide", "complete source text", 0, 3,
                true, "engineering", "internal", "v2", Map.of());
        when(service.find("chunk-1", access)).thenReturn(Optional.of(chunk));

        assertThat(controller.find("chunk-1", "engineering", "tenant-a").getBody()).isEqualTo(chunk);
    }
}
