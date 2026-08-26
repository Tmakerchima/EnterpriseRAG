package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.service.EnterpriseHumanReviewService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EnterpriseHumanReviewControllerTest {

    @Test
    void reviewQueueReadRequiresTheAdminToken() {
        EnterpriseHumanReviewService service = mock(EnterpriseHumanReviewService.class);
        EnterpriseHumanReviewController controller = new EnterpriseHumanReviewController(service, "secret-token");

        assertThat(controller.list(null, "PENDING", 100).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.list("wrong-token", "PENDING", 100).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(service);
    }
}
