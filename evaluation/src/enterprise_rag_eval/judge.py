from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, dataclass
from typing import Any, Protocol


@dataclass(frozen=True)
class JudgeResult:
    status: str
    metric: str
    score: float | None
    threshold: float | None
    passed: bool | None
    reason: str | None
    judge_model: str | None
    framework_version: str | None
    rubric_version: str
    cache_key: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class JudgeAdapter(Protocol):
    def score(self, *, question: str, answer: str, contexts: list[str], expected: str | None, case_id: str) -> JudgeResult:
        ...


class NotExecutedJudgeAdapter:
    def __init__(self, reason: str = "judge dependency or key is not configured", rubric_version: str = "rag.v1") -> None:
        self.reason = reason
        self.rubric_version = rubric_version

    def score(self, *, question: str, answer: str, contexts: list[str], expected: str | None, case_id: str) -> JudgeResult:
        key = cache_key(question, answer, contexts, self.rubric_version, None, {"case_id": case_id})
        return JudgeResult("NOT_EXECUTED", "faithfulness", None, None, None, self.reason, None, None, self.rubric_version, key)


class DeepEvalJudgeAdapter:
    """Optional adapter; importing DeepEval is intentionally delayed until this class is used."""

    def __init__(self, model: str | None = None, threshold: float = 0.9, rubric_version: str = "rag.v1") -> None:
        self.model = model
        self.threshold = threshold
        self.rubric_version = rubric_version

    def score(self, *, question: str, answer: str, contexts: list[str], expected: str | None, case_id: str) -> JudgeResult:
        key = cache_key(question, answer, contexts, self.rubric_version, self.model, {"threshold": self.threshold, "case_id": case_id})
        try:
            import deepeval  # type: ignore
            from deepeval.metrics import FaithfulnessMetric  # type: ignore
            from deepeval.test_case import LLMTestCase  # type: ignore
        except Exception as error:  # noqa: BLE001 - optional provider compatibility boundary
            return JudgeResult("NOT_EXECUTED", "faithfulness", None, self.threshold, None, f"DeepEval unavailable: {error}", self.model, None, self.rubric_version, key)
        try:
            metric_kwargs = {"threshold": self.threshold, "include_reason": True}
            if self.model:
                metric_kwargs["model"] = self.model
            metric = FaithfulnessMetric(**metric_kwargs)
            test_case = LLMTestCase(input=question, actual_output=answer, retrieval_context=contexts)
            metric.measure(test_case)
            score = float(metric.score) if metric.score is not None else None
            return JudgeResult("MEASURED", "faithfulness", score, self.threshold, score is not None and score >= self.threshold,
                               metric.reason, self.model, getattr(deepeval, "__version__", None), self.rubric_version, key)
        except Exception as error:  # noqa: BLE001 - provider failures become NOT_EXECUTED
            return JudgeResult("NOT_EXECUTED", "faithfulness", None, self.threshold, None, f"DeepEval call failed: {error}", self.model,
                               getattr(deepeval, "__version__", None), self.rubric_version, key)


def cache_key(question: str, answer: str, contexts: list[str], rubric_version: str, judge_model: str | None, parameters: dict[str, Any]) -> str:
    payload = json.dumps({"question": question, "answer": answer, "contexts": contexts, "rubric_version": rubric_version,
                          "judge_model": judge_model, "parameters": parameters}, ensure_ascii=False, sort_keys=True).encode()
    return hashlib.sha256(payload).hexdigest()
