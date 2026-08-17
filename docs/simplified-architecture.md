# Simplified EnterpriseRAG architecture

The online path intentionally has one retrieval orchestration. Query planning is
not part of the runtime path because it adds an LLM call and a second fusion
loop without a benchmark-backed requirement.

```text
Document text
    |
    v
Python worker (canonical production ingestion)
    parse -> chunk -> metadata -> optional contextual prefix -> embedding -> STAGING corpus
                                                                    |
                                                                    v
                                                        explicit activate / rollback
                                                                    |
                                                                    v
User request -> validated tenant + ACL context
    |
    v
ACL-aware retrieval in SQL
    +--> PGVector search
    +--> PostgreSQL FTS or optional ParadeDB BM25
    |
    v
RRF rank fusion
    |
    +--> optional bounded reranker (failure falls back to RRF)
    |
    v
deduplicate -> per-document cap -> token/character budget
    |
    v
RetrievalResult (chunks, source metadata, ranks, timing, fallback)
    |
    v
grounded context -> ChatClient (or deterministic abstention when empty)
    |
    v
versioned SSE: sources -> token -> metrics -> done
    |
    +--> redacted trace / telemetry / feedback queue
    +--> deterministic evaluation -> triage -> manual review -> regression case
```

## Runtime boundaries

- `EnterpriseDocumentRepository` owns database filtering. Every vector, FTS and
  BM25 query includes `ACTIVE` corpus, soft-delete and the same tenant/ACL
  predicates before ranking.
- `EnterpriseRetrievalService.retrieve(...)` is the only online retrieval
  orchestration. `RrfFusion` is used because vector and lexical scores do not
  share a numeric scale.
- `EnterpriseChatService` only validates the request, calls retrieval, builds a
  grounded prompt, emits sources/metrics and calls the model when evidence
  exists. It never knows SQL, FTS, BM25 or reranker details.
- The Python worker keeps generated contextual prefixes separate from source
  evidence; the Java online path only reads the resulting ACTIVE corpus.

## Ingestion decision

`evaluation/enterprise_rag_worker.py` is the only ingestion path:
it is restartable, writes only to a STAGING corpus, keeps a checkpoint and
requires explicit activation. The Java service only exposes corpus lifecycle
operations (`create`, `activate`, and `rollback`).

## Evaluation decision

The installable `enterprise_rag_eval` package is canonical. The deterministic
layers are retrieval, generation/citation/abstention, security and runtime.
DeepEval is optional and returns `NOT_EXECUTED` when unavailable. The old
top-level scripts remain compatibility adapters for existing benchmark commands;
they are not a second scoring implementation.

## Deliberately retained complexity

Tenant isolation, SQL ACL, corpus generations, rollback, fingerprint-based
reproducibility, source/chunk metadata, FTS/BM25 fallback, reranker fallback,
redacted traces, deterministic evaluation and bad-case promotion are retained
because they solve real enterprise operating risks.
