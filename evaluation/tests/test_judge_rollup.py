from enterprise_rag_eval.cli import _apply_judge_to_case_success, _judge_summary


def test_judge_rollup_records_denominator_and_resolves_pending_case() -> None:
    judge_rows = [{
        "case_id": "case-1", "status": "MEASURED", "score": 0.95,
        "passed": True, "reason": "grounded", "judge_model": "judge-model",
    }]
    summary = _judge_summary(judge_rows, 0.9)
    resolved = _apply_judge_to_case_success({
        "eligible_cases": 1,
        "cases": [{"case_id": "case-1", "success": None, "outcome": "NEEDS_REVIEW", "reasons": []}],
    }, judge_rows)

    assert summary["measured_cases"] == 1
    assert summary["mean_score"] == 0.95
    assert resolved["status"] == "MEASURED"
    assert resolved["success_rate"] == 1.0
    assert resolved["cases"][0]["review_method"] == "LLM_AS_JUDGE"
