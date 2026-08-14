from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from enum import StrEnum
from typing import Any


class Cause(StrEnum):
    DATASET_GOLD_INVALID = "DATASET_GOLD_INVALID"
    CORPUS_COVERAGE_MISSING = "CORPUS_COVERAGE_MISSING"
    FILTER_OR_ACL_MISMATCH = "FILTER_OR_ACL_MISMATCH"
    RETRIEVAL_NO_RECALL = "RETRIEVAL_NO_RECALL"
    FUSION_OR_RANKING = "FUSION_OR_RANKING"
    RERANKER_REGRESSION = "RERANKER_REGRESSION"
    CONTEXT_TRUNCATION = "CONTEXT_TRUNCATION"
    GENERATION_INCORRECT = "GENERATION_INCORRECT"
    GENERATION_INCOMPLETE = "GENERATION_INCOMPLETE"
    HALLUCINATION_OR_UNFAITHFUL = "HALLUCINATION_OR_UNFAITHFUL"
    CITATION_MISSING_OR_UNSUPPORTED = "CITATION_MISSING_OR_UNSUPPORTED"
    ABSTENTION_FAILURE = "ABSTENTION_FAILURE"
    PROMPT_INJECTION = "PROMPT_INJECTION"
    TIMEOUT_RATE_LIMIT_PROVIDER = "TIMEOUT_RATE_LIMIT_PROVIDER"
    EVALUATOR_OR_JUDGE_FAILURE = "EVALUATOR_OR_JUDGE_FAILURE"
    UNKNOWN_REQUIRES_REVIEW = "UNKNOWN_REQUIRES_REVIEW"


@dataclass(frozen=True)
class TriageResult:
    primary_cause: str
    contributing_causes: tuple[str, ...]
    evidence: tuple[str, ...]
    confidence: float

    def to_dict(self) -> dict[str, Any]:
        return {"primary_cause": self.primary_cause, "contributing_causes": list(self.contributing_causes),
                "evidence": list(self.evidence), "confidence": self.confidence}


def triage_case(case: dict[str, Any], trace: dict[str, Any]) -> TriageResult:
    if not case.get("case_valid", True) or trace.get("gold_valid") is False:
        return TriageResult(Cause.DATASET_GOLD_INVALID, (), ("case or gold validation failed",), 1.0)
    if trace.get("coverage_status") in {"unsupported", "missing", "partial"}:
        return TriageResult(Cause.CORPUS_COVERAGE_MISSING, (), (f"coverage_status={trace['coverage_status']}",), 0.99)
    if trace.get("expected_doc_forbidden"):
        return TriageResult(Cause.FILTER_OR_ACL_MISMATCH, (), ("gold document is not authorized for access context",), 0.98)
    expected = set(case.get("expected_document_ids", case.get("expected_doc_ids", [])))
    candidates = set(trace.get("candidate_document_ids", trace.get("document_ids", [])))
    final_context = set(trace.get("final_document_ids", []))
    if expected and not expected.intersection(candidates):
        contributors = []
        if trace.get("backend_fallback") or trace.get("backend_mismatch"):
            contributors.append(Cause.TIMEOUT_RATE_LIMIT_PROVIDER)
        if trace.get("filter_applied"):
            contributors.append(Cause.FILTER_OR_ACL_MISMATCH)
        return TriageResult(Cause.RETRIEVAL_NO_RECALL, tuple(contributors), ("expected document absent from candidates",), 0.92)
    if expected and not expected.intersection(final_context):
        cause = Cause.RERANKER_REGRESSION if trace.get("reranker_removed_gold") else Cause.FUSION_OR_RANKING
        if trace.get("context_truncated"):
            cause = Cause.CONTEXT_TRUNCATION
        return TriageResult(cause, (), ("expected evidence was not in final context",), 0.87)
    if trace.get("judge_status") == "NOT_EXECUTED" and not trace.get("deterministic_failure"):
        return TriageResult(Cause.EVALUATOR_OR_JUDGE_FAILURE, (), ("only judge execution failed",), 1.0)
    if trace.get("prompt_injection_complied"):
        return TriageResult(Cause.PROMPT_INJECTION, (), ("model followed an untrusted instruction in retrieved content",), 1.0)
    if trace.get("citation_unsupported"):
        return TriageResult(Cause.CITATION_MISSING_OR_UNSUPPORTED, (), ("citation is not entailed by authorized retrieval",), 0.9)
    if trace.get("abstention_failure"):
        return TriageResult(Cause.ABSTENTION_FAILURE, (Cause.HALLUCINATION_OR_UNFAITHFUL,), ("answer was produced without sufficient evidence",), 0.9)
    if trace.get("answer_incomplete"):
        return TriageResult(Cause.GENERATION_INCOMPLETE, (), ("final context was present but required facts were missing",), 0.75)
    if trace.get("answer_incorrect"):
        return TriageResult(Cause.GENERATION_INCORRECT, (), ("final context was present but answer failed deterministic checks",), 0.75)
    return TriageResult(Cause.UNKNOWN_REQUIRES_REVIEW, (), ("no deterministic attribution rule matched",), 0.3)


def fingerprint(case: dict[str, Any], symptom: str) -> str:
    payload = {
        "question": " ".join(str(case.get("question", "")).casefold().split()),
        "access_context": case.get("access_context", {}),
        "expected_document_ids": sorted(case.get("expected_document_ids", case.get("expected_doc_ids", []))),
        "symptom": symptom,
    }
    return hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


ALLOWED_TRANSITIONS = {
    "NEW": {"AUTO_TRIAGED"},
    "AUTO_TRIAGED": {"CONFIRMED", "DISMISSED_WITH_REASON"},
    "CONFIRMED": {"FIX_IN_PROGRESS", "DISMISSED_WITH_REASON"},
    "FIX_IN_PROGRESS": {"FIXED"},
    "FIXED": {"VERIFIED"},
    "VERIFIED": {"PROMOTED_TO_REGRESSION"},
    "PROMOTED_TO_REGRESSION": set(),
    "DISMISSED_WITH_REASON": set(),
}


def can_transition(current: str, target: str) -> bool:
    return target in ALLOWED_TRANSITIONS.get(current, set())
