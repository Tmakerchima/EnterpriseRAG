# EnterpriseRAG evaluation harness

This is an installable, restartable Python package. The default path is deterministic and does not require a paid API, a database, a full corpus or a judge key.

```powershell
python -m pip install -e ".[dev]"
python -m pytest
python -m enterprise_rag_eval smoke
```

The optional `.[judge]` extra enables the lazy DeepEval adapter. It emits structured `MEASURED` or `NOT_EXECUTED` results with rubric/model/cache metadata; judge failure is never converted to a quality score of zero. Ragas remains supplemental and is not averaged into the primary score.

## Data boundary

No EnterpriseRAG-Bench documents, database dumps, secrets or raw run artifacts are committed. Use the official [EnterpriseRAG-Bench release](https://github.com/onyx-dot-app/EnterpriseRAG-Bench) or dataset only after checking release/version/license/SHA-256. A local slice is not evidence for full-corpus coverage. Classify each case as fully supported, partially supported or unsupported before retrieval metrics. A report may expose case questions, generated answers and bounded source excerpts for audit UI only when the target deployment is authorized to display that content; never publish private enterprise content through a public static report.

For an official source slice, coverage can be derived directly from the ZIP filenames without extracting or reading document content:

```powershell
python filter_questions.py --questions <questions.jsonl> --document-archive <github_slice_0001.zip> --output-dir <coverage-dir>
python -m enterprise_rag_eval collect --cases <coverage-dir>/fully_supported.jsonl --api-base <api-base> --out <run-dir> --profile enterprise-rag-bench-github-slice-v1 --scope-manifest <coverage-dir>/manifest.json
```

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

To publish the same structured report to the Vue scorecard:

```powershell
python -m enterprise_rag_eval report --run reports/<run-id> --frontend-out ../frontend/public/evaluation/latest.json
```

`python -m enterprise_rag_eval smoke --out reports/smoke` writes a complete synthetic run for contract checks. It remains a fixture and must never be presented as a benchmark.
