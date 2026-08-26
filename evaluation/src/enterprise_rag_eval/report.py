from __future__ import annotations

import html
import json
from pathlib import Path
from typing import Any

from .models import canonical_hash, load_jsonl


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def _snippet(value: Any, limit: int = 800) -> str:
    text = " ".join(str(value or "").split())
    return text if len(text) <= limit else text[:limit].rstrip() + "…"


def _case_details(run_dir: Path, generation: dict[str, Any]) -> list[dict[str, Any]]:
    """Join display-safe case, prediction and source details into the report.

    Metrics deliberately stay separate from this presentation projection.  A
    bounded source excerpt is enough for audit UI and avoids publishing an
    entire enterprise document through a static report.
    """
    case_path = run_dir / "cases.jsonl"
    prediction_path = run_dir / "predictions.jsonl"
    cases = {str(row.get("case_id")): row for row in load_jsonl(case_path)} if case_path.exists() else {}
    predictions = ({str(row.get("case_id")): row for row in load_jsonl(prediction_path)}
                   if prediction_path.exists() else {})
    scored = generation.get("case_success", {}).get("cases", [])
    details: list[dict[str, Any]] = []
    for result in scored:
        case_id = str(result.get("case_id", ""))
        case = cases.get(case_id, {})
        prediction = predictions.get(case_id, {})
        source_rows = prediction.get("sources") if isinstance(prediction.get("sources"), list) else []
        sources = []
        if source_rows:
            for rank, source in enumerate(source_rows, start=1):
                if not isinstance(source, dict):
                    continue
                sources.append({
                    "rank": source.get("rank", rank),
                    "title": source.get("title"),
                    "document_id": source.get("document_id", source.get("external_id")),
                    "chunk_id": source.get("chunk_id"),
                    "content": _snippet(source.get("chunk", source.get("content"))),
                })
        else:
            contexts = prediction.get("contexts", []) if isinstance(prediction.get("contexts"), list) else []
            document_ids = prediction.get("final_document_ids", prediction.get("document_ids", []))
            chunk_ids = prediction.get("final_chunk_ids", prediction.get("chunk_ids", []))
            for index, context in enumerate(contexts):
                sources.append({
                    "rank": index + 1,
                    "title": None,
                    "document_id": document_ids[index] if index < len(document_ids) else None,
                    "chunk_id": chunk_ids[index] if index < len(chunk_ids) else None,
                    "content": _snippet(context),
                })
        details.append({
            **result,
            "question": case.get("question", prediction.get("question")),
            "reference_answer": case.get("gold_answer"),
            "answer": _snippet(prediction.get("answer"), 2_000),
            "expected_document_ids": case.get("expected_document_ids", []),
            "retrieved_document_ids": prediction.get("document_ids", []),
            "final_document_ids": prediction.get("final_document_ids", prediction.get("document_ids", [])),
            "sources": sources,
        })
    return details


def build_report(run_dir: Path) -> dict[str, Any]:
    manifest = load_json(run_dir / "run-manifest.json")
    metrics = load_json(run_dir / "metrics.json")
    generation = load_json(run_dir / "generation-metrics.json")
    if generation.get("case_success"):
        generation["case_success"] = {
            **generation["case_success"],
            "cases": _case_details(run_dir, generation),
        }
    layers = {
        "retrieval": metrics,
        "generation": generation,
        "corpus": load_json(run_dir / "corpus-metrics.json"),
        "security": load_json(run_dir / "security-metrics.json"),
        "performance": load_json(run_dir / "performance-metrics.json"),
    }
    comparison = load_json(run_dir / "comparison.json")
    case_file = run_dir / "cases.jsonl"
    case_hash_matches = True
    if manifest.get("cases_hash") and case_file.exists():
        case_hash_matches = canonical_hash(load_jsonl(case_file)) == manifest.get("cases_hash")
    valid = (bool(manifest) and manifest.get("dataset_hash") and manifest.get("config_hash")
             and manifest.get("status") not in {"INVALID_RUN"} and case_hash_matches)
    if not case_hash_matches:
        manifest = {**manifest, "status": "INVALID_RUN", "invalid_reason": "cases_hash_mismatch"}
    return {"run_validity": "MEASURED" if valid else "INVALID_RUN", "manifest": manifest, "metrics": metrics, "layers": layers,
            "comparison": comparison, "synthetic_fixture": manifest.get("profile") == "smoke"}


def write_report(run_dir: Path) -> dict[str, Any]:
    report = build_report(run_dir)
    (run_dir / "summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# EnterpriseRAG evaluation report", "", f"- Run validity: `{report['run_validity']}`"]
    if report["synthetic_fixture"]:
        lines.append("- **SYNTHETIC_FIXTURE — NOT A BENCHMARK RESULT**")
    metrics = report.get("metrics", {})
    lines.extend(["", "## Metrics", "", "```json", json.dumps(metrics, ensure_ascii=False, indent=2), "```"])
    (run_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    body = html.escape(json.dumps(report, ensure_ascii=False, indent=2))
    (run_dir / "report.html").write_text(f"<!doctype html><meta charset='utf-8'><title>EnterpriseRAG report</title><pre>{body}</pre>", encoding="utf-8")
    return report
