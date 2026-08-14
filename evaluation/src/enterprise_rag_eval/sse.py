from __future__ import annotations

import json
from collections.abc import Iterable
from dataclasses import dataclass


@dataclass(frozen=True)
class SseMessage:
    event: str
    data: str


def parse_sse(text: str) -> list[SseMessage]:
    messages: list[SseMessage] = []
    for block in text.replace("\r\n", "\n").split("\n\n"):
        if not block.strip() or block.lstrip().startswith(":"):
            continue
        event = "message"
        data: list[str] = []
        for line in block.split("\n"):
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                data.append(line[5:].lstrip())
        if data:
            messages.append(SseMessage(event, "\n".join(data)))
    return messages


def decode(message: SseMessage) -> tuple[str, dict[str, object] | str]:
    """Decode v1 typed events and the legacy marker payloads."""
    data = message.data
    legacy_markers = {"@@SOURCES@@": "sources", "@@METRICS@@": "metrics", "@@ERROR@@": "error"}
    for marker, event in legacy_markers.items():
        if data.startswith(marker):
            return event, json.loads(data[len(marker):])
    try:
        payload = json.loads(data)
    except json.JSONDecodeError:
        return message.event, data
    if isinstance(payload, dict):
        return str(payload.get("type", message.event)), payload.get("payload", payload)  # type: ignore[return-value]
    return message.event, payload


def collect_messages(chunks: Iterable[str]) -> tuple[str, list[dict[str, object]], dict[str, object], dict[str, object] | None]:
    answer: list[str] = []
    sources: list[dict[str, object]] = []
    metrics: dict[str, object] = {}
    error: dict[str, object] | None = None
    for message in parse_sse("".join(chunks)):
        event, payload = decode(message)
        if isinstance(payload, str):
            if event == "token":
                answer.append(payload)
            else:
                answer.append(payload)
        elif event == "token":
            answer.append(str(payload.get("text", "")))
        elif event == "sources":
            sources = list(payload.get("sources", []))
        elif event == "metrics":
            metrics = payload
        elif event == "error":
            error = payload
    return "".join(answer), sources, metrics, error
