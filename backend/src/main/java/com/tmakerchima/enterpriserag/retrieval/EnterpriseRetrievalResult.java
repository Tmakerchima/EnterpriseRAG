package com.tmakerchima.enterpriserag.retrieval;

import com.tmakerchima.enterpriserag.model.EnterpriseRetrievalStrategy;
import com.tmakerchima.enterpriserag.model.EnterpriseSearchHit;

import java.util.List;

public record EnterpriseRetrievalResult(
        List<EnterpriseSearchHit> hits,
        EnterpriseRetrievalStrategy strategy,
        EnterpriseRetrievalMetrics metrics) {
}
