# Extraction map

| Source material | Target | Treatment |
|---|---|---|
| `portfolio-rag/src/main/java/.../enterprise` | `backend/src/main/java/com/tmakerchima/enterpriserag` | package and application boundary renamed |
| V1-V4 migrations | `backend/src/main/resources/db/migration` | retained; V5 adds telemetry/feedback/queue/bad cases |
| `enterprise-rag-frontend` | `frontend` | standalone UI, typed SSE and feedback |
| `eval` worker/adapters | `evaluation` | canonical worker plus installable evaluation package; obsolete HTTP importer removed |
| `deploy/paradedb` | `deploy/paradedb` | local integration SQL retained |
| Portfolio app/config/MCP/knowledge | excluded | no runtime or UI dependency |

Post-extraction simplification keeps one online retrieval orchestration and one
canonical production ingestion path. The old query-planner/expanded-query
branch was removed; the Java ingestion endpoint and its obsolete HTTP importer
were removed, leaving the Python worker as the single ingestion path.
