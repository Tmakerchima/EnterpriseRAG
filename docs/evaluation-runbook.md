# Evaluation runbook

1. Apply reviewed V1-V6 migrations to a non-production database and configure only environment variables.
2. Validate cases and export ACTIVE corpus IDs.
3. Run `collect`; inspect `run-manifest.json` before scoring.
4. Run retrieval, generation, triage and report as separate resumable commands.
5. Compare only paired runs with the same dataset version/hash, corpus generation, access contexts and seed.
6. Treat `NOT_EXECUTED`, `UNSUPPORTED`, `SKIPPED_EXTERNAL_DEPENDENCY`, `BACKEND_MISMATCH` and `INVALID_RUN` as explicit statuses.

Production corpus activation, full corpus download, paid judge, large embedding and production database changes require an explicit human approval. The online endpoint never synchronously calls DeepEval. V6 adds an operator-only human-review queue; keep its GET/PATCH endpoints behind the admin token and apply the same retention policy as other user-generated content.
