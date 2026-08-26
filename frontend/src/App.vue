<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import ChunkPool from './components/ChunkPool.vue'
import EvaluationDashboard from './components/EvaluationDashboard.vue'

type Locale = 'zh' | 'en'
type Role = 'public' | 'engineering' | 'finance' | 'hr' | 'admin'
type Strategy = 'HYBRID' | 'VECTOR' | 'KEYWORD' | 'HYBRID_RERANK'
type View = 'query' | 'chunks' | 'evaluation'

interface Source {
  source_type: string
  source: string
  title: string
  document_id: string
  chunk_id: string
  chunk: string
  rank: number
  score: number
}

interface ChunkDetail {
  chunkId: string
  content: string
  tokenCount: number
  corpusVersion: string
  accessLevel: string
  department: string
}

interface Metrics {
  request_id: string
  trace_id?: string
  strategy: Strategy
  vector_ms: number
  fts_ms: number
  rrf_ms: number
  rerank_ms: number
  llm_ms: number
  total_ms: number
  candidate_count: number
  final_context_count: number
  fallback?: string | null
}

interface Health {
  status: string
  message?: string
  active_corpus_id?: string | null
  dataset_version?: string
  document_count?: number
  expected_documents?: number
  chunk_count?: number
  embedded_chunk_count?: number
  failed_count?: number
  vector_backend?: string
  embedding_model?: string
  embedding_dimension?: number
  chunker_version?: string
  source_distribution?: Record<string, number>
  vector_ready?: boolean
  fts_ready?: boolean
  benchmark?: { status?: string }
}

const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const locale = ref<Locale>('zh')
const initialView: View = window.location.hash === '#evaluation'
  ? 'evaluation'
  : window.location.hash === '#chunks' ? 'chunks' : 'query'
const activeView = ref<View>(initialView)
const question = ref('What are the default limits for multipart uploads?')
const role = ref<Role>('engineering')
const strategy = ref<Strategy>('HYBRID')
const answer = ref('')
const sources = ref<Source[]>([])
const metrics = ref<Metrics | null>(null)
const error = ref('')
const loading = ref(false)
const expandedSource = ref<string | null>(null)
const chunkDetails = ref<Record<string, ChunkDetail>>({})
const chunkDetailLoading = ref<string | null>(null)
const chunkDetailError = ref<Record<string, string>>({})
let accessVersion = 0
const health = ref<Health | null>(null)
const healthLoading = ref(false)
const feedbackSent = ref(false)
const reviewQueued = ref(false)
const reviewSubmitting = ref(false)
const feedbackError = ref('')
const answeredQuestion = ref('')

const copy = {
  zh: {
    brand: '企业知识库',
    language: '语言',
    queryView: '问答',
    chunksView: '切片池',
    evaluationView: '评测',
    kicker: 'ENTERPRISE KNOWLEDGE / RAG',
    title: '让每一个答案，\n都有证据。',
    lede: '面向企业内部知识的可审计 RAG 工作台。回答、来源、权限上下文、阶段耗时和反馈通过同一个 request_id 串联。',
    architecture: 'versioned corpus · ACL SQL filter · vector + lexical → fusion / rerank → grounded answer → async evaluation',
    queryKicker: 'QUERY CONSOLE',
    queryTitle: '向知识库提问',
    live: '在线',
    statusReady: '语料已就绪',
    statusLoading: '检查中…',
    statusUnavailable: '后端不可用',
    statusMigration: '等待数据库迁移',
    statusEmpty: '暂无 ACTIVE 语料',
    statusIngesting: '语料导入中',
    corpusKicker: 'CORPUS OVERVIEW',
    corpusTitle: '当前 ACTIVE V2 语料',
    documents: '文档',
    expected: '目标文档',
    chunks: '切片',
    embedded: '已向量化',
    failed: '失败',
    backend: '向量后端',
    model: 'Embedding 模型',
    benchmark: '评估',
    notMeasured: '尚未测量',
    queryUnavailable: '当前 ACTIVE 语料未就绪，查询暂不可用。',
    question: '问题',
    role: '角色',
    strategy: '检索策略',
    run: '开始检索',
    retrieving: '检索中…',
    aclNote: '先执行权限过滤，再进行排序',
    examples: '试试这些问题',
    responseKicker: 'GROUNDED RESPONSE',
    responseTitle: '回答',
    emptyTitle: '你的回答会出现在这里。',
    emptyNote: '来源与延迟指标会随流式回答返回。',
    evidenceKicker: 'EVIDENCE',
    evidenceTitle: '检索来源',
    evidenceEmpty: '运行一次查询，查看文档级证据。',
    fullChunk: '完整引用切片',
    loadingChunk: '正在读取完整切片…',
    chunkLoadFailed: '完整切片读取失败，当前显示的是 180 字摘要。',
    chunkMeta: '切片详情',
    observabilityKicker: 'OBSERVABILITY',
    observabilityTitle: '链路指标',
    metricsEmpty: '回答完成后显示检索和模型耗时。',
    candidates: '候选片段',
    contextChunks: '上下文片段',
    feedbackPrompt: '这个回答有帮助吗？',
    helpful: '有帮助',
    needsReview: '送人工审核',
    feedbackThanks: '谢谢，反馈已记录。',
    reviewQueued: '问题、回答与引用片段已进入评测页的人工审核队列。',
    reviewQueueFailed: '反馈已记录，但人工审核入队失败，请检查 V6 数据库迁移。',
    footer: 'EnterpriseRAG · 企业知识库检索与评测工作台',
    api: 'API',
    notConfigured: '未配置',
    missingApi: '未配置后端地址。请在 Vercel 项目中设置 VITE_API_BASE_URL。',
    networkError: '无法连接后端。请检查 Railway 服务是否正在运行。',
    backendDown: '后端暂时不可用（Railway 返回 502）。请先恢复后端服务。',
    timeout: '请求超时。后端可能正在冷启动或等待模型响应。',
    requestFailed: '请求失败',
    public: 'Public / 公开',
    engineering: 'Engineering / 工程',
    finance: 'Finance / 财务',
    hr: 'HR / 人力',
    admin: 'Admin / 管理员',
    publicDescription: '仅查看公开文档',
    engineeringDescription: '工程文档与公开文档',
    financeDescription: '财务文档与公开文档',
    hrDescription: '人力文档与公开文档',
    adminDescription: '查看当前租户内全部授权演示数据',
    hybrid: 'Hybrid RRF / 混合',
    vector: 'Vector / 向量',
    keyword: 'Keyword / 关键词',
    rerank: 'Hybrid + reranker / 混合重排',
  },
  en: {
    brand: 'Enterprise knowledge',
    language: 'Language',
    queryView: 'Query',
    chunksView: 'Chunks',
    evaluationView: 'Evaluation',
    kicker: 'ENTERPRISE KNOWLEDGE / RAG',
    title: 'Every answer\nwith evidence.',
    lede: 'An auditable EnterpriseRAG workspace. Answers, sources, authorization context, stage timings and feedback share one request_id.',
    architecture: 'versioned corpus · ACL SQL filter · vector + lexical → fusion / rerank → grounded answer → async evaluation',
    queryKicker: 'QUERY CONSOLE',
    queryTitle: 'Ask the knowledge base',
    live: 'live',
    statusReady: 'corpus ready',
    statusLoading: 'checking…',
    statusUnavailable: 'backend unavailable',
    statusMigration: 'migration required',
    statusEmpty: 'no ACTIVE corpus',
    statusIngesting: 'ingestion in progress',
    corpusKicker: 'CORPUS OVERVIEW',
    corpusTitle: 'Active V2 corpus',
    documents: 'documents',
    expected: 'expected',
    chunks: 'chunks',
    embedded: 'embedded',
    failed: 'failed',
    backend: 'vector backend',
    model: 'embedding model',
    benchmark: 'evaluation',
    notMeasured: 'not measured yet',
    queryUnavailable: 'The ACTIVE corpus is not ready; querying is paused.',
    question: 'Question',
    role: 'Role',
    strategy: 'Retrieval strategy',
    run: 'Run query',
    retrieving: 'Retrieving…',
    aclNote: 'Authorization is applied before ranking',
    examples: 'Try a question',
    responseKicker: 'GROUNDED RESPONSE',
    responseTitle: 'Answer',
    emptyTitle: 'Your grounded answer will appear here.',
    emptyNote: 'Sources and latency metrics arrive with the stream.',
    evidenceKicker: 'EVIDENCE',
    evidenceTitle: 'Retrieved sources',
    evidenceEmpty: 'Run a query to inspect document-level evidence.',
    fullChunk: 'Full cited chunk',
    loadingChunk: 'Loading the full chunk…',
    chunkLoadFailed: 'Unable to load the full chunk; showing the 180-character preview.',
    chunkMeta: 'Chunk details',
    observabilityKicker: 'OBSERVABILITY',
    observabilityTitle: 'Pipeline metrics',
    metricsEmpty: 'Retrieval and model timings appear after the answer completes.',
    candidates: 'candidates',
    contextChunks: 'context chunks',
    feedbackPrompt: 'Was this answer useful?',
    helpful: 'Helpful',
    needsReview: 'Send to human review',
    feedbackThanks: 'Thanks — feedback was recorded.',
    reviewQueued: 'The question, answer, and cited excerpts are now in the Evaluation human-review queue.',
    reviewQueueFailed: 'Feedback was recorded, but review queueing failed. Check the V6 database migration.',
    footer: 'EnterpriseRAG · enterprise retrieval and evaluation workspace',
    api: 'API',
    notConfigured: 'not configured',
    missingApi: 'The backend URL is not configured. Set VITE_API_BASE_URL in the Vercel project.',
    networkError: 'Cannot reach the backend. Check that the Railway service is running.',
    backendDown: 'The backend is unavailable (Railway returned 502). Restore the backend service first.',
    timeout: 'The request timed out. The backend may be cold-starting or waiting for the model.',
    requestFailed: 'Request failed',
    public: 'Public',
    engineering: 'Engineering',
    finance: 'Finance',
    hr: 'HR',
    admin: 'Admin',
    publicDescription: 'Public documents only',
    engineeringDescription: 'Engineering + public documents',
    financeDescription: 'Finance + public documents',
    hrDescription: 'HR + public documents',
    adminDescription: 'All authorized demo data in the selected tenant',
    hybrid: 'Hybrid RRF',
    vector: 'Vector only',
    keyword: 'Keyword only',
    rerank: 'Hybrid + reranker',
  },
} as const

type CopyKey = keyof typeof copy.en
const t = (key: CopyKey) => copy[locale.value][key]

const roles = computed(() => [
  { value: 'public' as Role, label: t('public'), description: t('publicDescription') },
  { value: 'engineering' as Role, label: t('engineering'), description: t('engineeringDescription') },
  { value: 'finance' as Role, label: t('finance'), description: t('financeDescription') },
  { value: 'hr' as Role, label: t('hr'), description: t('hrDescription') },
  { value: 'admin' as Role, label: t('admin'), description: t('adminDescription') },
])

const examples = computed(() => locale.value === 'zh'
  ? [
      'What are the default limits for multipart uploads?',
      'How should an EU region outage fail over, and what are the recovery targets?',
      'What is the recommended two-stage process for rotating signing credentials?',
    ]
  : [
      'What are the default limits for multipart uploads?',
      'How should an EU region outage fail over, and what are the recovery targets?',
      'What is the recommended two-stage process for rotating signing credentials?',
    ])

const selectedRole = computed(() => roles.value.find((item) => item.value === role.value))
const statusKey = computed(() => {
  if (healthLoading.value) return 'statusLoading'
  if (!health.value) return 'statusUnavailable'
  if (health.value.status === 'READY' || health.value.status === 'DEGRADED') return 'statusReady'
  if (health.value.status === 'MIGRATION_REQUIRED') return 'statusMigration'
  if (['STAGING', 'EMBEDDING', 'INGESTING', 'INDEXING', 'VALIDATING'].includes(health.value.status)) return 'statusIngesting'
  return 'statusEmpty'
})
const queryReady = computed(() => health.value?.status === 'READY' || health.value?.status === 'DEGRADED')
const statusTone = computed(() => queryReady.value ? 'ready' : 'blocked')
const statusLabel = computed(() => t(statusKey.value as CopyKey))

function switchView(view: View) {
  activeView.value = view
  const location = view === 'query' ? window.location.pathname : `#${view}`
  window.history.replaceState(null, '', location)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function toggleSource(source: Source) {
  if (expandedSource.value === source.chunk_id) {
    expandedSource.value = null
    return
  }
  expandedSource.value = source.chunk_id
  if (chunkDetails.value[source.chunk_id] || !apiBase) return

  chunkDetailLoading.value = source.chunk_id
  const requestedAccessVersion = accessVersion
  delete chunkDetailError.value[source.chunk_id]
  try {
    const params = new URLSearchParams({ role: role.value, tenantId: 'default' })
    const response = await fetch(`${apiBase}/api/enterprise/chunks/${encodeURIComponent(source.chunk_id)}?${params}`, {
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const payload = await response.json() as ChunkDetail
    if (requestedAccessVersion === accessVersion) chunkDetails.value[source.chunk_id] = payload
  } catch {
    if (requestedAccessVersion === accessVersion) chunkDetailError.value[source.chunk_id] = t('chunkLoadFailed')
  } finally {
    if (requestedAccessVersion === accessVersion && chunkDetailLoading.value === source.chunk_id) chunkDetailLoading.value = null
  }
}

async function refreshHealth() {
  if (!apiBase) return
  healthLoading.value = true
  try {
    const response = await fetch(`${apiBase}/api/enterprise/health`, { headers: { Accept: 'application/json' } })
    const payload = await response.json() as Health
    health.value = payload
  } catch {
    health.value = null
  } finally {
    healthLoading.value = false
  }
}

onMounted(refreshHealth)

watch(role, () => {
  // Demo roles simulate different identities. Never keep evidence from the
  // previous ACL context visible after the role changes.
  accessVersion += 1
  answer.value = ''
  sources.value = []
  metrics.value = null
  expandedSource.value = null
  chunkDetails.value = {}
  chunkDetailError.value = {}
  chunkDetailLoading.value = null
})

async function ask(questionOverride?: string) {
  if (questionOverride) question.value = questionOverride
  const trimmed = question.value.trim()
  if (!trimmed || loading.value) return

  if (!apiBase) {
    error.value = t('missingApi')
    return
  }
  if (!queryReady.value) {
    error.value = t('queryUnavailable')
    return
  }

  loading.value = true
  answer.value = ''
  sources.value = []
  metrics.value = null
  feedbackSent.value = false
  reviewQueued.value = false
  reviewSubmitting.value = false
  feedbackError.value = ''
  answeredQuestion.value = trimmed
  error.value = ''
  expandedSource.value = null
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 60_000)

  try {
    const response = await fetch(`${apiBase}/api/enterprise/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ question: trimmed, role: role.value, strategy: strategy.value }),
      signal: controller.signal,
    })
    if (!response.ok) {
      if (response.status === 502 || response.status === 503) {
        let message = 'BACKEND_UNAVAILABLE'
        try {
          const payload = await response.clone().json() as { message?: string }
          if (payload.message) message = payload.message
        } catch { /* keep localized fallback */ }
        throw new Error(message)
      }
      throw new Error(`${t('requestFailed')} (${response.status})`)
    }
    if (!response.body) throw new Error('Streaming response is unavailable')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const result = await reader.read()
      buffer += decoder.decode(result.value || new Uint8Array(), { stream: !result.done })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() || ''
      events.forEach(handleEvent)
      if (result.done) {
        if (buffer.trim()) handleEvent(buffer)
        break
      }
    }
  } catch (requestError) {
    if (requestError instanceof DOMException && requestError.name === 'AbortError') {
      error.value = t('timeout')
    } else if (requestError instanceof TypeError) {
      error.value = t('networkError')
    } else if (requestError instanceof Error && (requestError.message === 'BACKEND_UNAVAILABLE' || requestError.message.includes('migration'))) {
      error.value = t('backendDown')
    } else {
      error.value = requestError instanceof Error ? requestError.message : t('requestFailed')
    }
  } finally {
    window.clearTimeout(timeout)
    loading.value = false
  }
}

function handleEvent(event: string) {
  const eventType = event.split(/\r?\n/).find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const rawData = event
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5))
    .join('\n')
  // SSE permits one optional separator space after `data:`. Remove only that
  // protocol space for parsing; keep any remaining leading whitespace so
  // legacy streamed answer tokens retain their word boundaries.
  const data = rawData.startsWith(' ') ? rawData.slice(1) : rawData
  if (!data) return

  try {
    if (data.startsWith('@@SOURCES@@')) {
      const frame = JSON.parse(data.slice('@@SOURCES@@'.length)) as { sources: Source[] }
      sources.value = frame.sources || []
    } else if (data.startsWith('@@METRICS@@')) {
      metrics.value = JSON.parse(data.slice('@@METRICS@@'.length)) as Metrics
    } else if (data.startsWith('@@ERROR@@')) {
      const frame = JSON.parse(data.slice('@@ERROR@@'.length)) as { message?: string }
      error.value = frame.message || t('requestFailed')
    } else {
      const envelope = JSON.parse(data) as { type?: string, payload?: Record<string, unknown> }
      const type = envelope.type || eventType
      const payload = envelope.payload || envelope as unknown as Record<string, unknown>
      if (type === 'token') answer.value += String(payload.text || '')
      else if (type === 'sources') sources.value = (payload.sources || []) as Source[]
      else if (type === 'metrics') metrics.value = payload as unknown as Metrics
      else if (type === 'error') error.value = String(payload.message || t('requestFailed'))
    }
  } catch {
    // Older deployments emit plain `data:<text>` SSE frames without an
    // `event: token` line. Keep accepting the structured envelope above, but
    // fall back to appending unstructured message/token frames so answers
    // generated by the currently deployed backend are not silently dropped.
    if (eventType === 'token' || eventType === 'message') answer.value += rawData
  }
}

async function sendFeedback(rating: 'positive' | 'negative') {
  if (!metrics.value?.request_id || feedbackSent.value || !apiBase) return
  feedbackError.value = ''
  try {
    const response = await fetch(`${apiBase}/api/enterprise/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requestId: metrics.value.request_id, rating }),
    })
    if (response.ok) feedbackSent.value = true
  } catch { /* feedback is additive and must not affect the answer */ }
  if (rating === 'negative') await queueForHumanReview()
}

async function queueForHumanReview() {
  if (!metrics.value?.request_id || !answeredQuestion.value || !answer.value || !apiBase || reviewSubmitting.value) return
  reviewSubmitting.value = true
  try {
    const response = await fetch(`${apiBase}/api/enterprise/reviews`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        requestId: metrics.value.request_id,
        question: answeredQuestion.value,
        answer: answer.value,
        accessRole: role.value,
        strategy: strategy.value,
        sources: sources.value.map((item) => ({
          rank: item.rank,
          title: item.title,
          document_id: item.document_id,
          chunk_id: item.chunk_id,
          chunk: item.chunk,
        })),
      }),
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    reviewQueued.value = true
    feedbackSent.value = true
  } catch {
    feedbackError.value = t('reviewQueueFailed')
  } finally {
    reviewSubmitting.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <a class="brand" href=".">
        <span class="brand-mark">ER</span>
        <span>
          <strong>EnterpriseRAG</strong>
          <small>{{ t('brand') }}</small>
        </span>
      </a>
      <div class="topbar-actions">
        <nav class="view-switch" aria-label="Workspace view">
          <button type="button" :class="{ active: activeView === 'query' }" @click="switchView('query')">{{ t('queryView') }}</button>
          <button type="button" :class="{ active: activeView === 'chunks' }" @click="switchView('chunks')">{{ t('chunksView') }}</button>
          <button type="button" :class="{ active: activeView === 'evaluation' }" @click="switchView('evaluation')">{{ t('evaluationView') }}</button>
        </nav>
        <span class="status" :class="statusTone"><i /> {{ health?.status ? `${health.status} · ${statusLabel}` : statusLabel }}</span>
        <label class="language-control">
          <span>{{ t('language') }}</span>
          <select v-model="locale" aria-label="Language">
            <option value="zh">中文</option>
            <option value="en">English</option>
          </select>
        </label>
      </div>
    </header>

    <main class="page-content">
      <section class="hero">
        <p class="eyebrow">{{ t('kicker') }}</p>
        <h1>{{ t('title') }}</h1>
        <p class="lede">{{ t('lede') }}</p>
        <p class="architecture-line">{{ t('architecture') }}</p>
      </section>

      <template v-if="activeView === 'query'">
      <section class="corpus-card card">
        <div class="section-heading compact">
          <div>
            <p class="eyebrow">{{ t('corpusKicker') }}</p>
            <h2>{{ t('corpusTitle') }}</h2>
          </div>
          <span class="request-id">{{ health?.dataset_version || '—' }}</span>
        </div>
        <div v-if="health" class="corpus-grid">
          <div><span>{{ t('documents') }}</span><strong>{{ health.document_count ?? 0 }}</strong><small>/ {{ health.expected_documents ?? 0 }}</small></div>
          <div><span>{{ t('chunks') }}</span><strong>{{ health.chunk_count ?? 0 }}</strong></div>
          <div><span>{{ t('embedded') }}</span><strong>{{ health.embedded_chunk_count ?? 0 }}</strong></div>
          <div><span>{{ t('failed') }}</span><strong>{{ health.failed_count ?? 0 }}</strong></div>
        </div>
        <div v-else class="metric-placeholder">{{ t('statusUnavailable') }}</div>
        <div v-if="health" class="corpus-footer">
          <span>{{ t('backend') }}: {{ health.vector_backend || '—' }}</span>
          <span>{{ t('model') }}: {{ health.embedding_model || '—' }}</span>
          <span>Chunker: {{ health.chunker_version || 'V2' }}</span>
          <span>{{ t('benchmark') }}: {{ health.benchmark?.status === 'NOT_MEASURED_YET' ? t('notMeasured') : health.benchmark?.status || t('notMeasured') }}</span>
        </div>
      </section>

      <section class="query-card card">
        <div class="section-heading">
          <div>
            <p class="eyebrow">{{ t('queryKicker') }}</p>
            <h2>{{ t('queryTitle') }}</h2>
          </div>
          <span class="live-pill" :class="statusTone"><i /> {{ statusLabel }}</span>
        </div>
        <form @submit.prevent="ask()">
          <label for="question">{{ t('question') }}</label>
          <textarea id="question" v-model="question" rows="4" :disabled="loading" />
          <div class="form-row">
            <label>
              {{ t('role') }}
              <select v-model="role" :disabled="loading">
                <option v-for="item in roles" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label>
              {{ t('strategy') }}
              <select v-model="strategy" :disabled="loading">
                <option value="HYBRID">{{ t('hybrid') }}</option>
                <option value="VECTOR">{{ t('vector') }}</option>
                <option value="KEYWORD">{{ t('keyword') }}</option>
                <option value="HYBRID_RERANK">{{ t('rerank') }}</option>
              </select>
            </label>
            <button type="submit" :disabled="loading || !question.trim() || !queryReady">
              <span v-if="loading" class="spinner" />
              {{ loading ? t('retrieving') : t('run') }}
            </button>
          </div>
          <p class="selection-note">{{ selectedRole?.description }} · {{ t('aclNote') }}</p>
          <p v-if="!queryReady" class="selection-note warning">{{ health?.message || t('queryUnavailable') }}</p>
        </form>
        <div class="examples">
          <span>{{ t('examples') }}</span>
          <button v-for="example in examples" :key="example" type="button" @click="ask(example)">
            {{ example }}
          </button>
        </div>
        <div class="api-note">{{ t('api') }} · {{ apiBase || t('notConfigured') }}</div>
      </section>

      <section class="answer-card card">
        <div class="section-heading compact">
          <div>
            <p class="eyebrow">{{ t('responseKicker') }}</p>
            <h2>{{ t('responseTitle') }}</h2>
          </div>
          <span v-if="metrics" class="request-id">{{ metrics.request_id.slice(0, 8) }}</span>
        </div>
        <div v-if="error" class="error-message" role="alert">{{ error }}</div>
        <div v-else-if="answer" class="answer-copy">{{ answer }}</div>
        <div v-else class="empty-state">
          <span class="empty-icon">✳</span>
          <p>{{ t('emptyTitle') }}</p>
          <small>{{ t('emptyNote') }}</small>
        </div>
        <div v-if="metrics && !feedbackSent" class="feedback-row" aria-label="Answer feedback">
          <span>{{ t('feedbackPrompt') }}</span>
          <button type="button" @click="sendFeedback('positive')">{{ t('helpful') }}</button>
          <button type="button" :disabled="reviewSubmitting" @click="sendFeedback('negative')">{{ reviewSubmitting ? '…' : t('needsReview') }}</button>
        </div>
        <div v-else-if="feedbackSent" class="feedback-row muted">{{ reviewQueued ? t('reviewQueued') : t('feedbackThanks') }}</div>
        <div v-if="feedbackError" class="feedback-row error-message">{{ feedbackError }}</div>
      </section>

      <section class="results-grid">
        <div class="card sources-panel">
          <div class="section-heading compact">
            <div>
              <p class="eyebrow">{{ t('evidenceKicker') }}</p>
              <h2>{{ t('evidenceTitle') }} <span>{{ sources.length }}</span></h2>
            </div>
          </div>
          <div v-if="sources.length" class="source-list">
            <article v-for="source in sources" :key="source.chunk_id" class="source-item">
              <button type="button" class="source-title" @click="toggleSource(source)">
                <span class="source-type">{{ source.source_type }}</span>
                <strong>{{ source.title || source.document_id }}</strong>
                <span class="source-rank">#{{ source.rank }} · {{ source.score.toFixed(3) }}</span>
              </button>
              <div v-if="expandedSource === source.chunk_id" class="source-content">
                <strong>{{ t('fullChunk') }}</strong>
                <p v-if="chunkDetailLoading === source.chunk_id">{{ t('loadingChunk') }}</p>
                <p v-else>{{ chunkDetails[source.chunk_id]?.content || source.chunk }}</p>
                <small v-if="chunkDetailError[source.chunk_id]">{{ chunkDetailError[source.chunk_id] }}</small>
                <small v-else-if="chunkDetails[source.chunk_id]">
                  {{ t('chunkMeta') }} · {{ source.chunk_id }} · {{ chunkDetails[source.chunk_id].tokenCount }} tokens · {{ chunkDetails[source.chunk_id].corpusVersion }}
                </small>
              </div>
            </article>
          </div>
          <p v-else class="muted">{{ t('evidenceEmpty') }}</p>
        </div>

        <div class="card metrics-panel">
          <div class="section-heading compact">
            <div>
              <p class="eyebrow">{{ t('observabilityKicker') }}</p>
              <h2>{{ t('observabilityTitle') }}</h2>
            </div>
          </div>
          <div v-if="metrics" class="metric-grid">
            <div><span>Vector</span><strong>{{ metrics.vector_ms }}<small>ms</small></strong></div>
            <div><span>FTS</span><strong>{{ metrics.fts_ms }}<small>ms</small></strong></div>
            <div><span>RRF</span><strong>{{ metrics.rrf_ms }}<small>ms</small></strong></div>
            <div><span>Rerank</span><strong>{{ metrics.rerank_ms }}<small>ms</small></strong></div>
            <div><span>LLM</span><strong>{{ metrics.llm_ms }}<small>ms</small></strong></div>
            <div class="total"><span>Total</span><strong>{{ metrics.total_ms }}<small>ms</small></strong></div>
          </div>
          <div v-else class="metric-placeholder">{{ t('metricsEmpty') }}</div>
          <div v-if="metrics" class="metric-footer">
            <span>{{ metrics.candidate_count }} {{ t('candidates') }}</span>
            <span>{{ metrics.final_context_count }} {{ t('contextChunks') }}</span>
            <span>{{ metrics.strategy }}</span>
            <span v-if="metrics.fallback">fallback: {{ metrics.fallback }}</span>
          </div>
        </div>
      </section>
      </template>

      <ChunkPool v-else-if="activeView === 'chunks'" v-model:role="role" :api-base="apiBase" :locale="locale" :ready="queryReady" />
      <EvaluationDashboard v-else :locale="locale" :api-base="apiBase" />
    </main>

    <footer>
      <span>{{ t('footer') }}</span>
      <span>Vue 3 · Spring AI · PostgreSQL · PGVector</span>
    </footer>
  </div>
</template>
