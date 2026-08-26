# Production deployment

## Backend: Railway

EnterpriseRAG has its own Railway project and must not share the `portfolio-rag`
service:

```text
Railway project: EnterpriseRAG
Service: enterprise-rag-backend
GitHub repository: Tmakerchima/EnterpriseRAG
Branch: main
Root Directory: backend
Dockerfile: /backend/Dockerfile
Health check: /api/enterprise/health
Service domain: enterprise-rag-backend-production.up.railway.app
```

Keep only EnterpriseRAG database, AI and CORS variables in this service. The
`portfolio-rag` project keeps its own repository, variables and domains. A push
to either repository must never deploy the other application.

## Frontend: Vercel

The production frontend is built from this repository:

```text
GitHub: Tmakerchima/EnterpriseRAG
Production branch: main
Vercel project: the existing project that owns enterprise-rag-frontend-seven.vercel.app
```

The Vercel project's Root Directory must be `frontend`. Vercel then uses `frontend/vercel.json` and the standard Vite install/build/output defaults. Configure these project environment variables for Production and Preview:

```text
VITE_API_BASE_URL=https://enterprise-rag-backend-production.up.railway.app
VITE_EVALUATION_REPORT_URL=/evaluation/latest.json
```

The backend already allows the production frontend and `http://localhost:5173`.
If you add another frontend domain, append it to the Railway variable below
using commas (do not remove the existing entries):

```text
ENTERPRISE_RAG_CORS_ORIGINS=http://localhost:5173,https://enterprise-rag-frontend-seven.vercel.app,https://<another-frontend>
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
