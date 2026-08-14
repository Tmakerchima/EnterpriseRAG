# Bad-case playbook

The symptom and root cause are separate. The deterministic rules prioritize: invalid gold → corpus coverage → ACL expectation → no candidate recall → fusion/rerank → context truncation → generation → abstention/faithfulness → citation/security. Backend fallback, timeout and rate limit are contributing infrastructure causes. Judge-only failure is `EVALUATOR_OR_JUDGE_FAILURE`, not product quality zero.

Each bundle contains case, access context, answer, gold facts, retrieval stages, final-context hashes/snippets, citations, metrics, trace, manifest, automatic and human causes, owner, status, run IDs and a minimal reproducer. Fingerprints use normalized question + access + expected docs + symptom. Status is `NEW → AUTO_TRIAGED → CONFIRMED → FIX_IN_PROGRESS → FIXED → VERIFIED → PROMOTED_TO_REGRESSION`, with documented dismissal allowed.

```powershell
python -m enterprise_rag_eval triage --run reports/<run-id>
```
