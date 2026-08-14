# Interview guide

## 60 seconds

EnterpriseRAG is an independent enterprise RAG service. Its value is not making the model magically smarter; it retrieves current private evidence under SQL ACL and returns a grounded answer with source, request and trace IDs. I evaluate corpus coverage, retrieval/ranking, final-context coverage, generation, citation, abstention, security, latency and cost separately. Every run has a manifest, paired baseline comparison and explicit `NOT_EXECUTED` states. Online Micrometer metrics and async feedback feed deterministic bad-case attribution and reviewed regression cases.

## 3 minutes

The system has versioned corpora and ACTIVE generation isolation, token/structure-aware chunking, PGVector plus PostgreSQL FTS or ParadeDB BM25, RRF, optional reranking and a context budget. ACL is applied in SQL before ranking; the stream contract exposes sources, tokens, metrics, errors and done. Offline evaluation first checks whether gold evidence exists in the corpus, then scores retrieval and generation. A security leak is a hard failure, not something a high average can hide. DeepEval is an optional, calibrated judge with model/rubric/cache metadata and no online blocking. Negative feedback, failures and sampled traffic enter an async queue; a bad case is automatically attributed, human-confirmed, rerun on the same case and only then promoted to regression.

| 现象 | 更可能的根因 |
|---|---|
| Recall@K 低 | corpus 缺失、切块、embedding、query、filter、top-k |
| Recall 高但 nDCG/MRR 低 | fusion/ranking/reranker |
| 检索好但 faithfulness/correctness 低 | prompt、context packing、generation model |
| Faithfulness 高但 answer correctness 低 | 证据不完整、gold/rubric、答案遗漏 |
| citation precision 低 | claim-citation mapping 或生成约束 |
| no-answer recall 低 | 拒答策略过于激进或 evidence threshold |
| 离线好、线上差 | query/corpus drift、fallback、延迟、provider、权限分布 |
| 平均分高但用户差评多 | 分组长尾、指标与业务目标错位、judge 未校准 |
