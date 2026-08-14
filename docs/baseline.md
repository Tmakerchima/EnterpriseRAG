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
