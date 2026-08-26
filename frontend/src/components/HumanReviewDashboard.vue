<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

const props = defineProps<{ locale: 'zh' | 'en'; apiBase: string }>()

interface ReviewItem {
  reviewId: string
  requestId: string
  question: string
  answer: string
  sources: Array<Record<string, unknown>>
  accessRole?: string
  strategy?: string
  status: 'PENDING' | 'REVIEWED'
  verdict?: string
  reviewerComment?: string
  judgeStatus?: 'NOT_RUN' | 'COMPLETED' | 'ERROR'
  judgeVerdict?: string
  judgeScore?: number
  judgeReason?: string
  judgeModel?: string
  judgedAt?: string
  createdAt: string
  reviewedAt?: string
}

const loading = ref(false)
const error = ref('')
const status = ref<'PENDING' | 'REVIEWED' | ''>('PENDING')
const items = ref<ReviewItem[]>([])
const comments = ref<Record<string, string>>({})
const judging = ref<string | null>(null)
const saving = ref<string | null>(null)

const copy = computed(() => props.locale === 'zh' ? {
  kicker: 'HUMAN REVIEW / 人工审核',
  title: '把模型建议变成人的结论',
  lede: '线上问答会保存问题、系统实际回答和引用片段。LLM Judge 先给出可解释的预审建议，最终标签必须由人工确认。',
  workflow: '线上问答 → 保存问题 / 实际回答 / 引用证据 → LLM 预审建议 → 人工最终结论 → 沉淀评测数据',
  privacy: '当前为单用户工作台，读取和审批不再要求管理员令牌。部署为多用户系统前，应重新接入登录身份和审核权限。',
  pending: '待审核', reviewed: '已审核', all: '全部', refresh: '刷新队列', empty: '当前筛选条件下没有审核记录。',
  noApi: '未配置后端 API。', loadFailed: '无法读取审核队列；请确认后端已部署，并已应用 V6、V7 数据库迁移。',
  actualAnswer: '系统实际回答', evidence: '引用证据', noEvidence: '没有保存引用片段；应判为证据不足。',
  judgeTitle: 'LLM Judge 预审', runJudge: '运行 LLM 预审', judging: '正在判断…', notRun: '尚未运行；LLM 结论只作建议，不会自动通过。', judgeFailed: 'LLM Judge 调用失败，请稍后重试。',
  humanTitle: '人工最终审批', rationale: '填写判断依据（建议说明答案与证据是否一致）', reliable: '确认可靠', unreliable: '确认不可靠', insufficient: '确认证据不足', saveFailed: '人工结论保存失败。',
  final: '人工结论', model: '模型', groundedness: '证据忠实度', request: '请求',
} : {
  kicker: 'HUMAN REVIEW', title: 'Turn model suggestions into human labels',
  lede: 'Live Q&A retains the question, actual model answer, and cited excerpts. An LLM Judge provides an explainable suggestion; a person must assign the final label.',
  workflow: 'Live Q&A → question / actual answer / evidence → LLM suggestion → human final label → evaluation data',
  privacy: 'This is currently a single-user workspace, so queue reads and approvals do not require an admin token. Restore identity and reviewer authorization before multi-user deployment.',
  pending: 'Pending', reviewed: 'Reviewed', all: 'All', refresh: 'Refresh queue', empty: 'No review records match this filter.',
  noApi: 'The backend API is not configured.', loadFailed: 'Unable to load the queue. Confirm the backend and V6/V7 database migrations are deployed.',
  actualAnswer: 'Actual system answer', evidence: 'Cited evidence', noEvidence: 'No cited excerpt was retained; this should be labeled insufficient evidence.',
  judgeTitle: 'LLM Judge suggestion', runJudge: 'Run LLM Judge', judging: 'Judging…', notRun: 'Not run. The LLM result is advisory and never auto-approves a record.', judgeFailed: 'The LLM Judge request failed. Try again later.',
  humanTitle: 'Human final approval', rationale: 'Reviewer rationale (explain whether the answer matches its evidence)', reliable: 'Confirm reliable', unreliable: 'Confirm unreliable', insufficient: 'Confirm insufficient evidence', saveFailed: 'Unable to save the human verdict.',
  final: 'Human verdict', model: 'Model', groundedness: 'Groundedness', request: 'Request',
})

function formatDate(value?: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(props.locale === 'zh' ? 'zh-CN' : 'en', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(new Date(value))
}

function formatScore(value?: number) {
  return value == null ? '—' : `${Math.round(value * 100)}%`
}

function verdictLabel(value?: string) {
  const labels = props.locale === 'zh'
    ? { RELIABLE: '可靠', UNRELIABLE: '不可靠', INSUFFICIENT_EVIDENCE: '证据不足' }
    : { RELIABLE: 'Reliable', UNRELIABLE: 'Unreliable', INSUFFICIENT_EVIDENCE: 'Insufficient evidence' }
  return labels[value as keyof typeof labels] ?? value ?? '—'
}

async function load() {
  if (!props.apiBase) { error.value = copy.value.noApi; return }
  loading.value = true
  error.value = ''
  try {
    const query = status.value ? `?status=${status.value}` : ''
    const response = await fetch(`${props.apiBase}/api/enterprise/reviews${query}`, {
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const payload = await response.json() as { items?: ReviewItem[] }
    items.value = payload.items ?? []
  } catch {
    error.value = copy.value.loadFailed
    items.value = []
  } finally {
    loading.value = false
  }
}

async function runJudge(item: ReviewItem) {
  if (!props.apiBase) return
  judging.value = item.reviewId
  error.value = ''
  try {
    const response = await fetch(`${props.apiBase}/api/enterprise/reviews/${item.reviewId}/judge`, {
      method: 'POST', headers: { Accept: 'application/json' },
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const updated = await response.json() as ReviewItem
    items.value = items.value.map(candidate => candidate.reviewId === updated.reviewId ? updated : candidate)
  } catch {
    error.value = copy.value.judgeFailed
  } finally {
    judging.value = null
  }
}

async function complete(item: ReviewItem, verdict: string) {
  if (!props.apiBase) return
  saving.value = item.reviewId
  error.value = ''
  try {
    const response = await fetch(`${props.apiBase}/api/enterprise/reviews/${item.reviewId}`, {
      method: 'PATCH',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ verdict, comment: comments.value[item.reviewId] || null }),
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    await load()
  } catch {
    error.value = copy.value.saveFailed
  } finally {
    saving.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="review-dashboard">
    <header class="review-heading">
      <div>
        <p class="eyebrow">{{ copy.kicker }}</p>
        <h2>{{ copy.title }}</h2>
        <p>{{ copy.lede }}</p>
      </div>
      <button type="button" class="primary-action" :disabled="loading" @click="load">{{ loading ? '…' : copy.refresh }}</button>
    </header>

    <div class="workflow">{{ copy.workflow }}</div>
    <p class="privacy-note">{{ copy.privacy }}</p>

    <div class="queue-controls card">
      <label><span>Status</span><select v-model="status" @change="load"><option value="PENDING">{{ copy.pending }}</option><option value="REVIEWED">{{ copy.reviewed }}</option><option value="">{{ copy.all }}</option></select></label>
      <strong>{{ items.length }}</strong>
    </div>

    <p v-if="error" class="review-error" role="alert">{{ error }}</p>
    <div v-if="items.length" class="review-list">
      <article v-for="item in items" :key="item.reviewId" class="review-item card">
        <div class="review-meta"><code>{{ item.requestId }}</code><span>{{ item.accessRole || '—' }}</span><time>{{ formatDate(item.createdAt) }}</time></div>
        <h3>{{ item.question }}</h3>

        <section class="answer-block"><strong>{{ copy.actualAnswer }}</strong><p>{{ item.answer }}</p></section>
        <details class="evidence-block" :open="item.sources?.length > 0"><summary>{{ copy.evidence }} · {{ item.sources?.length ?? 0 }}</summary><div v-if="item.sources?.length"><blockquote v-for="(source, index) in item.sources" :key="index"><header><span>#{{ source.rank || index + 1 }}</span><strong>{{ source.title || source.document_id || source.chunk_id }}</strong></header><p>{{ source.chunk }}</p><small>{{ source.document_id }} · {{ source.chunk_id }}</small></blockquote></div><p v-else>{{ copy.noEvidence }}</p></details>

        <section class="judge-block" :class="{ complete: item.judgeStatus === 'COMPLETED' }">
          <header><div><span>{{ copy.judgeTitle }}</span><strong v-if="item.judgeVerdict">{{ verdictLabel(item.judgeVerdict) }}</strong></div><button v-if="item.status === 'PENDING'" type="button" :disabled="judging === item.reviewId" @click="runJudge(item)">{{ judging === item.reviewId ? copy.judging : copy.runJudge }}</button></header>
          <template v-if="item.judgeStatus === 'COMPLETED'"><div class="judge-facts"><span>{{ copy.groundedness }} <b>{{ formatScore(item.judgeScore) }}</b></span><span>{{ copy.model }} <b>{{ item.judgeModel || '—' }}</b></span></div><p>{{ item.judgeReason }}</p></template>
          <p v-else>{{ copy.notRun }}</p>
        </section>

        <section v-if="item.status === 'PENDING'" class="human-block"><strong>{{ copy.humanTitle }}</strong><textarea v-model="comments[item.reviewId]" rows="3" :placeholder="copy.rationale"/><div class="review-buttons"><button type="button" :disabled="saving === item.reviewId" @click="complete(item, 'RELIABLE')">{{ copy.reliable }}</button><button type="button" :disabled="saving === item.reviewId" @click="complete(item, 'UNRELIABLE')">{{ copy.unreliable }}</button><button type="button" :disabled="saving === item.reviewId" @click="complete(item, 'INSUFFICIENT_EVIDENCE')">{{ copy.insufficient }}</button></div></section>
        <section v-else class="final-block"><strong>{{ copy.final }} · {{ verdictLabel(item.verdict) }}</strong><p>{{ item.reviewerComment || '—' }}</p><time>{{ formatDate(item.reviewedAt) }}</time></section>
      </article>
    </div>
    <div v-else-if="!loading && !error" class="empty card">{{ copy.empty }}</div>
  </section>
</template>

<style scoped>
.review-dashboard{display:grid;gap:18px}.review-heading{align-items:end;display:flex;gap:32px;justify-content:space-between}.review-heading h2{font-size:clamp(34px,5vw,56px)}.review-heading p:not(.eyebrow){color:var(--ink-soft);font-size:16px;line-height:1.75;margin:18px 0 0;max-width:740px}.primary-action{background:var(--accent);border:1px solid var(--accent);border-radius:10px;color:#fffaf4;font-size:11px;font-weight:750;min-height:42px;padding:0 16px}.workflow{background:#edf5ef;border:1px solid #b9d2c0;border-radius:12px;color:#35634a;font:11px/1.6 var(--font-mono);padding:14px 17px}.privacy-note{background:#fff4df;border:1px solid #e2c58d;border-radius:12px;color:#745b2e;font-size:12px;line-height:1.65;margin:0;padding:12px 16px}.queue-controls{align-items:end;display:flex;gap:18px;justify-content:space-between}.queue-controls label{margin:0;max-width:220px;width:100%}.queue-controls label span{display:block;margin-bottom:7px}.queue-controls select{min-height:42px}.queue-controls>strong{color:var(--accent);font-size:30px}.review-error{background:#fff0eb;border:1px solid #dfa18f;border-radius:12px;color:#a1432e;margin:0;padding:13px 16px}.review-list{display:grid;gap:14px}.review-item{display:grid;gap:18px}.review-meta{align-items:center;color:var(--ink-muted);display:flex;flex-wrap:wrap;font-size:10px;gap:12px}.review-meta code{color:var(--ink)}.review-meta time{margin-left:auto}.review-item h3{font-size:22px;margin:0}.answer-block,.judge-block,.human-block,.final-block{border-radius:12px;padding:16px}.answer-block{background:#f2eee6}.answer-block>strong,.human-block>strong{color:var(--ink-muted);display:block;font-size:10px;letter-spacing:.08em;text-transform:uppercase}.answer-block p{line-height:1.75;margin:10px 0 0;white-space:pre-wrap}.evidence-block summary{cursor:pointer;font-size:13px;font-weight:700}.evidence-block>div{display:grid;gap:9px;margin-top:12px}.evidence-block blockquote{background:#fffaf4;border-left:3px solid #d0a354;border-radius:0 10px 10px 0;margin:0;padding:12px 14px}.evidence-block blockquote header{align-items:center;display:flex;gap:8px}.evidence-block blockquote p{line-height:1.65;margin:8px 0}.evidence-block blockquote small{color:var(--ink-muted);font:9px/1.5 var(--font-mono)}.judge-block{background:#f5f1e9;border:1px solid #d8d0c4}.judge-block.complete{background:#edf5ef;border-color:#b9d2c0}.judge-block>header{align-items:center;display:flex;gap:12px;justify-content:space-between}.judge-block header div span,.judge-block header div strong{display:block}.judge-block header div span{color:var(--ink-muted);font-size:9px;letter-spacing:.08em;text-transform:uppercase}.judge-block header div strong{font-size:17px;margin-top:4px}.judge-block button,.review-buttons button{background:#fffaf4;border:1px solid #cfc6ba;border-radius:9px;color:var(--ink-soft);font-size:10px;font-weight:700;padding:9px 11px}.judge-block button:hover,.review-buttons button:hover{border-color:var(--accent);color:var(--accent-dark)}.judge-block>p{line-height:1.65;margin:12px 0 0}.judge-facts{display:flex;flex-wrap:wrap;gap:16px;margin-top:13px}.judge-facts span{color:var(--ink-muted);font-size:10px}.judge-facts b{color:var(--ink);margin-left:4px}.human-block{background:#fffaf4;border:1px solid #e1d8cd}.human-block textarea{background:#fffdf9;border:1px solid #cfc6ba;border-radius:10px;box-sizing:border-box;color:var(--ink);line-height:1.55;margin-top:10px;padding:11px;width:100%}.review-buttons{display:flex;flex-wrap:wrap;gap:8px;margin-top:9px}.final-block{background:#edf5ef;color:#35634a}.final-block p{margin:8px 0}.final-block time{font-size:10px}.empty{color:var(--ink-muted);min-height:140px;padding-top:60px;text-align:center}@media(max-width:680px){.review-heading{align-items:flex-start;flex-direction:column}.review-heading button{width:100%}.review-meta time{margin-left:0}.judge-block>header{align-items:flex-start;flex-direction:column}.judge-block button{width:100%}.review-buttons{display:grid}.review-buttons button{width:100%}}
</style>
