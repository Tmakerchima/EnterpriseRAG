ALTER TABLE rag_human_reviews
    ADD COLUMN IF NOT EXISTS judge_status TEXT NOT NULL DEFAULT 'NOT_RUN'
        CHECK (judge_status IN ('NOT_RUN', 'COMPLETED', 'ERROR'));
ALTER TABLE rag_human_reviews
    ADD COLUMN IF NOT EXISTS judge_verdict TEXT
        CHECK (judge_verdict IN ('RELIABLE', 'UNRELIABLE', 'INSUFFICIENT_EVIDENCE'));
ALTER TABLE rag_human_reviews
    ADD COLUMN IF NOT EXISTS judge_score NUMERIC(5, 4);
ALTER TABLE rag_human_reviews
    ADD COLUMN IF NOT EXISTS judge_reason TEXT;
ALTER TABLE rag_human_reviews
    ADD COLUMN IF NOT EXISTS judge_model TEXT;
ALTER TABLE rag_human_reviews
    ADD COLUMN IF NOT EXISTS judged_at TIMESTAMPTZ;

COMMENT ON COLUMN rag_human_reviews.judge_verdict IS
    'Non-authoritative LLM-as-a-Judge suggestion. Human verdict remains the final label.';
