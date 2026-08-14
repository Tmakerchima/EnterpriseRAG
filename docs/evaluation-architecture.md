# Evaluation architecture

The pipeline is intentionally composable:

```text
versioned cases + corpus manifest
  -> collect traces
  -> deterministic corpus/retrieval/generation/security/performance scores
  -> optional asynchronous DeepEval judge
  -> paired baseline/candidate compare
  -> deterministic bad-case triage
  -> human confirmation and regression promotion
```

Cases use `enterprise-rag.case.v1`; traces and runs carry schema/version, dataset and config hashes. A case without gold documents is excluded from document Recall/MRR/nDCG but can still evaluate abstention, ACL, safety or human rubric. A partially supported corpus is reported as coverage, not retriever failure.

The DeepEval adapter is lazy and optional. Its compatibility spike follows official `LLMTestCase` + `FaithfulnessMetric` usage and records framework/model/rubric/cache metadata. It does not block the online request path.
