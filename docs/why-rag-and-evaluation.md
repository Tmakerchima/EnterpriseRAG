# Why RAG and why evaluation

RAG is useful when an answer needs current, private, authorized and traceable evidence. It lets a team update the corpus and ACL metadata without retraining the model. It is not automatically better: small stable corpora that fit in context, translation, creative writing and knowledge the model already handles reliably may not need retrieval.

The product contract is evidence, not a vague “smarter model”. A useful failure decomposition is corpus/ingestion, retrieval/ranking, context construction, generation, safety and system/business. Evaluation therefore reports layers and attribution instead of averaging one RAG score.

Thresholds are provisional until calibrated against business risk, human labels, baseline variance, user SLOs and regression tolerance. Online empty retrieval, fallback and negative feedback are proxy signals, not correctness ground truth.
