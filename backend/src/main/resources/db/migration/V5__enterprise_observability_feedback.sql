CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS rag_interactions (
    request_id TEXT PRIMARY KEY,
    trace_id TEXT,
    tenant_id TEXT,
    role TEXT NOT NULL,
    strategy TEXT NOT NULL,
    lexical_backend TEXT,
    source_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    stage_timings JSONB NOT NULL DEFAULT '{}'::jsonb,
    tokens JSONB NOT NULL DEFAULT '{}'::jsonb,
    outcome TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_interactions_created_at ON rag_interactions (created_at DESC);

CREATE TABLE IF NOT EXISTS rag_feedback (
    feedback_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id TEXT NOT NULL,
    rating TEXT NOT NULL CHECK (rating IN ('positive', 'negative')),
    reason TEXT CHECK (reason IN ('wrong_answer', 'missing_evidence', 'unsafe', 'acl', 'latency', 'other')),
    redacted_comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_feedback_request_id ON rag_feedback (request_id);

CREATE TABLE IF NOT EXISTS rag_evaluation_queue (
    queue_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id TEXT NOT NULL,
    sampling_reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_evaluation_queue_pending ON rag_evaluation_queue (status, next_retry_at);

CREATE TABLE IF NOT EXISTS rag_bad_cases (
    bad_case_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fingerprint TEXT NOT NULL UNIQUE,
    taxonomy TEXT NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    owner TEXT,
    status TEXT NOT NULL DEFAULT 'NEW',
    linked_regression_case TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    fixed_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ
);
