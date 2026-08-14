package com.tmakerchima.enterpriserag.retrieval;

import com.tmakerchima.enterpriserag.model.EnterpriseSearchHit;
import java.util.List;

/** Test/fallback implementation. Production uses ConfigurableEnterpriseReranker. */
public class NoOpReranker implements Reranker {
    @Override
    public List<EnterpriseSearchHit> rerank(String query, List<EnterpriseSearchHit> candidates) {
        return List.copyOf(candidates);
    }
}
