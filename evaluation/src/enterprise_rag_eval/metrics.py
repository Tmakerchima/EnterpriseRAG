from __future__ import annotations

import math
import re
from collections import Counter
from collections.abc import Iterable
from typing import Any

from .models import EvaluationCase


def ranked_unique(ids: Iterable[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in ids:
        value = str(value)
        if value and value not in seen:
            result.append(value)
            seen.add(value)
    return result


def hit_rate(retrieved: list[str], expected: set[str], k: int) -> float:
    return float(bool(expected and expected.intersection(ranked_unique(retrieved)[:k])))


def recall(retrieved: list[str], expected: set[str], k: int) -> float:
    return len(expected.intersection(ranked_unique(retrieved)[:k])) / len(expected) if expected else math.nan


def precision(retrieved: list[str], expected: set[str], k: int) -> float:
    top = ranked_unique(retrieved)[:k]
    return len(expected.intersection(top)) / len(top) if top else 0.0


def mrr(retrieved: list[str], expected: set[str], k: int) -> float:
    for index, value in enumerate(ranked_unique(retrieved)[:k], start=1):
        if value in expected:
            return 1.0 / index
    return 0.0


def ndcg(retrieved: list[str], expected: set[str], k: int) -> float:
    if not expected:
        return math.nan
    top = ranked_unique(retrieved)[:k]
    dcg = sum(1.0 / math.log2(index + 2) for index, value in enumerate(top) if value in expected)
    ideal = sum(1.0 / math.log2(index + 2) for index in range(min(k, len(expected))))
    return dcg / ideal if ideal else 0.0


def average_precision(retrieved: list[str], expected: set[str], k: int) -> float:
    if not expected:
        return math.nan
    top = ranked_unique(retrieved)[:k]
    hits = 0
    total = 0.0
    for index, value in enumerate(top, start=1):
        if value in expected:
            hits += 1
            total += hits / index
    return total / min(len(expected), k) if expected else 0.0


def wilson_interval(successes: int, total: int, z: float = 1.959963984540054) -> list[float] | None:
    if total <= 0:
        return None
    p = successes / total
    denominator = 1 + z * z / total
    centre = (p + z * z / (2 * total)) / denominator
    margin = z * math.sqrt((p * (1 - p) + z * z / (4 * total)) / total) / denominator
    return [max(0.0, centre - margin), min(1.0, centre + margin)]


def macro(values: list[float]) -> dict[str, Any]:
    values = [value for value in values if not math.isnan(value)]
    if not values:
        return {"value": None, "count": 0, "ci95": None}
    successes = sum(values)
    return {
        "value": sum(values) / len(values),
        "count": len(values),
        "ci95": wilson_interval(round(successes), len(values)) if all(value in {0.0, 1.0} for value in values) else None,
    }


def score_retrieval(cases: list[EvaluationCase], predictions: dict[str, dict[str, Any]], ks: tuple[int, ...] = (1, 3, 5, 10)) -> dict[str, Any]:
    eligible = [case for case in cases if case.has_retrieval_gold and case.case_id in predictions]
    excluded = {
        "no_gold": sum(not case.has_retrieval_gold for case in cases),
        "missing_prediction": sum(case.has_retrieval_gold and case.case_id not in predictions for case in cases),
    }
    result: dict[str, Any] = {"status": "MEASURED" if eligible else "NOT_EXECUTED", "eligible_cases": len(eligible), "excluded": excluded, "ks": {}}
    final_context_values = [
        float(bool(set(case.expected_document_ids).intersection(
            ranked_unique(predictions[case.case_id].get("final_document_ids",
                                                         predictions[case.case_id].get("document_ids", []))))))
        for case in eligible
    ]
    for k in ks:
        values = {"hit_rate": [], "recall": [], "precision": [], "mrr": [], "ndcg": [], "map": []}
        for case in eligible:
            retrieved = predictions[case.case_id].get("document_ids", [])
            expected = set(case.expected_document_ids)
            values["hit_rate"].append(hit_rate(retrieved, expected, k))
            values["recall"].append(recall(retrieved, expected, k))
            values["precision"].append(precision(retrieved, expected, k))
            values["mrr"].append(mrr(retrieved, expected, k))
            values["ndcg"].append(ndcg(retrieved, expected, k))
            values["map"].append(average_precision(retrieved, expected, k))
        result["ks"][str(k)] = {name: macro(items) for name, items in values.items()}
    # Candidate recall and final-context coverage are separate: context budgets
    # or reranking can remove a gold document even when retrieval found it.
    result["gold_in_final_context"] = macro(final_context_values)
    return result


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value.casefold()).strip()


def token_f1(predicted: str, expected: str) -> float:
    predicted_tokens = normalize_text(predicted).split()
    expected_tokens = normalize_text(expected).split()
    if not predicted_tokens or not expected_tokens:
        return float(predicted_tokens == expected_tokens)
    overlap = sum((Counter(predicted_tokens) & Counter(expected_tokens)).values())
    if not overlap:
        return 0.0
    precision_value = overlap / len(predicted_tokens)
    recall_value = overlap / len(expected_tokens)
    return 2 * precision_value * recall_value / (precision_value + recall_value)


def fact_coverage(answer: str, facts: Iterable[str]) -> float | None:
    facts = [normalize_text(fact) for fact in facts if normalize_text(fact)]
    if not facts:
        return None
    normalized_answer = normalize_text(answer)
    return sum(fact in normalized_answer for fact in facts) / len(facts)


def score_generation(cases: list[EvaluationCase], predictions: dict[str, dict[str, Any]]) -> dict[str, Any]:
    rows = []
    for case in cases:
        prediction = predictions.get(case.case_id)
        if prediction is None:
            continue
        answer = str(prediction.get("answer", ""))
        gold = str(case.gold_answer or "")
        rows.append({
            "case_id": case.case_id,
            "exact_match": float(bool(gold) and normalize_text(answer) == normalize_text(gold)),
            "token_f1": token_f1(answer, gold) if gold else None,
            # Exact normalized containment is a useful regression signal and a
            # sufficient proof of coverage, but a zero cannot establish that a
            # paraphrased answer missed the fact. Keep it explicitly lexical.
            "verbatim_fact_coverage": fact_coverage(answer, case.answer_facts),
            "empty_answer": float(not answer.strip()),
            "citation_schema_valid": float(all(isinstance(item, str) and item for item in prediction.get("citations", []))),
            "citation_validity": citation_validity(case, prediction),
            "error": bool(prediction.get("metrics", {}).get("error")),
        })
    result = {"status": "MEASURED" if rows else "NOT_EXECUTED", "eligible_cases": len(rows), "cases": rows}
    for key in ("exact_match", "token_f1", "verbatim_fact_coverage", "citation_schema_valid", "citation_validity"):
        result[key] = macro([row[key] for row in rows if row[key] is not None])
    result["fact_coverage"] = {
        "status": "NOT_EXECUTED",
        "value": None,
        "count": 0,
        "ci95": None,
        "reason": "semantic fact coverage requires an LLM judge or human review",
    }
    result["case_success"] = score_success(cases, predictions)
    return result


def citation_validity(case: EvaluationCase, prediction: dict[str, Any]) -> float:
    """Check that returned citations point to chunks in the final context."""
    citations = {str(value) for value in prediction.get("citations", []) if str(value)}
    authorized_chunks = {str(value) for value in prediction.get(
        "final_chunk_ids", prediction.get("chunk_ids", [])) if str(value)}
    if case.answerability != "ANSWERABLE" and not citations:
        return 1.0
    return float(bool(citations) and citations.issubset(authorized_chunks))


def case_success(case: EvaluationCase, prediction: dict[str, Any]) -> tuple[bool | None, list[str]]:
    reasons: list[str] = []
    metrics = prediction.get("metrics", {})
    if metrics.get("error") or prediction.get("error"):
        reasons.append("PRODUCT_ERROR")
    if prediction.get("security_violation") or prediction.get("forbidden_document_ids"):
        reasons.append("SECURITY_VIOLATION")
    if case.answerability == "ANSWERABLE":
        if not set(case.expected_document_ids).intersection(prediction.get("final_document_ids", prediction.get("document_ids", []))):
            reasons.append("REQUIRED_EVIDENCE_NOT_IN_FINAL_CONTEXT")
    else:
        if not prediction.get("abstained", False):
            reasons.append("ABSTENTION_FAILURE")
    if reasons:
        return False, reasons
    if case.answerability == "ANSWERABLE" and case.answer_facts:
        lexical_coverage = fact_coverage(str(prediction.get("answer", "")), case.answer_facts)
        if lexical_coverage != 1:
            return None, ["SEMANTIC_FACT_REVIEW_REQUIRED"]
    return True, []


def score_success(cases: list[EvaluationCase], predictions: dict[str, dict[str, Any]]) -> dict[str, Any]:
    rows = []
    excluded = 0
    for case in cases:
        prediction = predictions.get(case.case_id)
        if prediction is None or (case.answerability == "ANSWERABLE" and not case.expected_document_ids):
            excluded += 1
            continue
        passed, reasons = case_success(case, prediction)
        outcome = "PASS" if passed is True else "FAIL" if passed is False else "NEEDS_REVIEW"
        rows.append({"case_id": case.case_id, "success": passed, "outcome": outcome, "reasons": reasons})
    successes = sum(row["success"] is True for row in rows)
    failures = sum(row["success"] is False for row in rows)
    pending = sum(row["success"] is None for row in rows)
    evidence_ready = sum(row["success"] is not False for row in rows)
    fully_resolved = bool(rows) and pending == 0
    return {
        "status": "MEASURED" if fully_resolved else "INCOMPLETE" if rows else "NOT_EXECUTED",
        "eligible_cases": len(rows),
        "successful_cases": successes,
        "failed_cases": failures,
        "pending_review_cases": pending,
        "evidence_ready_cases": evidence_ready,
        "evidence_ready_rate": evidence_ready / len(rows) if rows else None,
        "excluded_cases": excluded,
        "success_rate": successes / len(rows) if fully_resolved else None,
        "ci95": wilson_interval(successes, len(rows)) if fully_resolved else None,
        "cases": rows,
    }


def score_security(cases: list[EvaluationCase], predictions: dict[str, dict[str, Any]]) -> dict[str, Any]:
    rows = []
    observed_requests = sum(case.case_id in predictions for case in cases)
    for case in cases:
        prediction = predictions.get(case.case_id)
        if prediction is None:
            continue
        tags = {value.casefold() for value in case.tags}
        category = case.category.casefold()
        is_acl_negative = bool(case.forbidden_document_ids)
        is_injection = category in {"prompt_injection", "injection"} or bool(tags.intersection({"prompt_injection", "injection"}))
        if not is_acl_negative and not is_injection:
            continue
        retrieved = set(prediction.get("document_ids", []))
        forbidden = retrieved.intersection(case.forbidden_document_ids)
        rows.append({"case_id": case.case_id, "forbidden_retrieval": bool(forbidden),
                     "unauthorized_citation": bool(prediction.get("unauthorized_citation")),
                     "prompt_injection_complied": bool(prediction.get("prompt_injection_complied")),
                     "acl_negative": is_acl_negative, "prompt_injection": is_injection,
                     "forbidden_document_ids": sorted(forbidden)})
    leak_count = sum(row["forbidden_retrieval"] or row["unauthorized_citation"] for row in rows)
    injection_count = sum(row["prompt_injection_complied"] for row in rows)
    return {"status": "MEASURED" if rows else "NOT_EXECUTED", "cases": len(rows),
            "observed_requests": observed_requests,
            "acl_negative_cases": sum(row["acl_negative"] for row in rows),
            "prompt_injection_cases": sum(row["prompt_injection"] for row in rows),
            "forbidden_retrieval_count": leak_count,
            "prompt_injection_compliance_count": injection_count,
            "hard_gate": "FAIL" if leak_count or injection_count else "PASS" if rows else "NOT_EXECUTED",
            "case_results": rows}


def score_performance(predictions: dict[str, dict[str, Any]]) -> dict[str, Any]:
    values = [float(row.get("metrics", {}).get("total_ms")) for row in predictions.values()
              if row.get("metrics", {}).get("total_ms") is not None]
    if not values:
        return {"status": "NOT_EXECUTED", "request_count": len(predictions)}
    values.sort()

    def percentile(percent: float) -> float:
        position = (len(values) - 1) * percent
        lower = math.floor(position)
        upper = math.ceil(position)
        return values[lower] + (values[upper] - values[lower]) * (position - lower)

    return {"status": "MEASURED", "request_count": len(predictions), "p50_ms": percentile(0.5),
            "p95_ms": percentile(0.95), "p99_ms": percentile(0.99),
            "error_rate": sum(bool(row.get("metrics", {}).get("error") or row.get("error")) for row in predictions.values()) / len(predictions),
            "fallback_rate": sum(bool(row.get("metrics", {}).get("fallback")) for row in predictions.values()) / len(predictions)}


def score_corpus(rows: list[dict[str, Any]]) -> dict[str, Any]:
    if not rows:
        return {"status": "NOT_EXECUTED", "reason": "no ingestion rows supplied"}
    hashes = [str(row.get("content_hash")) for row in rows if row.get("content_hash")]
    token_counts = sorted(int(row.get("token_count", 0)) for row in rows)
    empty_chunks = sum(not str(row.get("content", "")).strip() for row in rows)
    parse_failures = sum(str(row.get("status", "")).upper() in {"FAILED", "PARSE_FAILED"} for row in rows)
    acl_missing = sum(row.get("acl_complete") is False for row in rows)
    embedded_missing = sum(row.get("embedded") is False for row in rows)

    def percentile(percent: float) -> int:
        return token_counts[min(len(token_counts) - 1, round((len(token_counts) - 1) * percent))]

    return {"status": "MEASURED", "expected_documents": len(rows), "loaded_documents": len(rows) - parse_failures,
            "indexed_documents": sum(row.get("indexed", True) is not False for row in rows),
            "coverage": (len(rows) - parse_failures) / len(rows), "parse_failure_rate": parse_failures / len(rows),
            "empty_chunk_rate": empty_chunks / len(rows), "duplicate_content_hash_ratio": 1 - len(set(hashes)) / len(hashes) if hashes else None,
            "chunk_token_length": {"p50": percentile(0.5), "p95": percentile(0.95), "max": max(token_counts)},
            "acl_incomplete_count": acl_missing, "embedding_missing_count": embedded_missing}
