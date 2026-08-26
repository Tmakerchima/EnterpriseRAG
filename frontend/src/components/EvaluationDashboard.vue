<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { isEvaluationReport, type CaseSuccessRow, type EvaluationReport } from '../evaluation-report'

const props = defineProps<{ locale: 'zh' | 'en' }>()
type CheckState = 'PASS' | 'FAIL' | 'PENDING' | 'INFO'
interface BeginnerCheck { key: string; number: string; question: string; value: string; state: CheckState; answer: string; detail: string }

const report = ref<EvaluationReport | null>(null)
const loading = ref(false)
const loadError = ref('')
const source = ref<'published' | 'local'>('published')
const fileInput = ref<HTMLInputElement | null>(null)

const copy = computed(() => props.locale === 'zh' ? {
  kicker: 'EVALUATION / 初学者模式', title: '这套 RAG 到底好不好？',
  lede: '先看四个能直接回答的问题。专业指标和逐题明细仍然保留，但默认折叠。',
  latest: '刷新报告', import: '导入报告', loading: '正在读取评测报告…',
  empty: '还没有评测报告。请先运行 evaluation 中的 smoke 或 collect/score/report，再导入 summary.json。',
  invalidFile: '这不是有效的 EnterpriseRAG summary.json。', loadFailed: '读取已发布评测报告失败。',
  synthetic: '这是合成数据，只能证明评测程序能运行，不能代表真实业务效果。',
  limitedScope: '本次只测了 {supported}/{questions} 道题；结论不能代表完整 500 题或你的真实企业数据。',
  verdictTitle: '一句话结论', verdictPass: '达到当前报告设定的门槛', verdictFail: '暂时不建议上线', verdictPending: '现在还不能判断是否可上线',
  verdictPassDetail: '已测的检索、安全和语义指标通过当前阈值；上线前仍要完成真实企业数据、身份系统和压力测试。',
  verdictFailDetail: '至少一项关键指标未过线，请先处理下方标红的项目。',
  verdictPendingDetail: '主要原因是语义裁判没有运行。系统找到证据，不等于最终回答一定正确。',
  nextTitle: '下一步先做什么',
  nextRetrieval: '先优化检索：正确证据进入前 5 条的比例还没达到 85%。',
  nextJudge: '配置 LLM Judge 或人工抽检，确认回答是否忠于证据。',
  nextSafety: '先修复权限泄漏；安全门禁失败时不能发布。',
  nextReady: '换成真实企业问题与真实权限数据，做预发布压测和人工验收。',
  qRetrieval: '能找到正确资料吗？', qAnswer: '回答真的可靠吗？', qSafety: '会不会看到无权查看的资料？', qSpeed: '用户要等多久？',
  yes: '通过', no: '未通过', pending: '待确认', observed: '已测量',
  retrievalAnswer: '每 100 个问题，约有 {rate} 个能在前 5 条里找到正确证据。',
  retrievalDetail: '目标是至少 85%。这项叫 Recall@5；数值越高越好。',
  answerMeasured: '语义裁判认为回答忠于证据的比例是 {rate}。',
  answerPending: '语义裁判没有运行，所以只能确认“找到了证据”，不能确认回答意思一定正确。',
  answerDetail: 'Token F1 只比较文字重合，不足以判断改写后的回答是否正确。',
  safetyPass: '本次测试没有发现越权切片。', safetyFail: '发现了越权切片，这是必须阻断发布的问题。', safetyPending: '安全测试没有执行。',
  safetyDetail: '管理员也只能访问自己的 tenant；安全项不能被其他平均分抵消。',
  speedAnswer: '95% 的请求在 {seconds} 秒内完成。', speedPending: '性能数据没有执行。',
  speedDetail: 'P95 比平均值更接近大多数用户遇到的较慢体验；正式上线应按自己的 SLO 设门槛。',
  local: '本地导入', published: '仓库内报告', run: '运行 ID', dataset: '数据集', measuredAt: '测量时间', scope: '有效题数',
  advanced: '查看专业指标（可选）', advancedHint: '这些指标给调优人员使用；初学者看上面的四个问题就够了。',
  retrievalMetrics: '检索指标', answerMetrics: '回答指标', operations: '安全与性能', cases: '查看逐题结果',
  caseId: '题目', result: '结果', reason: '原因', pass: '通过', fail: '失败', review: '待人工判断', noReason: '必要条件已通过', noCases: '报告没有逐题明细。',
  notMeasured: '未测量', glossary: '小词典',
  recall: 'Recall@5：正确证据有没有出现在前 5 条。', ndcg: 'nDCG@10：正确资料是否排得靠前。',
  tokenF1: 'Token F1：回答和标准答案的文字重合度。', exact: 'Exact match：回答文字是否完全相同；生成式回答通常很低。',
} : {
  kicker: 'EVALUATION / BEGINNER MODE', title: 'Is this RAG system actually good?',
  lede: 'Start with four plain questions. Professional metrics and case details remain available but collapsed by default.',
  latest: 'Refresh report', import: 'Import report', loading: 'Loading the evaluation report…',
  empty: 'No evaluation report is available. Run smoke or collect/score/report in evaluation, then import summary.json.',
  invalidFile: 'This is not a valid EnterpriseRAG summary.json.', loadFailed: 'Unable to read the published evaluation report.',
  synthetic: 'This is synthetic data. It proves the evaluator runs, not that the product performs well on real work.',
  limitedScope: 'Only {supported}/{questions} questions were measured. This does not represent the full benchmark or your enterprise data.',
  verdictTitle: 'Bottom line', verdictPass: 'Meets the thresholds in this report', verdictFail: 'Not ready to release', verdictPending: 'Release readiness is still unknown',
  verdictPassDetail: 'Measured retrieval, safety, and semantic checks meet current thresholds. Real data, identity integration, and load tests are still required.',
  verdictFailDetail: 'At least one critical metric missed its threshold. Fix the red item below first.',
  verdictPendingDetail: 'The semantic judge did not run. Finding evidence does not prove the final answer is correct.',
  nextTitle: 'What to do next', nextRetrieval: 'Improve retrieval first: correct evidence appears in the top 5 less than 85% of the time.',
  nextJudge: 'Run an LLM judge or human review to confirm answers stay faithful to evidence.',
  nextSafety: 'Fix the permission leak first. A failed security gate blocks release.',
  nextReady: 'Evaluate real enterprise questions and permissions, then run pre-release load and human acceptance tests.',
  qRetrieval: 'Can it find the right material?', qAnswer: 'Are the answers actually reliable?', qSafety: 'Can users see forbidden material?', qSpeed: 'How long do users wait?',
  yes: 'Pass', no: 'Fail', pending: 'Pending', observed: 'Measured',
  retrievalAnswer: 'For every 100 questions, about {rate} find correct evidence in the first 5 results.',
  retrievalDetail: 'The target is at least 85%. This metric is Recall@5; higher is better.',
  answerMeasured: 'The semantic judge rated {rate} of answers faithful to their evidence.',
  answerPending: 'The semantic judge did not run. Evidence was found, but answer meaning has not been verified.',
  answerDetail: 'Token F1 only measures word overlap and cannot validate a correctly paraphrased answer.',
  safetyPass: 'No forbidden chunks were found in this test.', safetyFail: 'Forbidden chunks were found. This must block release.', safetyPending: 'Security evaluation did not run.',
  safetyDetail: 'An admin is still tenant-scoped. Security cannot be averaged away by other scores.',
  speedAnswer: '95% of requests finished within {seconds} seconds.', speedPending: 'Performance evaluation did not run.',
  speedDetail: 'P95 reflects the slower experience most users may encounter. Production needs a threshold based on your own SLO.',
  local: 'Local import', published: 'Published report', run: 'Run ID', dataset: 'Dataset', measuredAt: 'Measured at', scope: 'Eligible cases',
  advanced: 'Show professional metrics (optional)', advancedHint: 'These help tuning work. Beginners can use the four questions above.',
  retrievalMetrics: 'Retrieval metrics', answerMetrics: 'Answer metrics', operations: 'Safety and performance', cases: 'Show case results',
  caseId: 'Case', result: 'Result', reason: 'Reason', pass: 'Pass', fail: 'Fail', review: 'Needs human review', noReason: 'Required checks passed', noCases: 'The report has no case details.',
  notMeasured: 'Not measured', glossary: 'Glossary', recall: 'Recall@5: whether correct evidence appears in the first 5 results.',
  ndcg: 'nDCG@10: whether relevant material ranks near the top.', tokenF1: 'Token F1: word overlap between the answer and a reference answer.',
  exact: 'Exact match: identical normalized text; usually low for generated prose.',
})

const reportUrl = (import.meta.env.VITE_EVALUATION_REPORT_URL || '/evaluation/latest.json').trim()
const retrieval = computed(() => report.value?.layers?.retrieval)
const generation = computed(() => report.value?.layers?.generation)
const security = computed(() => report.value?.layers?.security)
const performance = computed(() => report.value?.layers?.performance)
const scope = computed(() => report.value?.manifest?.scope)
const recall5 = computed(() => retrieval.value?.ks?.['5']?.recall?.value ?? null)
const ndcg10 = computed(() => retrieval.value?.ks?.['10']?.ndcg?.value ?? null)
const judgeResult = computed(() => generation.value?.judge?.results?.find(item => item.metric === 'faithfulness' && item.status === 'MEASURED' && item.score != null))
const judgeScore = computed(() => judgeResult.value?.score ?? null)

const releaseState = computed<'PASS' | 'FAIL' | 'PENDING'>(() => {
  if (security.value?.hard_gate === 'FAIL') return 'FAIL'
  if (recall5.value != null && recall5.value < 0.85) return 'FAIL'
  if (ndcg10.value != null && ndcg10.value < 0.80) return 'FAIL'
  if (judgeScore.value == null || security.value?.status !== 'MEASURED' || recall5.value == null) return 'PENDING'
  return judgeScore.value >= (judgeResult.value?.threshold ?? 0.90) ? 'PASS' : 'FAIL'
})
const verdict = computed(() => releaseState.value === 'PASS'
  ? { title: copy.value.verdictPass, detail: copy.value.verdictPassDetail }
  : releaseState.value === 'FAIL'
    ? { title: copy.value.verdictFail, detail: copy.value.verdictFailDetail }
    : { title: copy.value.verdictPending, detail: copy.value.verdictPendingDetail })
const nextAction = computed(() => security.value?.hard_gate === 'FAIL' ? copy.value.nextSafety
  : recall5.value != null && recall5.value < 0.85 ? copy.value.nextRetrieval
    : judgeScore.value == null ? copy.value.nextJudge : copy.value.nextReady)

const checks = computed<BeginnerCheck[]>(() => {
  const retrievalState: CheckState = recall5.value == null ? 'PENDING' : recall5.value >= 0.85 ? 'PASS' : 'FAIL'
  const answerState: CheckState = judgeScore.value == null ? 'PENDING' : judgeScore.value >= (judgeResult.value?.threshold ?? 0.90) ? 'PASS' : 'FAIL'
  const safetyState: CheckState = security.value?.status !== 'MEASURED' ? 'PENDING' : security.value?.hard_gate === 'PASS' ? 'PASS' : 'FAIL'
  const p95 = performance.value?.p95_ms
  return [
    { key: 'retrieval', number: '01', question: copy.value.qRetrieval, value: formatPercent(recall5.value), state: retrievalState,
      answer: recall5.value == null ? copy.value.notMeasured : interpolate(copy.value.retrievalAnswer, { rate: Math.round(recall5.value * 100) }), detail: copy.value.retrievalDetail },
    { key: 'answer', number: '02', question: copy.value.qAnswer, value: formatPercent(judgeScore.value), state: answerState,
      answer: judgeScore.value == null ? copy.value.answerPending : interpolate(copy.value.answerMeasured, { rate: formatPercent(judgeScore.value) }), detail: copy.value.answerDetail },
    { key: 'safety', number: '03', question: copy.value.qSafety,
      value: safetyState === 'PASS' ? '0 leak' : safetyState === 'FAIL' ? String(security.value?.forbidden_retrieval_count ?? 1) : '—', state: safetyState,
      answer: safetyState === 'PASS' ? copy.value.safetyPass : safetyState === 'FAIL' ? copy.value.safetyFail : copy.value.safetyPending, detail: copy.value.safetyDetail },
    { key: 'speed', number: '04', question: copy.value.qSpeed, value: p95 == null ? '—' : `${(p95 / 1000).toFixed(1)}s`, state: p95 == null ? 'PENDING' : 'INFO',
      answer: p95 == null ? copy.value.speedPending : interpolate(copy.value.speedAnswer, { seconds: (p95 / 1000).toFixed(1) }), detail: copy.value.speedDetail },
  ]
})

const professionalMetrics = computed(() => [
  { group: copy.value.retrievalMetrics, rows: [
    { name: 'Recall@5', metric: retrieval.value?.ks?.['5']?.recall }, { name: 'nDCG@10', metric: retrieval.value?.ks?.['10']?.ndcg },
    { name: 'MRR@10', metric: retrieval.value?.ks?.['10']?.mrr }, { name: 'HitRate@5', metric: retrieval.value?.ks?.['5']?.hit_rate },
  ] },
  { group: copy.value.answerMetrics, rows: [
    { name: 'Faithfulness', metric: judgeScore.value == null ? undefined : { value: judgeScore.value, count: 1 } },
    { name: 'Fact coverage', metric: generation.value?.fact_coverage }, { name: 'Token F1', metric: generation.value?.token_f1 },
    { name: 'Exact match', metric: generation.value?.exact_match },
  ] },
])

function interpolate(template: string, values: Record<string, string | number>) { return Object.entries(values).reduce((text, [key, value]) => text.replace(`{${key}}`, String(value)), template) }
function formatPercent(value: number | null | undefined, digits = 1) { return value == null ? '—' : `${(value * 100).toFixed(digits)}%` }
function formatDate(value: string | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(props.locale === 'zh' ? 'zh-CN' : 'en', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}
function checkLabel(state: CheckState) { return state === 'PASS' ? copy.value.yes : state === 'FAIL' ? copy.value.no : state === 'INFO' ? copy.value.observed : copy.value.pending }
function caseOutcome(item: CaseSuccessRow): 'pass' | 'fail' | 'review' { return item.outcome === 'NEEDS_REVIEW' || item.success == null ? 'review' : item.success ? 'pass' : 'fail' }
function caseLabel(item: CaseSuccessRow) { const outcome = caseOutcome(item); return outcome === 'pass' ? copy.value.pass : outcome === 'fail' ? copy.value.fail : copy.value.review }
function reasonLabel(reason: string) {
  const labels = props.locale === 'zh' ? {
    REQUIRED_EVIDENCE_NOT_IN_FINAL_CONTEXT: '正确证据没有进入最终上下文', SEMANTIC_FACT_REVIEW_REQUIRED: '已经找到证据，回答意思待人工确认',
    PRODUCT_ERROR: '产品请求出错', SECURITY_VIOLATION: '权限策略违规', ABSTENTION_FAILURE: '应该拒答但没有拒答',
  } : {
    REQUIRED_EVIDENCE_NOT_IN_FINAL_CONTEXT: 'Correct evidence did not enter final context', SEMANTIC_FACT_REVIEW_REQUIRED: 'Evidence found; answer meaning needs review',
    PRODUCT_ERROR: 'Product request failed', SECURITY_VIOLATION: 'Permission policy violation', ABSTENTION_FAILURE: 'The system should have abstained',
  }
  return labels[reason as keyof typeof labels] || reason
}
async function loadLatest() {
  loading.value = true; loadError.value = ''
  try {
    const response = await fetch(reportUrl, { cache: 'no-store', headers: { Accept: 'application/json' } })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const payload: unknown = await response.json()
    if (!isEvaluationReport(payload)) throw new Error('invalid report')
    report.value = payload; source.value = 'published'
  } catch { if (!report.value) loadError.value = copy.value.loadFailed } finally { loading.value = false }
}
function openFilePicker() { fileInput.value?.click() }
async function importReport(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (!file) return
  loadError.value = ''
  try { const payload: unknown = JSON.parse(await file.text()); if (!isEvaluationReport(payload)) throw new Error('invalid report'); report.value = payload; source.value = 'local' }
  catch { loadError.value = copy.value.invalidFile } finally { input.value = '' }
}
onMounted(loadLatest)
</script>

<template>
  <section id="evaluation" class="evaluation-dashboard">
    <header class="evaluation-heading">
      <div><p class="eyebrow">{{ copy.kicker }}</p><h2>{{ copy.title }}</h2><p>{{ copy.lede }}</p></div>
      <div class="evaluation-actions">
        <button type="button" class="secondary-action" :disabled="loading" @click="loadLatest">{{ copy.latest }}</button>
        <button type="button" class="primary-action" @click="openFilePicker">{{ copy.import }}</button>
        <input ref="fileInput" class="visually-hidden" type="file" accept="application/json,.json" @change="importReport">
      </div>
    </header>
    <div v-if="loading && !report" class="empty card">{{ copy.loading }}</div>
    <div v-else-if="!report" class="empty card"><strong>NOT MEASURED</strong><p>{{ loadError || copy.empty }}</p></div>
    <template v-else>
      <div v-if="report.synthetic_fixture" class="notice warning"><strong>SYNTHETIC FIXTURE</strong><span>{{ copy.synthetic }}</span></div>
      <div v-else-if="scope" class="notice scope"><strong>LIMITED SCOPE</strong><span>{{ interpolate(copy.limitedScope, { supported: scope.fully_supported ?? 0, questions: scope.question_count ?? 0 }) }}</span></div>
      <div v-if="loadError" class="notice danger">{{ loadError }}</div>

      <section class="verdict card" :class="releaseState.toLowerCase()">
        <div class="verdict-mark">{{ releaseState === 'PASS' ? '✓' : releaseState === 'FAIL' ? '!' : '?' }}</div>
        <div><span>{{ copy.verdictTitle }} · {{ source === 'local' ? copy.local : copy.published }}</span><h3>{{ verdict.title }}</h3><p>{{ verdict.detail }}</p></div>
        <aside><strong>{{ copy.nextTitle }}</strong><p>{{ nextAction }}</p></aside>
      </section>

      <div class="beginner-checks">
        <article v-for="check in checks" :key="check.key" class="check-card card" :class="check.state.toLowerCase()">
          <div class="check-topline"><span>{{ check.number }}</span><em>{{ checkLabel(check.state) }}</em></div>
          <h3>{{ check.question }}</h3><strong class="check-value">{{ check.value }}</strong><p>{{ check.answer }}</p><small>{{ check.detail }}</small>
        </article>
      </div>

      <section class="run-facts card">
        <div><span>{{ copy.run }}</span><strong>{{ report.manifest?.run_id || '—' }}</strong></div>
        <div><span>{{ copy.dataset }}</span><strong>{{ Array.isArray(report.manifest?.dataset_version) ? report.manifest.dataset_version.join(', ') : report.manifest?.dataset_version || '—' }}</strong></div>
        <div><span>{{ copy.scope }}</span><strong>{{ retrieval?.eligible_cases ?? 0 }}</strong></div>
        <div><span>{{ copy.measuredAt }}</span><strong>{{ formatDate(report.manifest?.created_at) }}</strong></div>
      </section>

      <details class="advanced card">
        <summary><span>{{ copy.advanced }}</span><small>{{ copy.advancedHint }}</small></summary>
        <div class="metric-groups">
          <section v-for="group in professionalMetrics" :key="group.group">
            <h3>{{ group.group }}</h3>
            <div v-for="row in group.rows" :key="row.name" class="metric-row">
              <span>{{ row.name }}</span><div><i :style="{ width: `${Math.max(0, Math.min(100, (row.metric?.value ?? 0) * 100))}%` }" /></div>
              <strong>{{ formatPercent(row.metric?.value) }}</strong><small>n={{ row.metric?.count ?? 0 }}</small>
            </div>
          </section>
          <section><h3>{{ copy.operations }}</h3><div class="operation-list">
            <p><span>ACL gate</span><strong>{{ security?.hard_gate || copy.notMeasured }}</strong></p>
            <p><span>Forbidden chunks</span><strong>{{ security?.forbidden_retrieval_count ?? '—' }}</strong></p>
            <p><span>P95</span><strong>{{ performance?.p95_ms == null ? '—' : `${Math.round(performance.p95_ms)} ms` }}</strong></p>
            <p><span>Error rate</span><strong>{{ formatPercent(performance?.error_rate) }}</strong></p>
          </div></section>
        </div>
        <div class="glossary"><strong>{{ copy.glossary }}</strong><p>{{ copy.recall }}</p><p>{{ copy.ndcg }}</p><p>{{ copy.tokenF1 }}</p><p>{{ copy.exact }}</p></div>
      </details>

      <details class="cases card">
        <summary>{{ copy.cases }} · {{ generation?.case_success?.cases?.length ?? 0 }}</summary>
        <div v-if="generation?.case_success?.cases?.length" class="case-table-wrap"><table>
          <thead><tr><th>{{ copy.caseId }}</th><th>{{ copy.result }}</th><th>{{ copy.reason }}</th></tr></thead>
          <tbody><tr v-for="item in generation.case_success.cases" :key="item.case_id">
            <td><code>{{ item.case_id }}</code></td><td><span class="case-result" :class="caseOutcome(item)">{{ caseLabel(item) }}</span></td>
            <td>{{ item.reasons.length ? item.reasons.map(reasonLabel).join(' · ') : copy.noReason }}</td>
          </tr></tbody>
        </table></div><p v-else>{{ copy.noCases }}</p>
      </details>
    </template>
  </section>
</template>

<style scoped>
.evaluation-dashboard{display:grid;gap:18px}.evaluation-heading{align-items:end;display:flex;gap:32px;justify-content:space-between;margin-bottom:18px}.evaluation-heading h2{font-size:clamp(34px,5vw,56px)}.evaluation-heading p:not(.eyebrow){color:#686158;font-family:var(--font-editorial);font-size:17px;line-height:1.65;margin:18px 0 0;max-width:680px}.evaluation-actions{display:flex;flex:0 0 auto;gap:9px}.evaluation-actions button{border-radius:1px;font-size:10px;font-weight:700;min-height:41px;padding:0 14px}.primary-action{background:#c9654b;border:1px solid #c9654b;color:#fffaf4}.secondary-action{background:transparent;border:1px solid #cfc6ba;color:#686158}.evaluation-actions button:disabled{cursor:wait;opacity:.5}.visually-hidden{height:1px;margin:-1px;opacity:0;overflow:hidden;position:absolute;width:1px}.empty{color:#817a70;min-height:220px;padding-top:70px;text-align:center}.empty strong{color:#a1432e;font:700 10px/1 ui-monospace,monospace;letter-spacing:.1em}.empty p{line-height:1.7;margin:16px auto 0;max-width:560px}
.notice{align-items:center;display:flex;font-family:var(--font-editorial);font-size:13px;gap:18px;line-height:1.5;padding:14px 17px}.notice strong{flex:0 0 auto;font:700 10px/1.4 ui-monospace,monospace;letter-spacing:.07em}.notice.warning{background:#fff4df;border:1px solid #dfbe7c;color:#745b2e}.notice.scope{background:#edf5ef;border:1px solid #a9c7b2;color:#35634a}.notice.danger{background:#fff0eb;border:1px solid #dfa18f;color:#a1432e}
.verdict{align-items:center;display:grid;gap:22px;grid-template-columns:58px 1fr minmax(220px,.65fr)}.verdict-mark{align-items:center;background:#eee8df;border-radius:50%;color:#817568;display:flex;font:500 28px/1 var(--font-editorial);height:54px;justify-content:center;width:54px}.verdict>div span{color:#817a70;font-size:9px;letter-spacing:.08em;text-transform:uppercase}.verdict h3{color:#39342f;font-family:var(--font-editorial);font-size:25px;font-weight:500;margin:7px 0 0}.verdict p{color:#686158;font-size:12px;line-height:1.6;margin:8px 0 0}.verdict aside{background:#f1ece4;padding:15px}.verdict aside strong{color:#817a70;font-size:9px;letter-spacing:.08em;text-transform:uppercase}.verdict aside p{color:#49423b}.verdict.pass .verdict-mark{background:#e9f3ed;color:#3b7658}.verdict.fail .verdict-mark{background:#fff0eb;color:#a1432e}
.beginner-checks{display:grid;gap:12px;grid-template-columns:repeat(2,1fr)}.check-card{border-top:3px solid #d5cabd;min-height:260px}.check-card.pass{border-top-color:#6b9e7e}.check-card.fail{border-top-color:#c9654b}.check-card.pending{border-top-color:#d0a354}.check-card.info{border-top-color:#668da1}.check-topline{align-items:center;display:flex;justify-content:space-between}.check-topline>span{color:#b55942;font:700 10px/1 ui-monospace,monospace}.check-topline em,.case-result{border:1px solid currentColor;font-size:9px;font-style:normal;font-weight:800;letter-spacing:.07em;padding:5px 7px;text-transform:uppercase}.pass .check-topline em,.case-result.pass{color:#3b7658}.fail .check-topline em,.case-result.fail{color:#a1432e}.pending .check-topline em,.case-result.review{color:#946d2d}.info .check-topline em{color:#4c7589}.check-card h3{color:#39342f;font-family:var(--font-editorial);font-size:22px;font-weight:500;margin:22px 0 0}.check-value{display:block;font:500 37px/1 var(--font-editorial);margin-top:18px}.check-card>p{color:#514a43;font-family:var(--font-editorial);font-size:14px;line-height:1.6;margin:16px 0 0}.check-card>small{border-top:1px solid #e3ddd4;color:#91887c;display:block;font-size:10px;line-height:1.55;margin-top:16px;padding-top:12px}
.run-facts{display:grid;gap:18px;grid-template-columns:1.2fr 1fr .5fr 1fr}.run-facts span,.run-facts strong{display:block}.run-facts span{color:#91887c;font-size:9px;letter-spacing:.07em;text-transform:uppercase}.run-facts strong{color:#49423b;font:10px/1.5 ui-monospace,monospace;margin-top:6px;overflow-wrap:anywhere}details summary{color:#39342f;cursor:pointer;font-family:var(--font-editorial);font-size:19px;font-weight:500}.advanced summary span,.advanced summary small{display:block}.advanced summary small{color:#91887c;font-family:system-ui,sans-serif;font-size:10px;font-weight:400;margin:6px 0 0 18px}.metric-groups{display:grid;gap:28px;grid-template-columns:repeat(3,1fr);margin-top:28px}.metric-groups h3{border-bottom:1px solid #e3ddd4;color:#49423b;font-size:12px;margin:0 0 14px;padding-bottom:10px}.metric-row{align-items:center;display:grid;gap:8px;grid-template-columns:82px 1fr 48px;margin-top:12px}.metric-row>span{color:#686158;font-size:10px}.metric-row>div{background:#e8e1d8;height:5px}.metric-row i{background:#c9654b;display:block;height:100%}.metric-row strong{font:10px/1 ui-monospace,monospace;text-align:right}.metric-row small{color:#9a9288;font:8px/1 ui-monospace,monospace;grid-column:2/-1;text-align:right}.operation-list p{align-items:center;border-bottom:1px solid #e3ddd4;display:flex;font-size:10px;justify-content:space-between;margin:0;padding:9px 0}.operation-list span{color:#817a70}.operation-list strong{font-family:ui-monospace,monospace}.glossary{background:#f1ece4;margin-top:24px;padding:16px}.glossary strong{color:#817a70;font-size:9px;letter-spacing:.08em;text-transform:uppercase}.glossary p{color:#686158;display:inline;font-size:10px;line-height:1.65;margin:0 14px 0 0}.cases[open] summary{border-bottom:1px solid #e3ddd4;padding-bottom:16px}.case-table-wrap{margin-top:18px;overflow-x:auto}table{border-collapse:collapse;width:100%}th{color:#91887c;font-size:9px;letter-spacing:.08em;padding:0 12px 10px;text-align:left;text-transform:uppercase}td{border-top:1px solid #e3ddd4;color:#686158;font-size:11px;line-height:1.5;padding:13px 12px}td code{color:#49423b;font-size:10px}
@media(max-width:900px){.verdict{grid-template-columns:54px 1fr}.verdict aside{grid-column:1/-1}.metric-groups{grid-template-columns:1fr}.run-facts{grid-template-columns:repeat(2,1fr)}}@media(max-width:680px){.evaluation-heading{align-items:flex-start;flex-direction:column}.evaluation-actions{display:grid;grid-template-columns:1fr 1fr;width:100%}.notice{align-items:flex-start;flex-direction:column;gap:7px}.verdict{grid-template-columns:1fr}.verdict aside{grid-column:auto}.beginner-checks,.run-facts{grid-template-columns:1fr}}
</style>
