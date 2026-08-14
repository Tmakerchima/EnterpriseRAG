from __future__ import annotations

import hashlib
import json
from collections.abc import Iterable
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, ValidationError

SCHEMA_VERSION = "enterprise-rag.case.v1"
TRACE_SCHEMA_VERSION = "enterprise-rag.trace.v1"


class CaseModel(BaseModel):
    """Pydantic boundary model; dataclass conversion below keeps scoring dependency-light."""

    model_config = ConfigDict(extra="allow")
    schema_version: str = Field(default=SCHEMA_VERSION)
    case_id: str = Field(min_length=1)
    question: str = Field(min_length=1)
    language: str = "en"
    category: str = "general"
    difficulty: str = "medium"
    source: str = "synthetic"
    answerability: str = "ANSWERABLE"
    access_context: dict[str, Any] = Field(default_factory=dict)
    dataset_version: str = Field(default="local-smoke", min_length=1)


def _string_list(value: Any, field_name: str) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list) or any(not isinstance(item, str) or not item.strip() for item in value):
        raise ValueError(f"{field_name} must be a list of non-empty strings")
    return [item.strip() for item in value]


@dataclass(frozen=True)
class EvaluationCase:
    case_id: str
    question: str
    language: str = "en"
    category: str = "general"
    difficulty: str = "medium"
    source: str = "synthetic"
    answerability: str = "ANSWERABLE"
    gold_answer: str | None = None
    answer_facts: tuple[str, ...] = ()
    expected_document_ids: tuple[str, ...] = ()
    expected_chunk_ids: tuple[str, ...] = ()
    forbidden_document_ids: tuple[str, ...] = ()
    access_context: dict[str, Any] = field(default_factory=dict)
    expected_behavior: str | None = None
    rubric: dict[str, Any] = field(default_factory=dict)
    tags: tuple[str, ...] = ()
    dataset_version: str = "local-smoke"
    schema_version: str = SCHEMA_VERSION

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> EvaluationCase:
        if not isinstance(raw, dict):
            raise TypeError("case must be an object")
        try:
            CaseModel.model_validate(raw)
        except ValidationError as error:
            raise ValueError(f"case schema validation failed: {error}") from error
        if raw.get("schema_version", SCHEMA_VERSION) != SCHEMA_VERSION:
            raise ValueError(f"unsupported case schema: {raw.get('schema_version')}")
        case_id = str(raw.get("case_id", "")).strip()
        question = str(raw.get("question", "")).strip()
        if not case_id or not question:
            raise ValueError("case_id and question are required")
        answerability = str(raw.get("answerability", "ANSWERABLE")).upper()
        if answerability not in {"ANSWERABLE", "UNANSWERABLE", "AMBIGUOUS"}:
            raise ValueError("answerability must be ANSWERABLE, UNANSWERABLE or AMBIGUOUS")
        access_context = raw.get("access_context") or {}
        if not isinstance(access_context, dict):
            raise TypeError("access_context must be an object")
        return cls(
            case_id=case_id,
            question=question,
            language=str(raw.get("language", "en")),
            category=str(raw.get("category", "general")),
            difficulty=str(raw.get("difficulty", "medium")),
            source=str(raw.get("source", "synthetic")),
            answerability=answerability,
            gold_answer=raw.get("gold_answer"),
            answer_facts=tuple(_string_list(raw.get("answer_facts"), "answer_facts")),
            expected_document_ids=tuple(_string_list(raw.get("expected_document_ids", raw.get("expected_doc_ids")), "expected_document_ids")),
            expected_chunk_ids=tuple(_string_list(raw.get("expected_chunk_ids"), "expected_chunk_ids")),
            forbidden_document_ids=tuple(_string_list(raw.get("forbidden_document_ids"), "forbidden_document_ids")),
            access_context=access_context,
            expected_behavior=raw.get("expected_behavior"),
            rubric=raw.get("rubric") or {},
            tags=tuple(_string_list(raw.get("tags"), "tags")),
            dataset_version=str(raw.get("dataset_version", "local-smoke")),
            schema_version=SCHEMA_VERSION,
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "schema_version": self.schema_version,
            "case_id": self.case_id,
            "question": self.question,
            "language": self.language,
            "category": self.category,
            "difficulty": self.difficulty,
            "source": self.source,
            "answerability": self.answerability,
            "gold_answer": self.gold_answer,
            "answer_facts": list(self.answer_facts),
            "expected_document_ids": list(self.expected_document_ids),
            "expected_chunk_ids": list(self.expected_chunk_ids),
            "forbidden_document_ids": list(self.forbidden_document_ids),
            "access_context": self.access_context,
            "expected_behavior": self.expected_behavior,
            "rubric": self.rubric,
            "tags": list(self.tags),
            "dataset_version": self.dataset_version,
        }

    @property
    def has_retrieval_gold(self) -> bool:
        return bool(self.expected_document_ids) and self.answerability == "ANSWERABLE"


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as error:
                raise ValueError(f"{path}:{line_number}: invalid JSON: {error}") from error
            if not isinstance(value, dict):
                raise TypeError(f"{path}:{line_number}: expected JSON object")
            rows.append(value)
    return rows


def load_cases(path: Path) -> list[EvaluationCase]:
    cases = [EvaluationCase.from_dict(row) for row in load_jsonl(path)]
    ids = [case.case_id for case in cases]
    duplicates = sorted({case_id for case_id in ids if ids.count(case_id) > 1})
    if duplicates:
        raise ValueError(f"duplicate case_id: {', '.join(duplicates)}")
    return cases


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def canonical_hash(value: Any) -> str:
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(payload).hexdigest()
