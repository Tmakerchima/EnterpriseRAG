from __future__ import annotations

import html
import json
from pathlib import Path
from typing import Any


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def build_report(run_dir: Path) -> dict[str, Any]:
    manifest = load_json(run_dir / "run-manifest.json")
    metrics = load_json(run_dir / "metrics.json")
    layers = {
        "retrieval": metrics,
        "generation": load_json(run_dir / "generation-metrics.json"),
        "corpus": load_json(run_dir / "corpus-metrics.json"),
        "security": load_json(run_dir / "security-metrics.json"),
        "performance": load_json(run_dir / "performance-metrics.json"),
    }
    comparison = load_json(run_dir / "comparison.json")
    valid = bool(manifest) and manifest.get("dataset_hash") and manifest.get("config_hash") and manifest.get("status") not in {"INVALID_RUN"}
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
