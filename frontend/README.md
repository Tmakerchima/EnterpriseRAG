# EnterpriseRAG frontend

Vue 3 + TypeScript + Vite frontend for the standalone EnterpriseRAG service. Backend URL is injected with `VITE_API_BASE_URL`; the UI never embeds a personal domain or a sample evaluation score.

```powershell
npm ci
npm run build
```

The stream parser accepts the versioned `sources`, `token`, `metrics`, `error`, and `done` events and keeps compatibility with the legacy marker payloads. Feedback is additive: failure to submit it never changes the answer path.
