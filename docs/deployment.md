# Frontend deployment

The production frontend is built from this repository:

```text
GitHub: Tmakerchima/EnterpriseRAG
Production branch: main
Vercel project: the existing project that owns enterprise-rag-frontend-seven.vercel.app
```

The Vercel project's Root Directory must be `frontend`. Vercel then uses `frontend/vercel.json` and the standard Vite install/build/output defaults. Configure these project environment variables for Production and Preview:

```text
VITE_API_BASE_URL=https://<shared-backend>
VITE_EVALUATION_REPORT_URL=/evaluation/latest.json
```

In Vercel, open **Project → Settings → Git**, disconnect the legacy repository, connect `Tmakerchima/EnterpriseRAG`, and select `main` as the production branch. Then open **Settings → Build and Deployment** and change Root Directory from the legacy `enterprise-rag-frontend` path to `frontend`. Relinking the existing Vercel project keeps its current domain; creating a separate Vercel project does not.

## Publishing an evaluation scorecard

The frontend reads `frontend/public/evaluation/latest.json`. Publish an evaluator run with:

```powershell
cd evaluation
python -m enterprise_rag_eval report `
  --run reports/<run-id> `
  --frontend-out ../frontend/public/evaluation/latest.json
```

Commit the generated file only after checking `run_validity`, dataset/corpus identity, and hard gates. A smoke report must keep `synthetic_fixture=true`; the UI displays a permanent `NOT A BENCHMARK RESULT` warning.

CI generates and uploads a fresh synthetic scorecard artifact to verify the full report-to-UI contract. Real benchmark runs remain explicit because they require a live backend, versioned corpus, dataset and optional judge credentials.
