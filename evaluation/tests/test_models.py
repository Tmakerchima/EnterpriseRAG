from enterprise_rag_eval.models import EvaluationCase


def test_official_enterprise_rag_bench_question_aliases() -> None:
    case = EvaluationCase.from_dict({
        "question_id": "qst_0001",
        "question_type": "basic",
        "source_types": ["github"],
        "question": "What is the limit?",
        "expected_doc_ids": ["dsid_gold"],
        "gold_answer": "10 MiB",
        "answer_facts": ["The limit is 10 MiB."],
    })

    assert case.case_id == "qst_0001"
    assert case.category == "basic"
    assert case.source == "enterprise-rag-bench"
    assert case.dataset_version == "EnterpriseRAG-Bench-v1.0.0"
    assert case.tags == ("github",)
    assert case.expected_document_ids == ("dsid_gold",)
