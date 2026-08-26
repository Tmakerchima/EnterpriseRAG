# Judge calibration

DeepEval is an optional LLM-as-Judge diagnostic/release-gate adapter, not the whole evaluator. The current rubric measures **faithfulness to retrieved context**; it does not by itself prove completeness, retrieval coverage, or permission safety. Sample answerable/unanswerable, difficulty, source, language and security strata. Two reviewers label the same rubric version independently; record reviewer IDs and disagreements. Use Cohen’s kappa for pass/fail and weighted kappa or Spearman for ordinal/continuous labels.

If agreement is below the team threshold, judge results remain diagnostic and cannot gate release. Recalibrate after a rubric or judge-model change. Without real double labels the command must emit `NOT_EXECUTED`; it must not fabricate human agreement.

```powershell
python -m pip install -e ".[judge]"
$env:OPENAI_API_KEY = "<key>"
$env:EVALUATION_JUDGE = "deepeval"
$env:EVALUATION_JUDGE_MODEL = "gpt-5-mini"
python -m enterprise_rag_eval score generation --run reports/<run-id>
python -m enterprise_rag_eval report --run reports/<run-id> --frontend-out ../frontend/public/evaluation/latest.json
```

The generated report records the judge framework, exact model, threshold, per-case score/reason, measured count, and pass/fail count. Missing dependencies, keys, or provider failures remain `NOT_EXECUTED`; they are never converted to a zero score.
