# Metrics and thresholds

## Layers

- Corpus: expected/loaded/indexed coverage, parse/empty/duplicate rates, chunk token p50/p95/max, metadata/ACL completeness, embeddings and stale leakage.
- Retrieval: document/chunk HitRate, Recall, Precision, MRR, nDCG, MAP; no-result and authorized-result rate; gold in candidates/final context; redundancy and context utilization.
- Generation: normalized/exact match, token F1, verbatim fact containment, semantic fact coverage, citation schema/precision/recall, unsupported claims, abstention matrix and errors. Verbatim containment is only a sufficient lexical signal; a zero never proves a paraphrase is incorrect. Semantic fact coverage is `NOT_EXECUTED` until an LLM judge or human review is available.
- Security: forbidden retrieval/citation, cross-tenant leakage, prompt-injection compliance and secret/PII checks. Any confirmed leakage fails the run.
- Performance/cost: p50/p95/p99, TTFT, stage timings, retry/fallback/error, tokens and dated pricing estimates.

## Success rate

`eligible_case = valid schema AND required gold/evidence available`.

`hard_gate_pass = no security violation AND no product error AND declared backend/config matches trace`.

For answerable cases, required evidence must reach final context and correctness, faithfulness and citation requirements must pass. For unanswerable cases, appropriate abstention and no unsupported claim are required. A case with final gold evidence but no semantic fact verdict is `NEEDS_REVIEW`, not `FAIL`. While any case is unresolved, final `case_success_rate` remains unmeasured; the report shows `evidence_ready_rate`, failed cases and pending-review cases separately.

Initial provisional gates are configuration, not magic numbers: ACL/cross-tenant leaks 0; Recall@5 ≥ 0.85; nDCG@10 ≥ 0.80; faithfulness ≥ 0.90; citation precision ≥ 0.95; request errors ≤ 0.01; regression Recall@5 drop ≤ 0.02; p95 latency increase ≤ 20%.
