package com.tmakerchima.enterpriserag.retrieval;

import com.tmakerchima.enterpriserag.model.EnterpriseSearchHit;

import java.util.List;

public interface Reranker {
    List<EnterpriseSearchHit> rerank(String query, List<EnterpriseSearchHit> candidates);
}
