package com.tmakerchima.enterpriserag.controller;

import com.tmakerchima.enterpriserag.service.EnterpriseHumanReviewService;
import com.tmakerchima.enterpriserag.service.EnterpriseReviewJudgeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EnterpriseHumanReviewControllerTest {

    @Test
    void reviewQueueReadDoesNotRequireAnAdminToken() {
        EnterpriseHumanReviewService service = mock(EnterpriseHumanReviewService.class);
        EnterpriseReviewJudgeService judge = mock(EnterpriseReviewJudgeService.class);
        EnterpriseHumanReviewController controller = new EnterpriseHumanReviewController(service, judge);

        assertThat(controller.list("PENDING", 100).getStatusCode().value()).isEqualTo(200);
        verify(service).list("PENDING", 100);
    }
}
