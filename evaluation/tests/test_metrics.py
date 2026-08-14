from enterprise_rag_eval.metrics import (
    average_precision,
    mrr,
    ndcg,
    precision,
    recall,
    score_generation,
    score_retrieval,
    wilson_interval,
)
from enterprise_rag_eval.models import EvaluationCase


def test_retrieval_fixture_formulas_dedupe_ids_and_short_top_k() -> None:
    retrieved = ["a", "a", "x", "b"]
    expected = {"a", "b"}
    assert recall(retrieved, expected, 2) == 0.5
    assert precision(retrieved, expected, 2) == 0.5
    assert mrr(retrieved, expected, 2) == 1.0
    assert round(ndcg(retrieved, expected, 2), 6) == round(1 / (1 + 1 / 1.5849625007211563), 6)
    assert average_precision(retrieved, expected, 2) == 0.5


def test_no_gold_is_excluded_from_retrieval_denominator() -> None:
    answerable = EvaluationCase(case_id="a", question="q", expected_document_ids=("doc-a",))
    no_gold = EvaluationCase(case_id="b", question="q", answerability="UNANSWERABLE")
    result = score_retrieval([answerable, no_gold], {"a": {"document_ids": ["doc-a"]}})
    assert result["eligible_cases"] == 1
    assert result["excluded"]["no_gold"] == 1


def test_wilson_interval_is_deterministic() -> None:
    assert wilson_interval(8, 10) == wilson_interval(8, 10)


def test_generation_report_keeps_case_level_success_reasons() -> None:
    case = EvaluationCase(
        case_id="answerable",
        question="q",
        answer_facts=("required fact",),
        expected_document_ids=("doc-a",),
    )
    result = score_generation([case], {
        "answerable": {"answer": "wrong", "document_ids": [], "final_document_ids": []},
    })
    case_result = result["case_success"]["cases"][0]
    assert case_result["success"] is False
    assert case_result["outcome"] == "FAIL"
    assert case_result["reasons"] == ["REQUIRED_EVIDENCE_NOT_IN_FINAL_CONTEXT"]


def test_paraphrased_fact_requires_semantic_review_instead_of_false_failure() -> None:
    case = EvaluationCase(
        case_id="paraphrase",
        question="q",
        answer_facts=("The default per-file limit is 10 MiB.",),
        expected_document_ids=("doc-a",),
    )
    result = score_generation([case], {
        "paraphrase": {
            "answer": "Uploads allow 10 MiB for each file.",
            "document_ids": ["doc-a"],
            "final_document_ids": ["doc-a"],
        },
    })

    case_result = result["case_success"]["cases"][0]
    assert result["fact_coverage"]["status"] == "NOT_EXECUTED"
    assert result["case_success"]["evidence_ready_rate"] == 1.0
    assert case_result == {
        "case_id": "paraphrase",
        "success": None,
        "outcome": "NEEDS_REVIEW",
        "reasons": ["SEMANTIC_FACT_REVIEW_REQUIRED"],
    }
