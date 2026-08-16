from __future__ import annotations

import argparse
import json
import random
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from typing import Any

from .judge import DeepEvalJudgeAdapter, NotExecutedJudgeAdapter
from .metrics import (
    case_success,
    score_corpus,
    score_generation,
    score_performance,
    score_retrieval,
    score_security,
)
from .models import (
    EvaluationCase,
    canonical_hash,
    load_cases,
    load_jsonl,
    sha256_file,
    write_jsonl,
)
from .report import write_report
from .sse import collect_messages
from .triage import fingerprint, triage_case


def _json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


def _predictions(run_dir: Path) -> dict[str, dict[str, Any]]:
    path = run_dir / "predictions.jsonl"
    return {str(row["case_id"]): row for row in load_jsonl(path)} if path.exists() else {}


def _target_metadata(api_base: str) -> dict[str, Any]:
    """Capture comparable, non-secret corpus/runtime identity for the run."""
    allowed = {
        "status", "corpus_id", "dataset_name", "dataset_version", "state",
        "document_count", "chunk_count", "embedded_chunk_count", "failed_count",
        "source_distribution", "embedding_provider", "embedding_model",
        "embedding_dimension", "chunker_version", "lexical_backend", "vector_backend",
    }
    request = urllib.request.Request(
        api_base.rstrip("/") + "/api/enterprise/health",
        headers={"Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = json.loads(response.read().decode("utf-8"))
        return {key: payload[key] for key in allowed if key in payload}
    except (OSError, ValueError, TimeoutError, urllib.error.URLError):
        return {"status": "UNAVAILABLE"}


def validate_dataset(args: argparse.Namespace) -> int:
    try:
        cases = load_cases(args.cases)
        result = {"status": "MEASURED", "cases": len(cases), "dataset_hash": sha256_file(args.cases),
                  "answerable": sum(case.answerability == "ANSWERABLE" for case in cases),
                  "unanswerable": sum(case.answerability == "UNANSWERABLE" for case in cases),
                  "ambiguous": sum(case.answerability == "AMBIGUOUS" for case in cases)}
    except (OSError, ValueError) as error:
        result = {"status": "INVALID_RUN", "error": str(error)}
    if args.output:
        _json(args.output, result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "MEASURED" else 2


def collect_case(api_base: str, case: EvaluationCase, strategy: str, role: str) -> dict[str, Any]:
    request_id = str(uuid.uuid4())
    trace_id = str(uuid.uuid4())
    body = json.dumps({"question": case.question, "role": role, "tenantId": case.access_context.get("tenant", "default"),
                       "strategy": strategy}).encode("utf-8")
    request = urllib.request.Request(api_base.rstrip("/") + "/api/enterprise/chat", data=body, method="POST", headers={
        "Content-Type": "application/json", "Accept": "text/event-stream", "X-Request-Id": request_id, "X-Trace-Id": trace_id,
    })
    with urllib.request.urlopen(request, timeout=90) as response:
        raw = response.read().decode("utf-8")
    answer, sources, metrics, error = collect_messages([raw])
    chunk_ids = [str(source.get("chunk_id")) for source in sources if source.get("chunk_id")]
    final_document_ids = [str(source.get("document_id")) for source in sources]
    candidate_document_ids = [str(value) for value in metrics.get("candidate_document_ids", final_document_ids)]
    candidate_chunk_ids = [str(value) for value in metrics.get("candidate_chunk_ids", chunk_ids)]
    return {"case_id": case.case_id, "question": case.question, "answer": answer,
            "document_ids": candidate_document_ids,
            "final_document_ids": final_document_ids,
            "chunk_ids": candidate_chunk_ids, "final_chunk_ids": chunk_ids,
            "contexts": [str(source.get("chunk", "")) for source in sources],
            "citations": chunk_ids, "metrics": metrics,
            "request_id": request_id, "trace_id": trace_id, "error": error}


def collect(args: argparse.Namespace) -> int:
    cases = load_cases(args.cases)
    args.out.mkdir(parents=True, exist_ok=True)
    scope = json.loads(args.scope_manifest.read_text(encoding="utf-8")) if args.scope_manifest else {}
    scope.pop("outputs", None)
    target = _target_metadata(args.api_base)
    selected_cases = cases[:args.limit] if args.limit else cases
    case_payload = [case.to_dict() for case in selected_cases]
    manifest = {"manifest_version": "enterprise-rag.run.v1", "status": "MEASURED", "profile": args.profile,
                "run_id": args.out.name, "dataset_hash": sha256_file(args.cases),
                "cases_hash": canonical_hash(case_payload),
                "dataset_version": sorted({case.dataset_version for case in cases}),
                "config_hash": canonical_hash({"strategy": args.strategy, "role": args.role,
                                               "api_base": args.api_base, "scope": scope, "target": target}),
                "strategy": args.strategy, "seed": args.seed, "created_at": __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat()}
    if scope:
        manifest["scope"] = scope
    manifest["target"] = target
    random.seed(args.seed)
    predictions: list[dict[str, Any]] = []
    traces: list[dict[str, Any]] = []
    for index, case in enumerate(selected_cases, start=1):
        try:
            prediction = collect_case(args.api_base, case, args.strategy, args.role)
            predictions.append(prediction)
            traces.append({"trace_schema_version": "enterprise-rag.trace.v1", "case_id": case.case_id,
                           "request_id": prediction["request_id"], "trace_id": prediction["trace_id"],
                           "candidate_document_ids": prediction["document_ids"],
                           "final_document_ids": prediction["final_document_ids"], "judge_status": "NOT_EXECUTED",
                           "candidate_chunk_ids": prediction.get("chunk_ids", []),
                           "final_chunk_ids": prediction.get("final_chunk_ids", []),
                           "gold_valid": True, "coverage_status": "fully_supported" if case.expected_document_ids else "unsupported",
                           "backend_fallback": bool(prediction.get("metrics", {}).get("fallback")),
                           "backend_mismatch": bool(prediction.get("metrics", {}).get("backend_mismatch"))})
        except (OSError, ValueError, TimeoutError, urllib.error.URLError) as error:
            predictions.append({"case_id": case.case_id, "question": case.question, "answer": "", "document_ids": [],
                                "contexts": [], "metrics": {"error": str(error)}, "error": str(error)})
            traces.append({"trace_schema_version": "enterprise-rag.trace.v1", "case_id": case.case_id,
                           "request_id": str(uuid.uuid4()), "trace_id": str(uuid.uuid4()),
                           "candidate_document_ids": [], "final_document_ids": [], "gold_valid": True,
                           "coverage_status": "unknown", "deterministic_failure": True,
                           "error": str(error)})
        print(f"collected {index}/{len(selected_cases)} {case.case_id}", file=sys.stderr, flush=True)
    write_jsonl(args.out / "predictions.jsonl", predictions)
    write_jsonl(args.out / "traces.jsonl", traces)
    write_jsonl(args.out / "cases.jsonl", case_payload)
    _json(args.out / "run-manifest.json", manifest)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


def score_retrieval_command(args: argparse.Namespace) -> int:
    cases = load_cases(args.run / "cases.jsonl") if (args.run / "cases.jsonl").exists() else load_cases(args.cases)
    result = score_retrieval(cases, _predictions(args.run))
    _json(args.run / "metrics.json", result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


def score_generation_command(args: argparse.Namespace) -> int:
    cases = load_cases(args.run / "cases.jsonl") if (args.run / "cases.jsonl").exists() else load_cases(args.cases)
    predictions = _predictions(args.run)
    result = score_generation(cases, predictions)
    adapter = DeepEvalJudgeAdapter(model=args.judge_model, threshold=args.threshold) if args.judge == "deepeval" else NotExecutedJudgeAdapter()
    judge_rows = []
    for case in cases:
        prediction = predictions.get(case.case_id)
        if prediction is None:
            continue
        judge_rows.append(adapter.score(question=case.question, answer=str(prediction.get("answer", "")),
                                       contexts=list(prediction.get("contexts", [])), expected=case.gold_answer, case_id=case.case_id).to_dict())
    result["judge"] = {"status": "MEASURED" if any(row["status"] == "MEASURED" for row in judge_rows) else "NOT_EXECUTED",
                       "results": judge_rows, "primary_framework": "DeepEval" if args.judge == "deepeval" else None}
    _json(args.run / "generation-metrics.json", result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


def score_corpus_command(args: argparse.Namespace) -> int:
    rows = load_jsonl(args.run / "ingestion.jsonl") if (args.run / "ingestion.jsonl").exists() else []
    result = score_corpus(rows)
    _json(args.run / "corpus-metrics.json", result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


def score_security_command(args: argparse.Namespace) -> int:
    cases = load_cases(args.run / "cases.jsonl")
    result = score_security(cases, _predictions(args.run))
    _json(args.run / "security-metrics.json", result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["hard_gate"] == "PASS" else 2


def score_performance_command(args: argparse.Namespace) -> int:
    result = score_performance(_predictions(args.run))
    _json(args.run / "performance-metrics.json", result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


def compare(args: argparse.Namespace) -> int:
    baseline_manifest = json.loads((args.baseline / "run-manifest.json").read_text(encoding="utf-8"))
    candidate_manifest = json.loads((args.candidate / "run-manifest.json").read_text(encoding="utf-8"))
    compatible = all(baseline_manifest.get(key) == candidate_manifest.get(key) for key in ("dataset_hash", "dataset_version"))
    baseline = json.loads((args.baseline / "metrics.json").read_text(encoding="utf-8")) if (args.baseline / "metrics.json").exists() else {}
    candidate = json.loads((args.candidate / "metrics.json").read_text(encoding="utf-8")) if (args.candidate / "metrics.json").exists() else {}
    deltas: dict[str, Any] = {}
    for k in ("1", "3", "5", "10"):
        for metric in ("hit_rate", "recall", "precision", "mrr", "ndcg", "map"):
            before = baseline.get("ks", {}).get(k, {}).get(metric, {}).get("value")
            after = candidate.get("ks", {}).get(k, {}).get(metric, {}).get("value")
            if before is not None and after is not None:
                deltas[f"{metric}@{k}"] = {"baseline": before, "candidate": after, "absolute_delta": after - before}
    result = {"status": "MEASURED" if compatible else "INVALID_RUN", "compatible": compatible,
              "reason": None if compatible else "dataset hash/version mismatch", "deltas": deltas,
              "gates": {"retrieval.recall_at_5_absolute_drop_max": 0.02,
                        "status": "PASS" if compatible and deltas.get("recall@5", {}).get("absolute_delta", 0) >= -0.02 else "FAIL"}}
    _json(args.out, result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "MEASURED" and result["gates"]["status"] == "PASS" else 2


def triage(args: argparse.Namespace) -> int:
    cases = {case.case_id: case.to_dict() for case in load_cases(args.run / "cases.jsonl")}
    traces = load_jsonl(args.run / "traces.jsonl") if (args.run / "traces.jsonl").exists() else []
    predictions = _predictions(args.run)
    rows = []
    for trace in traces:
        case = cases.get(str(trace.get("case_id")), {})
        prediction = predictions.get(str(trace.get("case_id")), {})
        merged = dict(trace)
        merged.update({"answer_incorrect": prediction.get("deterministic_failure", False),
                       "security_violation": prediction.get("security_violation", False)})
        result = triage_case(case, merged)
        if result.primary_cause != "UNKNOWN_REQUIRES_REVIEW" or merged.get("deterministic_failure"):
            rows.append({"case_id": trace.get("case_id"), "fingerprint": fingerprint(case, result.primary_cause), **result.to_dict(),
                         "status": "AUTO_TRIAGED"})
    write_jsonl(args.run / "bad-cases.jsonl", rows)
    _json(args.run / "bad-case-summary.json", {"count": len(rows), "causes": {cause: sum(row["primary_cause"] == cause for row in rows) for cause in sorted({row["primary_cause"] for row in rows})}})
    print(json.dumps({"status": "MEASURED", "bad_cases": len(rows)}, ensure_ascii=False, indent=2))
    return 0


def report_command(args: argparse.Namespace) -> int:
    report = write_report(args.run)
    if args.frontend_out:
        _json(args.frontend_out, report)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


def smoke(out_dir: Path | None = None) -> int:
    fixture = Path(__file__).parents[2] / "fixtures" / "smoke-cases.jsonl"
    cases = load_cases(fixture)
    predictions = {
        "smoke-1": {"answer": "The upload limit is 10 MB.", "document_ids": ["doc-upload"], "final_document_ids": ["doc-upload"], "chunk_ids": ["chunk-upload"], "final_chunk_ids": ["chunk-upload"], "contexts": ["The upload limit is 10 MB."], "citations": ["chunk-upload"]},
        "smoke-2": {"answer": "Insufficient evidence.", "document_ids": [], "final_document_ids": [], "chunk_ids": [], "final_chunk_ids": [], "abstained": True, "contexts": [], "citations": []},
    }
    retrieval = score_retrieval(cases, predictions)
    generation = score_generation(cases, predictions)
    assert retrieval["ks"]["5"]["recall"]["value"] == 1.0
    assert generation["verbatim_fact_coverage"]["value"] == 1.0
    assert generation["fact_coverage"]["status"] == "NOT_EXECUTED"
    assert case_success(cases[0], predictions["smoke-1"])[0]
    triage_result = triage_case(cases[0].to_dict(), {"candidate_document_ids": [], "coverage_status": "fully_supported"})
    assert triage_result.primary_cause == "RETRIEVAL_NO_RECALL"
    if out_dir:
        out_dir.mkdir(parents=True, exist_ok=True)
        manifest = {
            "manifest_version": "enterprise-rag.run.v1",
            "status": "MEASURED",
            "profile": "smoke",
            "run_id": out_dir.name,
            "dataset_hash": sha256_file(fixture),
            "cases_hash": canonical_hash([case.to_dict() for case in cases]),
            "dataset_version": sorted({case.dataset_version for case in cases}),
            "config_hash": canonical_hash({"fixture": "synthetic-smoke", "strategy": "HYBRID"}),
            "strategy": "HYBRID",
            "seed": 17,
            "created_at": __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat(),
        }
        traces = [
            {"trace_schema_version": "enterprise-rag.trace.v1", "case_id": "smoke-1",
             "request_id": "synthetic-smoke-1", "trace_id": "synthetic-smoke-trace-1",
             "candidate_document_ids": ["doc-upload"], "final_document_ids": ["doc-upload"],
             "judge_status": "NOT_EXECUTED", "gold_valid": True, "coverage_status": "fully_supported"},
            {"trace_schema_version": "enterprise-rag.trace.v1", "case_id": "smoke-2",
             "request_id": "synthetic-smoke-2", "trace_id": "synthetic-smoke-trace-2",
             "candidate_document_ids": [], "final_document_ids": [], "judge_status": "NOT_EXECUTED",
             "gold_valid": True, "coverage_status": "not_applicable"},
        ]
        judge = NotExecutedJudgeAdapter(reason="synthetic smoke does not invoke a paid judge")
        generation["judge"] = {
            "status": "NOT_EXECUTED",
            "primary_framework": None,
            "results": [judge.score(question=case.question,
                                     answer=str(predictions[case.case_id].get("answer", "")),
                                     contexts=list(predictions[case.case_id].get("contexts", [])),
                                     expected=case.gold_answer, case_id=case.case_id).to_dict() for case in cases],
        }
        write_jsonl(out_dir / "cases.jsonl", [case.to_dict() for case in cases])
        write_jsonl(out_dir / "predictions.jsonl", [{"case_id": case_id, **prediction} for case_id, prediction in predictions.items()])
        write_jsonl(out_dir / "traces.jsonl", traces)
        _json(out_dir / "run-manifest.json", manifest)
        _json(out_dir / "metrics.json", retrieval)
        _json(out_dir / "generation-metrics.json", generation)
        _json(out_dir / "corpus-metrics.json", score_corpus([]))
        _json(out_dir / "security-metrics.json", score_security(cases, predictions))
        _json(out_dir / "performance-metrics.json", score_performance(predictions))
        write_report(out_dir)
    print(json.dumps({"status": "MEASURED", "fixture": "SYNTHETIC_FIXTURE", "retrieval": retrieval["ks"]["5"], "generation": generation}, ensure_ascii=False, indent=2))
    return 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(prog="enterprise_rag_eval")
    commands = root.add_subparsers(dest="command", required=True)
    dataset = commands.add_parser("dataset"); dataset_commands = dataset.add_subparsers(dest="dataset_command", required=True)
    validate = dataset_commands.add_parser("validate"); validate.add_argument("--cases", type=Path, required=True); validate.add_argument("--output", type=Path); validate.set_defaults(handler=validate_dataset)
    collect_parser = commands.add_parser("collect"); collect_parser.add_argument("--cases", type=Path, required=True); collect_parser.add_argument("--api-base", default="http://localhost:8080"); collect_parser.add_argument("--out", type=Path, required=True); collect_parser.add_argument("--strategy", default="HYBRID"); collect_parser.add_argument("--role", default="admin"); collect_parser.add_argument("--limit", type=int); collect_parser.add_argument("--profile", default="smoke"); collect_parser.add_argument("--scope-manifest", type=Path); collect_parser.add_argument("--seed", type=int, default=17); collect_parser.set_defaults(handler=collect)
    retrieval_parser = commands.add_parser("score"); score_commands = retrieval_parser.add_subparsers(dest="score_command", required=True)
    retrieval = score_commands.add_parser("retrieval"); retrieval.add_argument("--run", type=Path, required=True); retrieval.add_argument("--cases", type=Path); retrieval.set_defaults(handler=score_retrieval_command)
    generation = score_commands.add_parser("generation"); generation.add_argument("--run", type=Path, required=True); generation.add_argument("--cases", type=Path); generation.add_argument("--judge", choices=["none", "deepeval"], default="none"); generation.add_argument("--judge-model"); generation.add_argument("--threshold", type=float, default=0.9); generation.set_defaults(handler=score_generation_command)
    corpus = score_commands.add_parser("corpus"); corpus.add_argument("--run", type=Path, required=True); corpus.set_defaults(handler=score_corpus_command)
    security = score_commands.add_parser("security"); security.add_argument("--run", type=Path, required=True); security.set_defaults(handler=score_security_command)
    performance = score_commands.add_parser("performance"); performance.add_argument("--run", type=Path, required=True); performance.set_defaults(handler=score_performance_command)
    compare_parser = commands.add_parser("compare"); compare_parser.add_argument("--baseline", type=Path, required=True); compare_parser.add_argument("--candidate", type=Path, required=True); compare_parser.add_argument("--out", type=Path, required=True); compare_parser.set_defaults(handler=compare)
    triage_parser = commands.add_parser("triage"); triage_parser.add_argument("--run", type=Path, required=True); triage_parser.set_defaults(handler=triage)
    report_parser = commands.add_parser("report"); report_parser.add_argument("--run", type=Path, required=True); report_parser.add_argument("--frontend-out", type=Path); report_parser.set_defaults(handler=report_command)
    smoke_parser = commands.add_parser("smoke"); smoke_parser.add_argument("--out", type=Path); smoke_parser.set_defaults(handler=lambda args: smoke(args.out))
    return root


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        return int(args.handler(args))
    except (OSError, ValueError, TypeError, KeyError, urllib.error.URLError) as error:
        print(json.dumps({"status": "INVALID_RUN", "error": str(error)}, ensure_ascii=False), file=sys.stderr)
        return 2
