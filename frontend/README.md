# EnterpriseRAG frontend

Vue 3 + TypeScript + Vite frontend for the standalone EnterpriseRAG service. Backend URL is injected with `VITE_API_BASE_URL`; the UI never embeds a personal domain or a sample evaluation score.

```powershell
npm ci
npm run build
```

The stream parser accepts the versioned `sources`, `token`, `metrics`, `error`, and `done` events and keeps compatibility with the legacy marker payloads. Feedback is additive: failure to submit it never changes the answer path.

The Evaluation view reads `/evaluation/latest.json` (or `VITE_EVALUATION_REPORT_URL`) and displays case success, confidence intervals, retrieval/generation scores, security gates, latency and case-level failures. It also accepts a local evaluator `summary.json`; the file is parsed in the browser and is not uploaded.

Publish a measured run from the repository root with:

```powershell
cd evaluation
python -m enterprise_rag_eval report --run reports/<run-id> --frontend-out ../frontend/public/evaluation/latest.json
```
