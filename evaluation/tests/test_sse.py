from enterprise_rag_eval.sse import collect_messages, parse_sse


def test_typed_sse_unicode_and_legacy_marker() -> None:
    stream = (
        'event: sources\ndata: {"schema_version":"enterprise-rag.sse.v1","type":"sources","payload":{"sources":[{"document_id":"文档-1"}]}}\n\n'
        'event: token\ndata: {"type":"token","payload":{"text":"你好"}}\n\n'
        'event: metrics\ndata: @@METRICS@@{"request_id":"r","total_ms":3}\n\n'
    )
    assert len(parse_sse(stream)) == 3
    answer, sources, metrics, error = collect_messages([stream[:30], stream[30:]])
    assert answer == "你好"
    assert sources[0]["document_id"] == "文档-1"
    assert metrics["total_ms"] == 3
    assert error is None
