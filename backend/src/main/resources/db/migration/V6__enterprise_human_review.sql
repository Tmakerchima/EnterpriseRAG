CREATE TABLE IF NOT EXISTS rag_human_reviews (
    review_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id TEXT NOT NULL UNIQUE,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    sources JSONB NOT NULL DEFAULT '[]'::jsonb,
    access_role TEXT,
    strategy TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'REVIEWED')),
    verdict TEXT CHECK (verdict IN ('RELIABLE', 'UNRELIABLE', 'INSUFFICIENT_EVIDENCE')),
    reviewer_comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_rag_human_reviews_status_created
    ON rag_human_reviews (status, created_at DESC);
