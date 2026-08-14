# Dataset and run manifest

Use separate source labels for official benchmark, human golden, online regression and synthetic security cases. Before a retrieval score, export ACTIVE corpus `external_id`s and classify each case as `fully_supported`, `partially_supported` or `unsupported`. Only fully supported cases enter the primary document metrics.

Every run records:

```text
run_id, dataset_hash/version, corpus_id/generation, config_hash, code SHA/dirty flag,
embedding/chat/reranker/judge models, prompt hash, lexical backend/fallback,
seed, timestamps, dependency lock hash, token-count estimated flag, pricing timestamp
```

Missing manifest or mismatched dataset/config hashes is `INVALID_RUN`, never a comparable score. Full corpus download is external and must be explicitly authorized; it is not stored in Git.
