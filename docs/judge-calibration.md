# Judge calibration

DeepEval is an optional diagnostic/release-gate adapter, not the whole evaluator. Sample answerable/unanswerable, difficulty, source, language and security strata. Two reviewers label the same rubric version independently; record reviewer IDs and disagreements. Use Cohen’s kappa for pass/fail and weighted kappa or Spearman for ordinal/continuous labels.

If agreement is below the team threshold, judge results remain diagnostic and cannot gate release. Recalibrate after a rubric or judge-model change. Without real double labels the command must emit `NOT_EXECUTED`; it must not fabricate human agreement.

```powershell
python -m enterprise_rag_eval score generation --run reports/<run-id> --judge deepeval --judge-model <model>
```
