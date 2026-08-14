# EnterpriseRAG evaluation harness

This is an installable, restartable Python package. The default path is deterministic and does not require a paid API, a database, a full corpus or a judge key.

```powershell
python -m pip install -e ".[dev]"
python -m pytest
python -m enterprise_rag_eval smoke
```

The optional `.[judge]` extra enables the lazy DeepEval adapter. It emits structured `MEASURED` or `NOT_EXECUTED` results with rubric/model/cache metadata; judge failure is never converted to a quality score of zero. Ragas remains supplemental and is not averaged into the primary score.

## Data boundary

No EnterpriseRAG-Bench documents, private answers, database dumps, secrets or generated reports are committed. Use the official [EnterpriseRAG-Bench release](https://github.com/onyx-dot-app/EnterpriseRAG-Bench) or dataset only after checking release/version/license/SHA-256. A local slice is not evidence for full-corpus coverage. Classify each case as fully supported, partially supported or unsupported before retrieval metrics.

## Pipeline

```powershell
python -m enterprise_rag_eval dataset validate --cases <cases.jsonl>
python -m enterprise_rag_eval collect --cases <cases.jsonl> --api-base http://localhost:8080 --out reports/<run-id>
python -m enterprise_rag_eval score retrieval --run reports/<run-id>
python -m enterprise_rag_eval score generation --run reports/<run-id> --judge none
python -m enterprise_rag_eval triage --run reports/<run-id>
python -m enterprise_rag_eval report --run reports/<run-id>
```

Each run keeps `run-manifest.json`, `cases.jsonl`, `predictions.jsonl`, `traces.jsonl`, layered metrics and static reports. The smoke fixture is explicitly labeled `SYNTHETIC_FIXTURE — NOT A BENCHMARK RESULT`.
