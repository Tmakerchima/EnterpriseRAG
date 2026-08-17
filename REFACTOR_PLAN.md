# EnterpriseRAG refactor plan

Audit date: 2026-08-17

This plan is based on repository-wide source, test, configuration, documentation,
and reference searches. The working tree already contained an unrelated,
untracked `.github.zip`; it is intentionally left untouched.

## Runtime call chain confirmed

```text
POST /api/enterprise/chat
  -> EnterpriseChatController
  -> EnterpriseChatService
  -> EnterpriseRetrievalService.retrieve
  -> SQL ACL-filtered vector + lexical retrieval
  -> RRF
  -> optional reranker
  -> context budget
  -> grounded ChatClient or deterministic abstention
  -> versioned SSE
```

Bulk ingestion is implemented by `evaluation/enterprise_rag_worker.py`, which
creates STAGING corpora, checkpoints work, and requires explicit activation.
The Java ingestion code is a separate deprecated synchronous compatibility path.

## KEEP

- `EnterpriseRetrievalService.retrieve(...)` as the single online retrieval
  orchestration entry point.
- `EnterpriseDocumentRepository` retrieval SQL and its shared ACL mapping.
- `EnterpriseLexicalRetriever` and `Reranker` interfaces: each has multiple
  meaningful implementations or is a deliberate test seam.
- FTS/BM25 selection and explicit fallback reporting; this is a real deployment
  boundary, not duplicate retrieval logic.
- Corpus lifecycle (`create`, `activate`, `rollback`) because the Python worker
  depends on versioned STAGING/ACTIVE corpora.
- The installable `enterprise_rag_eval` package and documented top-level
  evaluation adapters until their external command contracts are migrated.
- Frontend API/SSE handling, evaluation dashboard, telemetry, migrations, and
  ParadeDB integration assets.

## DELETE

- The deprecated Java `/api/enterprise/admin/ingest` endpoint, its request DTO,
  Java ingestion service, Java chunker/contextualizer, and their unit tests.
  The repository's only known caller is `evaluation/import_enterprise_bench.py`,
  which is deleted with this legacy path. The canonical Python worker remains.
- The repository-only `EnterpriseIngestionService.delete` and
  `EnterpriseDocumentRepository.softDelete` methods: no endpoint or caller
  uses them.
- `EnterpriseHealthController`'s unused `/stats` endpoint; `/health` already
  returns the same corpus and lexical status used by the frontend and evaluator.
- `EnterpriseCorpusService.setState`, which has no caller; lifecycle changes
  use the explicit activate/rollback methods.
- `EnterpriseChatService.streamAnswer(request)` overload, which has no caller;
  the controller uses the request/trace-aware method.
- `EnterpriseRetrievalResult` convenience accessors that have no callers
  (`chunks`, `documentIds`, `chunkIds`, `sourceMetadata`).
- Production `NoOpReranker`, which is instantiated only by tests; tests will use
  the existing `Reranker` seam with a small lambda instead.
- `spring-ai-starter-vector-store-pgvector` and the unused `spring.ai.vectorstore`
  configuration. Runtime retrieval uses explicit SQL plus `EmbeddingModel`; no
  `VectorStore` or `PgVectorStore` code exists in the project.

## MERGE

- No large service merge is justified. The current online chain has one
  meaningful service per boundary; FTS and BM25 implementations cannot be
  merged without hiding their distinct SQL and fallback behavior.
- Fold the old importer/endpoint removal together so no documented or internal
  path points at a deleted ingestion API.

## SIMPLIFY

- Remove Java-only ingestion configuration (`max-documents`, chunk sizing,
  contextualizer settings, and ingestion embedding fingerprint settings) from
  `application.yml`; those concerns belong to the canonical Python worker.
- Remove repository write/upsert methods that existed solely for the deleted
  Java ingestion path, leaving the repository focused on retrieval and hit
  mapping.
- Update README and architecture/extraction/baseline documentation to state one
  ingestion path instead of documenting a deprecated compatibility route.
- Keep migrations immutable for existing databases; the legacy corpus row and
  historical columns remain database compatibility history, not active Java code.

## Configuration and dependency audit

- No unused frontend dependency was found; Vue/Vite/TypeScript are all used by
  the build.
- BM25, reranker, telemetry, feedback, CORS, and AI chat/embedding settings are
  read by live code and remain.
- No migration is removed or rewritten because migration history is an external
  database contract.

## Verification

1. Run backend Maven tests.
2. Run evaluation pytest, ruff, and package smoke/report commands.
3. Run frontend production build.
4. Re-scan for `/admin/ingest`, deleted Java ingestion symbols, unused removed
   configuration, and stale documentation references.
