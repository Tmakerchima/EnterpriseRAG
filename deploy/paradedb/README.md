# ParadeDB BM25 integration

This directory contains the explicit ParadeDB publication/subscription, BM25 index and smoke SQL. Start the optional search service from the repository root:

```powershell
docker compose -f .\deploy\docker-compose.paradedb.yml up -d
```

Set `ENTERPRISE_RAG_LEXICAL_BACKEND=PARADEDB_BM25` and the `ENTERPRISE_RAG_BM25_*` connection variables only for an integration run. If the service falls back to PostgreSQL FTS, the evaluator records `BACKEND_MISMATCH`; it is not reported as a BM25 result.
