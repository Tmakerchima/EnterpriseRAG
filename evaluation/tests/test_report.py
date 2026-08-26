import json
from pathlib import Path

from enterprise_rag_eval.report import build_report


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value), encoding="utf-8")


def test_report_joins_question_answer_and_bounded_source_excerpt(tmp_path: Path) -> None:
    write_json(tmp_path / "run-manifest.json", {
        "status": "MEASURED", "dataset_hash": "dataset", "config_hash": "config",
    })
    write_json(tmp_path / "metrics.json", {"status": "MEASURED"})
    write_json(tmp_path / "generation-metrics.json", {
        "case_success": {"cases": [{
            "case_id": "case-1", "success": None, "outcome": "NEEDS_REVIEW", "reasons": [],
        }]},
    })
    (tmp_path / "cases.jsonl").write_text(json.dumps({
        "case_id": "case-1", "question": "What is the limit?", "gold_answer": "10 MiB",
        "expected_document_ids": ["doc-1"],
    }) + "\n", encoding="utf-8")
    (tmp_path / "predictions.jsonl").write_text(json.dumps({
        "case_id": "case-1", "answer": "It is 10 MiB.", "document_ids": ["doc-1"],
        "final_document_ids": ["doc-1"], "final_chunk_ids": ["chunk-1"],
        "contexts": ["x" * 900],
    }) + "\n", encoding="utf-8")

    detail = build_report(tmp_path)["layers"]["generation"]["case_success"]["cases"][0]

    assert detail["question"] == "What is the limit?"
    assert detail["answer"] == "It is 10 MiB."
    assert detail["reference_answer"] == "10 MiB"
    assert detail["sources"][0]["document_id"] == "doc-1"
    assert len(detail["sources"][0]["content"]) == 801
