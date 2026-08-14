from enterprise_rag_eval.triage import Cause, can_transition, fingerprint, triage_case


def test_triage_priority_and_fingerprint() -> None:
    result = triage_case({"expected_document_ids": ["gold"]}, {"coverage_status": "fully_supported", "candidate_document_ids": []})
    assert result.primary_cause == Cause.RETRIEVAL_NO_RECALL
    assert fingerprint({"question": " Q ", "expected_document_ids": ["gold"]}, result.primary_cause) == fingerprint(
        {"question": "q", "expected_document_ids": ["gold"]}, result.primary_cause
    )


def test_bad_case_state_machine() -> None:
    assert can_transition("NEW", "AUTO_TRIAGED")
    assert not can_transition("NEW", "VERIFIED")
