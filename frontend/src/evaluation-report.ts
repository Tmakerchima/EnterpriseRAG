export interface MetricValue {
  status?: string
  value?: number | null
  count?: number
  ci95?: [number, number] | null
  reason?: string | null
}

export interface CaseSuccessRow {
  case_id: string
  success: boolean | null
  outcome?: 'PASS' | 'FAIL' | 'NEEDS_REVIEW'
  reasons: string[]
  question?: string | null
  answer?: string | null
  reference_answer?: string | null
  expected_document_ids?: string[]
  retrieved_document_ids?: string[]
  final_document_ids?: string[]
  judge_score?: number | null
  judge_reason?: string | null
  judge_model?: string | null
  review_method?: string | null
  sources?: Array<{
    rank?: number
    title?: string | null
    document_id?: string | null
    chunk_id?: string | null
    content?: string | null
  }>
}

export interface EvaluationReport {
  run_validity?: string
  synthetic_fixture?: boolean
  manifest?: {
    run_id?: string
    profile?: string
    dataset_version?: string[] | string
    strategy?: string
    created_at?: string
    status?: string
    scope?: {
      document_count?: number
      question_count?: number
      fully_supported?: number
      partially_supported?: number
      unsupported?: number
      coverage_policy?: string
    }
    target?: {
      corpus_id?: string
      dataset_name?: string
      dataset_version?: string
      document_count?: number
      chunk_count?: number
      source_distribution?: Record<string, number>
      lexical_backend?: string
      vector_backend?: string
      status?: string
    }
  }
  metrics?: Record<string, unknown>
  layers?: {
    retrieval?: {
      status?: string
      eligible_cases?: number
      excluded?: Record<string, number>
      ks?: Record<string, Record<string, MetricValue>>
    }
    generation?: {
      status?: string
      eligible_cases?: number
      exact_match?: MetricValue
      token_f1?: MetricValue
      fact_coverage?: MetricValue
      verbatim_fact_coverage?: MetricValue
      citation_schema_valid?: MetricValue
      case_success?: {
        status?: string
        eligible_cases?: number
        successful_cases?: number
        failed_cases?: number
        pending_review_cases?: number
        evidence_ready_cases?: number
        evidence_ready_rate?: number | null
        excluded_cases?: number
        success_rate?: number | null
        ci95?: [number, number] | null
        cases?: CaseSuccessRow[]
      }
      judge?: {
        status?: string
        primary_framework?: string | null
        total_cases?: number
        measured_cases?: number
        not_executed_cases?: number
        passed_cases?: number
        failed_cases?: number
        mean_score?: number | null
        threshold?: number | null
        judge_models?: string[]
        results?: Array<{
          case_id?: string
          status?: string
          metric?: string
          score?: number | null
          threshold?: number | null
          passed?: boolean | null
          reason?: string | null
          judge_model?: string | null
        }>
      }
    }
    corpus?: Record<string, unknown> & { status?: string }
    security?: {
      status?: string
      cases?: number
      observed_requests?: number
      acl_negative_cases?: number
      prompt_injection_cases?: number
      forbidden_retrieval_count?: number
      prompt_injection_compliance_count?: number
      hard_gate?: string
    }
    performance?: {
      status?: string
      request_count?: number
      p50_ms?: number
      p95_ms?: number
      p99_ms?: number
      error_rate?: number
      fallback_rate?: number
    }
  }
}

export function isEvaluationReport(value: unknown): value is EvaluationReport {
  if (!value || typeof value !== 'object') return false
  const candidate = value as EvaluationReport
  return typeof candidate.run_validity === 'string' && !!candidate.manifest && !!candidate.layers
}
