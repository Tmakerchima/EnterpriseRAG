<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

interface ChunkItem {
  chunkId: string
  documentId: string
  externalId: string
  source: string
  sourceType: string
  title: string
  content: string
  chunkIndex: number
  tokenCount: number
  embedded: boolean
  department: string
  accessLevel: string
  corpusVersion: string
  metadata: Record<string, unknown>
}

interface ChunkPage {
  items: ChunkItem[]
  page: number
  size: number
  total: number
  totalPages: number
  query: string
}

type DemoRole = 'public' | 'engineering' | 'finance' | 'hr' | 'admin'

const props = defineProps<{
  apiBase: string
  locale: 'zh' | 'en'
  role: DemoRole
  ready: boolean
}>()
const emit = defineEmits<{ (event: 'update:role', value: DemoRole): void }>()

const query = ref('')
const result = ref<ChunkPage | null>(null)
const loading = ref(false)
const error = ref('')
const expanded = ref<string | null>(null)
let requestSequence = 0

const copy = computed(() => props.locale === 'zh' ? {
  kicker: 'CHUNK POOL / 切片池',
  title: '看看知识是怎么被切开的',
  lede: '切片是送给检索器和大模型的一小段原文。这里展示当前角色真正有权限看到的 ACTIVE 语料；向量、检索增强前缀等内部字段不会暴露。',
  flow: '原始文档 → 切片 → 向量/关键词检索 → 选中切片 → 生成带证据的回答',
  search: '按标题、文档 ID 或正文搜索',
  searchAction: '搜索切片',
  clear: '清空',
  role: '当前演示角色',
  count: '可见切片',
  empty: '没有找到符合条件的切片。',
  unavailable: '后端或 ACTIVE 语料尚未就绪。',
  missingApi: '未配置后端地址。',
  loadFailed: '切片读取失败，请确认后端已经更新并可访问。',
  fullText: '完整可引用原文',
  tokens: '估算 tokens',
  embedded: '已向量化',
  lexicalOnly: '仅关键词',
  previous: '上一页',
  next: '下一页',
  page: '页',
  permission: '权限',
} : {
  kicker: 'CHUNK POOL',
  title: 'See how documents become chunks',
  lede: 'A chunk is a small piece of original text sent to retrieval and the model. This pool shows only ACTIVE-corpus chunks visible to the selected demo role.',
  flow: 'Document → chunks → vector/keyword retrieval → selected chunks → grounded answer',
  search: 'Search title, document ID, or content',
  searchAction: 'Search chunks',
  clear: 'Clear',
  role: 'Demo role',
  count: 'visible chunks',
  empty: 'No matching chunks were found.',
  unavailable: 'The backend or ACTIVE corpus is not ready.',
  missingApi: 'The backend URL is not configured.',
  loadFailed: 'Unable to load chunks. Confirm that the updated backend is reachable.',
  fullText: 'Full citable source text',
  tokens: 'estimated tokens',
  embedded: 'embedded',
  lexicalOnly: 'keyword only',
  previous: 'Previous',
  next: 'Next',
  page: 'page',
  permission: 'access',
})

async function load(page = 0) {
  const requestId = ++requestSequence
  if (!props.apiBase) {
    error.value = copy.value.missingApi
    return
  }
  if (!props.ready) {
    error.value = copy.value.unavailable
    return
  }
  loading.value = true
  error.value = ''
  expanded.value = null
  try {
    const params = new URLSearchParams({
      role: props.role,
      tenantId: 'default',
      q: query.value.trim(),
      page: String(Math.max(0, page)),
      size: '12',
    })
    const response = await fetch(`${props.apiBase}/api/enterprise/chunks?${params}`, {
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const payload = await response.json() as ChunkPage
    if (requestId === requestSequence) result.value = payload
  } catch {
    if (requestId === requestSequence) error.value = copy.value.loadFailed
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

function clearSearch() {
  query.value = ''
  void load(0)
}

function updateRole(event: Event) {
  emit('update:role', (event.target as HTMLSelectElement).value as DemoRole)
}

onMounted(() => load())
watch(() => [props.role, props.ready, props.apiBase], () => {
  // A role change represents a new identity in this demo. Remove the old ACL
  // result immediately and ignore any slower response from the previous role.
  result.value = null
  void load(0)
})
</script>

<template>
  <section class="chunk-pool">
    <header class="chunk-heading">
      <div>
        <p class="eyebrow">{{ copy.kicker }}</p>
        <h2>{{ copy.title }}</h2>
        <p>{{ copy.lede }}</p>
      </div>
      <label class="role-badge">
        <span>{{ copy.role }}</span>
        <select :value="role" @change="updateRole">
          <option value="public">public</option><option value="engineering">engineering</option>
          <option value="finance">finance</option><option value="hr">hr</option><option value="admin">admin</option>
        </select>
      </label>
    </header>

    <div class="chunk-flow" aria-label="RAG flow">{{ copy.flow }}</div>

    <form class="chunk-search card" @submit.prevent="load(0)">
      <label for="chunk-search">{{ copy.search }}</label>
      <div>
        <input id="chunk-search" v-model="query" type="search" :placeholder="copy.search" :disabled="loading">
        <button type="submit" :disabled="loading || !ready">{{ loading ? '…' : copy.searchAction }}</button>
        <button v-if="query" class="clear-button" type="button" :disabled="loading" @click="clearSearch">{{ copy.clear }}</button>
      </div>
    </form>

    <div v-if="error" class="chunk-message card error-message" role="alert">{{ error }}</div>
    <div v-else-if="loading && !result" class="chunk-message card">Loading…</div>
    <template v-else-if="result">
      <div class="chunk-result-heading">
        <strong>{{ result.total.toLocaleString() }}</strong>
        <span>{{ copy.count }}</span>
        <small v-if="result.query">“{{ result.query }}”</small>
      </div>

      <div v-if="result.items.length" class="chunk-list">
        <article v-for="item in result.items" :key="item.chunkId" class="chunk-item card">
          <button type="button" class="chunk-toggle" @click="expanded = expanded === item.chunkId ? null : item.chunkId">
            <span class="chunk-number">#{{ item.chunkIndex + 1 }}</span>
            <span class="chunk-identity">
              <strong>{{ item.title || item.externalId }}</strong>
              <small>{{ item.sourceType }} · {{ item.externalId }}</small>
            </span>
            <span class="chunk-state">{{ item.embedded ? copy.embedded : copy.lexicalOnly }}</span>
          </button>
          <p class="chunk-preview" :class="{ expanded: expanded === item.chunkId }">{{ item.content }}</p>
          <div v-if="expanded === item.chunkId" class="chunk-details">
            <strong>{{ copy.fullText }}</strong>
            <dl>
              <div><dt>chunk_id</dt><dd>{{ item.chunkId }}</dd></div>
              <div><dt>{{ copy.tokens }}</dt><dd>{{ item.tokenCount }}</dd></div>
              <div><dt>{{ copy.permission }}</dt><dd>{{ item.accessLevel }} · {{ item.department }}</dd></div>
              <div><dt>corpus</dt><dd>{{ item.corpusVersion || '—' }}</dd></div>
            </dl>
          </div>
        </article>
      </div>
      <div v-else class="chunk-message card">{{ copy.empty }}</div>

      <nav v-if="result.totalPages > 1" class="chunk-pagination" aria-label="Chunk pages">
        <button type="button" :disabled="loading || result.page === 0" @click="load(result.page - 1)">{{ copy.previous }}</button>
        <span>{{ copy.page }} {{ result.page + 1 }} / {{ result.totalPages }}</span>
        <button type="button" :disabled="loading || result.page + 1 >= result.totalPages" @click="load(result.page + 1)">{{ copy.next }}</button>
      </nav>
    </template>
  </section>
</template>

<style scoped>
.chunk-pool { display: grid; gap: 18px; }
.chunk-heading { align-items: end; display: flex; gap: 32px; justify-content: space-between; margin-bottom: 8px; }
.chunk-heading h2 { font-size: clamp(34px, 5vw, 56px); }
.chunk-heading p:not(.eyebrow) { color: #686158; font-family: var(--font-editorial); line-height: 1.7; margin: 18px 0 0; max-width: 720px; }
.role-badge { background: #282521; color: #fffaf4; min-width: 150px; padding: 14px 16px; }
.role-badge span { display: block; }
.role-badge span { color: #cfc6ba; font-size: 9px; letter-spacing: .08em; text-transform: uppercase; }
.role-badge select { background: transparent; border: 0; color: #fffaf4; font: 500 15px/1.3 var(--font-editorial); margin-top: 6px; padding: 0; width: 100%; }
.role-badge option { color: #282521; }
.chunk-flow { background: #edf5ef; border: 1px solid #a9c7b2; color: #35634a; font: 11px/1.5 ui-monospace, monospace; padding: 14px 17px; }
.chunk-search label { color: #817a70; display: block; font-size: 10px; font-weight: 700; letter-spacing: .07em; margin-bottom: 9px; text-transform: uppercase; }
.chunk-search > div { display: flex; gap: 8px; }
.chunk-search input { background: #fffdf9; border: 1px solid #cfc6ba; color: #282521; flex: 1; min-width: 0; padding: 12px 13px; }
.chunk-search button, .chunk-pagination button { background: #282521; border: 1px solid #282521; color: #fffaf4; font-size: 10px; font-weight: 700; padding: 0 16px; }
.chunk-search .clear-button { background: transparent; color: #686158; }
.chunk-search button:disabled, .chunk-pagination button:disabled { cursor: not-allowed; opacity: .4; }
.chunk-message { color: #817a70; min-height: 110px; padding-top: 44px; text-align: center; }
.chunk-result-heading { align-items: baseline; display: flex; gap: 8px; }
.chunk-result-heading strong { font: 500 28px/1 var(--font-editorial); }
.chunk-result-heading span, .chunk-result-heading small { color: #817a70; font-size: 10px; letter-spacing: .07em; text-transform: uppercase; }
.chunk-result-heading small { margin-left: auto; text-transform: none; }
.chunk-list { display: grid; gap: 10px; }
.chunk-item { padding: 0; }
.chunk-toggle { align-items: center; background: transparent; border: 0; color: #39342f; display: grid; gap: 14px; grid-template-columns: 46px 1fr auto; padding: 18px 20px 12px; text-align: left; width: 100%; }
.chunk-number { color: #b55942; font: 700 10px/1 ui-monospace, monospace; }
.chunk-identity strong, .chunk-identity small { display: block; }
.chunk-identity strong { font-size: 13px; }
.chunk-identity small { color: #91887c; font: 9px/1.4 ui-monospace, monospace; margin-top: 4px; overflow-wrap: anywhere; }
.chunk-state { background: #e9f3ed; border: 1px solid #9ec2ac; color: #3b7658; font-size: 9px; font-weight: 700; padding: 5px 7px; text-transform: uppercase; }
.chunk-preview { color: #686158; display: -webkit-box; font-family: var(--font-editorial); font-size: 13px; -webkit-line-clamp: 2; -webkit-box-orient: vertical; line-height: 1.65; margin: 0; overflow: hidden; padding: 0 20px 18px 80px; white-space: pre-wrap; }
.chunk-preview.expanded { display: block; overflow: visible; }
.chunk-details { background: #f1ece4; border-top: 1px solid #e3ddd4; padding: 14px 20px; }
.chunk-details > strong { color: #817a70; font-size: 9px; letter-spacing: .08em; text-transform: uppercase; }
.chunk-details dl { display: grid; gap: 6px 18px; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 12px 0 0; }
.chunk-details dl div { display: grid; gap: 8px; grid-template-columns: 80px 1fr; }
.chunk-details dt { color: #91887c; font: 9px/1.4 ui-monospace, monospace; }
.chunk-details dd { color: #49423b; font: 10px/1.4 ui-monospace, monospace; margin: 0; overflow-wrap: anywhere; }
.chunk-pagination { align-items: center; display: flex; justify-content: center; gap: 14px; }
.chunk-pagination button { min-height: 38px; }
.chunk-pagination span { color: #817a70; font: 10px/1 ui-monospace, monospace; }

@media (max-width: 720px) {
  .chunk-heading { align-items: flex-start; flex-direction: column; }
  .role-badge { min-width: 0; width: 100%; }
  .chunk-search > div { display: grid; grid-template-columns: 1fr 1fr; }
  .chunk-search input { grid-column: 1 / -1; }
  .chunk-search button { min-height: 42px; }
  .chunk-toggle { grid-template-columns: 38px 1fr; }
  .chunk-state { grid-column: 2; justify-self: start; }
  .chunk-preview { padding-left: 20px; }
  .chunk-details dl { grid-template-columns: 1fr; }
}
</style>
