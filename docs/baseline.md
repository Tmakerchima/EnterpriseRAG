# Baseline and preflight

Preflight date: 2026-08-14.

- Source repository: `D:\claude\portfolio-rag`, branch `fix/enterprise-bm25-hardening`, HEAD `b2fb134`; source worktree had two untracked Markdown prompt files. It was read only.
- Target repository: `D:\codex\EnterpriseRAG`, initialized on `main`; remote is `https://github.com/Tmakerchima/EnterpriseRAG.git`; remote had no heads at preflight.
- No applicable `AGENTS.md` was found.
- Source baseline was not rerun because the target extraction began from tracked Enterprise paths; the extracted Enterprise unit suite later passed 30 tests with one opt-in ParadeDB test skipped.
- Extraction boundary: Enterprise Java package, V1-V5 SQL migrations, Vue frontend, worker/benchmark adapters, evaluation package, deployment SQL and Enterprise docs. Portfolio controllers/services/knowledge/prompts/MCP were excluded.

Runtime graph:

```text
POST /api/enterprise/chat
  -> EnterpriseChatService
  -> EnterpriseRetrievalService
  -> PGVector SQL + PostgreSQL FTS or ParadeDB BM25
  -> ACL-filtered final context
  -> Spring AI ChatClient
  -> versioned SSE sources/token/metrics/done
```

Measured in this workspace: local Java unit tests, Python deterministic tests/smoke, frontend build. Not executed: real database, real provider, full corpus, DeepEval judge, ParadeDB service integration.

## 2026-08-17 architecture audit and simplification

The target repository was re-audited before code changes. The working tree was
clean and `origin` already pointed only to `Tmakerchima/EnterpriseRAG`. The
runtime call graph was:

```text
chat controller -> ChatService -> RetrievalService -> repository/vector + lexical
                 -> optional planner/expand -> context -> ChatClient -> SSE
```

The planner/expand branch had no benchmark evidence, added an online model call
and duplicated fusion/control flow. It was removed. The resulting call graph is
now:

```text
chat controller -> ChatService -> RetrievalService.retrieve
                 -> SQL ACL-filtered vector + lexical -> RRF -> optional rerank
                 -> context budget -> ChatClient or deterministic abstention -> SSE
```

No migration was removed or changed. Tenant filtering was tightened from a
nullable bypass to an explicit tenant equality in all three retrieval SQL
implementations. The Python worker is the only ingestion path; the deprecated
Java compatibility endpoint and its repository-only importer were removed.
