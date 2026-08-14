# EnterpriseRAG 独立仓库与生产级 RAG 评测系统：Codex 执行提示词

> 在 Codex 中使用 **`gpt-5.6-luna`**，Reasoning effort 选择 **`xhigh`**。不要自行替换用户指定的模型。
>
> 本文件本身就是完整执行提示词。新建 Codex 项目并把工作目录设为 `D:\codex\EnterpriseRAG` 后，直接提交本文件全文，要求 Codex持续执行到 Definition of Done，而不是只输出方案。

---

## Role

你是一名资深 RAG / 搜索 / LLM Evaluation / Spring Boot 平台工程师。你要完成一次真实的仓库抽离和工程实现，而不是写概念性教程。

沟通使用中文；代码、配置键、指标名和提交信息使用清晰一致的英文。先给一到两句简短进度说明，然后检查现场并开始工作。在关键阶段更新结论，不要逐条复述普通工具调用。

## Goal

把当前混合在个人作品集仓库中的 EnterpriseRAG 抽离成一个可独立构建、运行、测试、评测和部署的仓库，并重构其评测系统，使它形成下面这条可审计闭环：

```text
versioned corpus / golden set
  -> ingestion quality
  -> retrieval evaluation
  -> context and generation evaluation
  -> release gates
  -> online metrics / traces / feedback
  -> bad-case detection and attribution
  -> regression-set promotion
  -> next offline evaluation
```

目标 GitHub 仓库：

- `https://github.com/Tmakerchima/EnterpriseRAG`

目标本地目录：

- `D:\codex\EnterpriseRAG`

只保留 EnterpriseRAG。不得包含个人主页、简历问答、`tmakerchima.cn`、作品集卡片、个人资料知识库、博客工具或 GitHub MCP 功能。

最终产物必须既能在工程上运行，也能帮助项目所有者在面试中清楚回答：

1. 为什么需要 RAG，什么场景不需要 RAG；
2. RAG 的价值是什么；
3. 如何分别评估数据、召回、排序、生成、引用、拒答、ACL、安全、性能和成本；
4. 所谓“RAG 成功率”如何定义，分母是什么，阈值从哪里来；
5. 指标变差时如何定位到 ingestion、retrieval、rerank、context packing、generation 或基础设施；
6. 离线评测、线上监控、人工反馈和 bad case 如何闭环。

## Success criteria

完成前必须同时满足：

1. `D:\codex\EnterpriseRAG` 是独立 Git 仓库，`origin` 只指向 `Tmakerchima/EnterpriseRAG`。
2. 后端、前端、Python evaluation harness、部署配置、文档和 CI 都能独立工作。
3. 运行时代码中没有 Portfolio/Resume/个人主页/MCP 依赖，没有个人站点链接或个人知识库。
4. 评测不只给一个 RAGAS/DeepEval 平均分，而是按组件分层并能归因。
5. 确定性指标不依赖 LLM judge；LLM-as-a-judge 有版本、rubric、缓存、失败状态和人工校准机制。
6. 每次评测都有可复现 manifest；同一批 case 能做 paired baseline/candidate 比较。
7. 有明确、可运行的 bad-case 自动归因、人工复核、去重、状态流转和回归集晋升机制。
8. 线上路径具备低开销 metrics、traces、结构化日志、反馈入口和异步抽样；judge 不得阻塞在线请求。
9. ACL/跨租户泄漏、提示注入、错误回答而不拒答等风险有专门测试和零容忍门禁。
10. 所有报告区分 `MEASURED`、`NOT_EXECUTED`、`UNSUPPORTED`、`SKIPPED_EXTERNAL_DEPENDENCY`、`INVALID_RUN`；绝不伪造数字。
11. 快速本地测试不需要付费 API 或完整 1.26GB 数据集；真实 benchmark 在具备数据、数据库和密钥时可一条命令运行。
12. 最终真实验证通过，完成清晰的提交，并在权限和远端状态允许时推送到目标仓库。不得推送到源仓库。

## Why RAG: the product contract

把以下认知写入架构与面试文档，并落实到指标设计中：

- RAG 的核心不是“让模型更聪明”，而是在推理时给模型提供**最新、私有、可授权、可追溯**的外部证据。
- 它解决模型训练知识截止、企业私有数据、事实更新、证据引用和权限隔离问题，并允许只更新索引而不是重新训练模型。
- RAG 不是所有场景都更好。很小且稳定、能完整放入上下文的语料可以直接 full context；创作、翻译、通用推理或模型本来就可靠掌握的问题不一定需要检索。
- RAG 失败不是单一问题。至少要拆成 corpus/ingestion、retrieval/ranking、context construction、generation、safety、system/business 六层，否则“答案错了”无法定位。
- 不能脱离业务风险宣称某个分数“合理”。合理阈值来自业务容错、人工标注基准、当前 baseline、用户 SLO 和回归容忍度。

## Known source state: verify before relying on it

源仓库只读路径：

- `D:\claude\portfolio-rag`
- remote：`https://github.com/Tmakerchima/portfolio-rag`
- 当前检查时分支：`fix/enterprise-bm25-hardening`
- 当前检查时提交：`b2fb134`

目标仓库在 2026-08-14 检查时是空仓库；本地目标目录存在但尚未初始化 Git。执行时必须重新验证，不能假设远端仍为空。

源仓库当前已确认包含：

- Java 21 / Spring Boot / Spring AI 后端；
- EnterpriseRAG 独立的 controller、service、ingestion、retrieval、repository、model；
- PGVector、PostgreSQL FTS、ParadeDB BM25、RRF、可插拔 reranker、query planner、ACL；
- V1-V4 enterprise migrations；
- 可恢复 Python ingestion worker；
- Vue/Vite `enterprise-rag-frontend`；
- 基础 Python `eval`：corpus coverage filtering、Recall/Precision/HitRate/MRR/nDCG、ablation、薄 RAGAS adapter；
- EnterpriseRAG-Bench 适配文档和 ParadeDB 部署脚本。

本次编写提示词时的只读 baseline：

- `mvn -B test`：43 tests，0 failure，0 error，1 skipped；这里混有 Portfolio 测试，抽离后应只保留并扩展 Enterprise 测试。
- `python -m unittest discover -s eval -p 'test_*.py'`：7 tests passed。
- `npm run build`（`enterprise-rag-frontend`）：passed。

源工作树有用户自己的未跟踪 Markdown 文件。不要修改、删除、移动、提交它们。源仓库全程只读；使用 `git ls-files`、显式路径和只读命令获取素材，不要把源 `.git`、build artifacts、数据集、密钥或未跟踪文件复制到目标。

## Source scope and extraction boundary

优先复用并重构这些源内容：

- `portfolio-rag/src/main/java/com/mac/portfolio/enterprise/**`
- Enterprise 所需的主数据源和 AI client 配置，但要移除 Portfolio 耦合；
- `portfolio-rag/src/main/resources/db/migration/V1__...` 到 `V4__...`
- `portfolio-rag/src/test/java/com/mac/portfolio/enterprise/**`
- `enterprise-rag-frontend/**`
- `eval/**` 中 Enterprise worker、benchmark adapter 和评测脚本；
- `docs/enterprise-*`
- `deploy/paradedb/**`
- Enterprise 相关 Docker/Compose/CI 配置。

明确排除：

- `portfolio-frontend/**`
- Resume/Portfolio controllers、services、tools、knowledge、prompts 和 tests；
- `ChatController`、`RagService`、`ResumeContextProvider`、`PortfolioInfoTools`、`AgentToolProvider`、MCP config；
- `classpath:knowledge/**/*`、`interview-system.st`；
- `GITHUB_MCP_PAT`、个人博客抓取、个人站点部署和作品集 UI；
- 旧 `render.yaml` 中的 Portfolio 服务；
- `eval/data` 下的大文件、partial download、真实报告、数据库 dump、`.env` 和 secrets；
- 源仓库中未跟踪的提示词文件。

目标推荐结构如下。若现场约束要求小幅调整可以调整，但必须保持清楚的边界：

```text
EnterpriseRAG/
  backend/
    pom.xml
    src/main/...
    src/test/...
  frontend/
  evaluation/
    pyproject.toml
    src/enterprise_rag_eval/
    tests/
    fixtures/
    schemas/
    reports/.gitkeep
  deploy/
  docs/
  .github/workflows/
  docker-compose.yml
  .env.example
  Makefile or equivalent cross-platform command documentation
  README.md
  EnterpriseRAG.md
```

把 Java package、artifact、应用入口和日志 namespace 从 `com.mac.portfolio...` 改成独立、专业的 EnterpriseRAG 命名，例如 `com.tmakerchima.enterpriserag`。应用入口改为 `EnterpriseRagApplication`。不要为了抽离顺便做大版本框架升级；框架升级如果有必要，单独记录原因、风险和验证结果。

删除后端不再需要的依赖，例如 Portfolio 的 Tika/JSoup/MCP/advisor/tooling；保留 Enterprise 实际调用链所需依赖。不得因为删除依赖而破坏 embedding、chat、WebFlux SSE、JDBC、PGVector/SQL 或 ParadeDB。

所有数据库 URL、用户名、provider base URL、模型名和 CORS origin 均通过配置或环境变量注入。禁止在 `application.yml` 中保留真实 Supabase host、project ref、用户名、密码、token 或固定个人域名。`.env.example` 只放明显的占位值。

## Authorization and safety boundaries

已授权：

- 读取源仓库和目标仓库；
- 在目标仓库中创建、编辑、重命名和删除本任务范围内的文件；
- 安装项目本地依赖；
- 运行无破坏性的 build、unit test、integration test、lint、type check、Docker config validation 和本地 smoke test；
- 初始化目标 Git，创建有意义的 commits；
- 在所有验证通过后，把目标仓库提交推送到 `https://github.com/Tmakerchima/EnterpriseRAG`。

必须先询问用户：

- 任何会产生不可忽略费用的完整 benchmark、LLM judge、大规模 embedding 或 contextualization；
- 下载约 1.26GB 的完整 corpus；
- 连接或修改生产数据库、激活/回滚 production corpus、执行不可逆 migration；
- 修改源仓库或向 `portfolio-rag` 推送；
- 删除非本任务创建的数据或远端分支；
- 目标远端执行时已非空且存在与本地目标冲突的内容。

绝对禁止：

- force push、重写源仓库历史、删除源文件或清理用户未跟踪文件；
- 把 secret、完整私有问题/回答、原始文档内容或数据库 dump 提交到 Git；
- 在没有真实执行时声称 benchmark、集成测试或线上 SLO 已通过；
- 把外部依赖失败记为 0 分或 pass；
- 用同一个模糊“RAG score”掩盖安全失败或某一组件的退化。

## Preflight

先完成并记录到 `docs/baseline.md`：

1. 阅读目标与源目录内所有适用的 `AGENTS.md`。
2. 检查两个目录的 `git status`、branch、remote、HEAD 和目标远端 refs。
3. 列出 Enterprise runtime 的实际依赖图：API -> retrieval -> DB/model -> SSE。
4. 运行或复核源 baseline；如果因外部依赖不能运行，准确记录命令、错误和状态。
5. 使用 `rg` 搜索所有 Portfolio/Resume/MCP/个人域名耦合。
6. 先写一个简短的 extraction map，再开始复制和重构。
7. 如果目标目录尚未初始化且远端为空，保留本文件后执行 `git init -b main` 并添加正确的 `origin`；如果远端不为空，先 fetch 和检查，不覆盖。

## Standalone runtime requirements

抽离后的系统至少保留并验证：

- `POST /api/enterprise/chat` SSE 问答；
- corpus create/activate/rollback 与安全的 admin token 边界；
- health/stats；
- token/structure-aware chunking 和可恢复 worker；
- ACTIVE generation isolation；
- SQL 层 tenant/role/department/access-level ACL；
- vector、lexical、hybrid、hybrid+rerank 策略；
- PostgreSQL FTS 与真正 ParadeDB BM25 的可验证切换和显式 fallback；
- RRF、context budget、per-document chunk cap；
- grounded answer、source IDs、request ID、structured metrics/error frames；
- Docker/Compose 本地依赖和 CI 可运行的 ParadeDB integration path。

让 SSE 协议变成正式、版本化的数据契约，而不是 evaluator 通过脆弱字符串猜测。可以保留向后兼容 marker，但新增清晰 event types，例如 `sources`、`token`、`metrics`、`error`、`done`，并为 parser 写 contract tests。每次请求必须有稳定 `request_id`，尽可能关联 `trace_id`。

## Evaluation architecture

现有 `eval` 不是推倒重写的理由。保留其正确逻辑，重构成有类型、有 schema、有测试、有 CLI、有报告和比较能力的独立 Python package。推荐目录名 `evaluation`、包名 `enterprise_rag_eval`。

使用 `pyproject.toml` 管理依赖并 pin 可复现版本。基础 deterministic evaluation 必须轻量、无付费 API即可运行；将 DeepEval、Ragas、特定 judge provider 放入 optional extras。不要同时用两个框架计算同名指标后平均。

至少提供以下等价 CLI，具体语法可以优化：

```powershell
python -m enterprise_rag_eval dataset validate --cases <cases.jsonl>
python -m enterprise_rag_eval collect --cases <cases.jsonl> --api-base http://localhost:8080 --out <run-dir>
python -m enterprise_rag_eval score retrieval --run <run-dir>
python -m enterprise_rag_eval score generation --run <run-dir> --judge deepeval
python -m enterprise_rag_eval compare --baseline <run-a> --candidate <run-b>
python -m enterprise_rag_eval triage --run <run-dir>
python -m enterprise_rag_eval report --run <run-dir>
python -m enterprise_rag_eval smoke
```

命令必须可组合和断点续跑。collect、deterministic scoring、judge scoring、triage、report 分开，避免一次 judge 失败导致已有 trace 丢失。失败时保留部分结果并返回准确 exit code。

### Canonical case schema

为 JSONL 建立 versioned JSON Schema/Pydantic model，至少包含：

```text
schema_version
case_id
question
language
category / difficulty / source
answerability: ANSWERABLE | UNANSWERABLE | AMBIGUOUS
gold_answer
answer_facts[]
expected_document_ids[]
expected_chunk_ids[] (optional)
forbidden_document_ids[] (for ACL/security)
access_context: role / tenant / department / access_level
expected_behavior / rubric
tags[]
dataset_version
```

不要强迫所有 case 都有 expected document。缺 gold doc 的 case 不能参与 document Recall/MRR/nDCG，但仍可参与拒答、安全或人工 rubric 评测。`partially_supported` 不能混入 fully-supported 主检索指标。

### Canonical trace schema

collect 的每个 case 必须保存足够证据用于复现和归因：

```text
run_id / case_id / request_id / trace_id
question and access context (redacted according to policy)
answer
citations
retrieved candidates and final contexts:
  document_id, chunk_id, rank, score, stage, source_type, title,
  authorized, content_hash, optional redacted content
strategy / lexical_backend / fallback_reason
query rewrites
corpus_id / corpus_generation / dataset_hash
embedding_model / chat_model / reranker / judge_model
prompt_version and prompt_hash
all retrieval and context parameters
stage latency / TTFT / total latency
input/output/context tokens and whether token counts are actual or estimated
estimated cost with currency and pricing-source timestamp, or NOT_AVAILABLE
error class / retry count / HTTP status
code git SHA / dirty flag / dependency lock hash / timestamp
```

建立 `run-manifest.json`。报告没有 manifest 或 dataset/config hash 不一致时标为 `INVALID_RUN`，不得与 baseline 比较。

## Offline metric layers

### 1. Corpus and ingestion quality

至少实现或输出：

- expected/loaded/indexed document count 和 coverage；
- parse failure rate、empty document/chunk rate；
- duplicate content/hash ratio；
- chunk token length p50/p95/max、overlap 和超限数；
- metadata/ACL completeness；
- embedding completion、dimension mismatch、null vector rate；
- stale/deleted document leakage；
- ACTIVE/STAGING generation 一致性；
- source type 和 category 分布漂移。

这些指标决定“gold 文档到底是否在 corpus 中”。gold 不在库里是 data coverage 问题，不能归为 retriever 失败。

### 2. Deterministic retrieval and ranking

在 document level 和必要时 chunk level 分别计算：

- HitRate@K；
- Recall@K；
- Precision@K；
- MRR@K（明确采用第一个 relevant result）；
- nDCG@K（支持多个 relevant docs）；
- MAP@K（有多个相关文档时）；
- no-result rate、authorized-result rate；
- gold-in-candidates 与 gold-in-final-context 两个阶段的 coverage；
- context redundancy、unique-document count、context token utilization。

实现必须正确处理：重复 document IDs、返回数量小于 K、空列表、多个 gold、无 gold、partial corpus、同文档多个 chunk。用手算 fixture 验证公式。聚合结果要有 macro average、case count、分组结果和置信区间，不能只输出平均值。

### 3. Generation, grounding and citation

确定性指标优先：

- exact match / normalized match（适合 ID、日期、短答案）；
- token F1 或 fact coverage；
- citation schema validity；
- citation precision：引用是否来自本次 authorized retrieval；
- citation recall/coverage：应有证据支持的 claim 是否被引用；
- unsupported-claim count；
- answer length、empty answer、error rate；
- abstention confusion matrix、precision、recall、F1。

LLM judge 指标按 case 输出 score、threshold、pass、reason、judge version：

- answer correctness；
- answer completeness / key-fact coverage；
- answer relevancy；
- faithfulness / groundedness；
- contextual relevancy、precision、recall；
- citation entailment；
- appropriate abstention；
- instruction-following / prompt-injection resistance。

不要用 BLEU/ROUGE 作为开放式回答的主要成功标准；它们可作为辅助信息。不要把“答案和 gold 文本措辞不同”直接当作错误。

### 4. Security and authorization

建立专门 golden cases 和硬门禁：

- cross-tenant document leakage rate；
- forbidden document retrieval rate；
- unauthorized citation/claim rate；
- prompt injection compliance rate；
- secret/PII exposure checks；
- 前端自报 role/tenant 是否会绕过服务端权限边界。

安全指标不得与普通质量分平均。任何确认的跨租户或 ACL 泄漏都使 run fail。若现有 API 的 role/tenant 只是 demo 模拟而不是可信认证，文档必须明确指出，不能把它描述成 production authentication。

### 5. Performance, reliability and cost

离线 run 和线上监控都要覆盖：

- latency p50/p95/p99；
- time to first token；
- vector / lexical / fusion / rerank / generation stage latency；
- timeout、rate limit、retry、error、fallback rate；
- throughput/concurrency；
- context/input/output tokens；
- cost per successful answer、cost per request；
- provider/model/backend 分组。

真实 token usage 优先；估算值必须带 `estimated=true`。价格会变化，只能通过带日期和来源的配置计算，不能硬编码后长期冒充真实成本。

## DeepEval and Python eval plugin policy

把 **DeepEval** 作为主要 Python LLM-as-a-judge adapter是可行方案，但它不是整个评测系统。实现时：

1. 先查阅实时官方 DeepEval 文档并做最小 compatibility spike，再 pin 版本；不要直接照抄现有过时 API。
2. 用 DeepEval 的 RAG metrics 或 GEval rubric 评估 answer relevancy、faithfulness、contextual precision/recall/relevancy、correctness、citation 和 abstention。
3. 封装 `JudgeAdapter`，让 deterministic evaluator 不导入 DeepEval；没有 judge key 时仍能完整运行 retrieval、安全和性能评测。
4. judge 默认不在生产请求同步路径执行；离线或异步抽样运行。
5. judge model、provider、temperature、rubric version、prompt hash、framework version、retries 和 raw reason 都写入 manifest/result。
6. judge 输出要求结构化，无法解析或调用失败记 `NOT_EXECUTED`，不得给 0 分。
7. 缓存键至少包含 case input、answer、context hashes、rubric version、judge model 和参数；旧 rubric 缓存不得复用。
8. 尽量使用与被测 generation model 不同的 judge；无法做到时在报告中标明 self-preference 风险。
9. 对 A/B 比较隐藏 system label，随机顺序，并对抽样 case 做 order-swap 检查。
10. 对同一抽样重复 judge 2-3 次，报告稳定性；不要假设一次 judge 是 ground truth。

保留一个薄 Ragas adapter作为可选交叉检查可以接受，但不得让 Ragas 与 DeepEval 产生两套互相冲突的“总分”。默认报告选择一个 primary judge framework，并把另一个结果标记为 supplemental。

## Judge calibration and human labels

建立 `docs/judge-calibration.md` 和可运行的 calibration command：

- 从 answerable/unanswerable、难度、来源、语言、安全类别中分层抽样；
- 由人工按相同 rubric 独立标注，保留 reviewer 和 rubric version；
- 对 pass/fail 报 Cohen's kappa，对序数/连续分数报 weighted kappa 或 Spearman；
- 输出 false positive/false negative 和 judge disagreement cases；
- 如果 agreement 未达约定阈值，judge score 只能作为诊断信号，不能当 release gate；
- 每次 rubric/judge model 大改后重新校准。

不需要伪造人工标签。没有真实双人标注时生成模板、命令和 `NOT_EXECUTED` calibration report。

## Definition of “RAG success rate”

不要输出没有定义的“成功率 90%”。实现并文档化 case-level success：

```text
eligible_case = case schema valid AND required gold/evidence available

hard_gate_pass =
  no ACL/cross-tenant/security violation
  AND request completed without product error
  AND result came from the declared backend/config (no hidden mismatch)

answerable_case_pass =
  hard_gate_pass
  AND required evidence reached final context
  AND answer correctness/completeness passed
  AND faithfulness passed
  AND citation requirements passed

unanswerable_case_pass =
  hard_gate_pass
  AND system abstained appropriately
  AND made no unsupported factual claim

case_success_rate = successful eligible cases / all eligible cases
```

报告 numerator、denominator、excluded reasons、分组结果和 95% Wilson confidence interval。不能用连续指标的简单平均冒充 success rate；可以有 scorecard，但安全失败不能被高平均分抵消。

初始 demo/release gates 可以在配置中提供以下**待校准建议值**，必须标注 `provisional`，先用真实 baseline、人工误差和业务风险校准：

```yaml
security:
  acl_leak_count: 0
  cross_tenant_leak_count: 0
retrieval:
  recall_at_5_min: 0.85
  ndcg_at_10_min: 0.80
generation:
  answer_correctness_min: 0.80
  faithfulness_min: 0.90
  citation_precision_min: 0.95
  abstention_f1_min: 0.85
reliability:
  request_error_rate_max: 0.01
regression:
  recall_at_5_absolute_drop_max: 0.02
  faithfulness_absolute_drop_max: 0.02
  p95_latency_relative_increase_max: 0.20
  cost_per_success_relative_increase_max: 0.15
```

阈值必须是配置，不是散落在代码中的 magic numbers。baseline/candidate 必须使用相同 case IDs、corpus generation、dataset version 和 access context。连续指标使用 bootstrap CI，比例使用 Wilson CI；比较使用 paired bootstrap 或合适的 paired test。固定随机 seed 并记录它。

## Dataset strategy

继续以官方 `onyx-dot-app/EnterpriseRAG-Bench` 为外部 benchmark 来源，但要严格解决 coverage 问题：

- 官方问题集约 500 cases；本地 5,000-document slice 不代表完整 corpus。
- 先从 ACTIVE corpus 导出 `external_id`，把问题分为 fully supported、partially supported、unsupported。
- 主 Recall/MRR/nDCG 只使用 fully supported；其他组单独报告。
- 完整 corpus 和大 zip 不提交 Git；下载脚本校验 URL、release/version、license 和 SHA-256（可获取时）。
- 原始 official benchmark、人工 golden、线上回灌 regression、synthetic security cases 用不同 source 标签，不能混称官方分数。
- 建立 deterministic stratified split/version，防止在 test set 上反复调参；保留 smoke、regression、full benchmark 三个 profile。
- 每个 bad case 晋升到 regression set 前必须人工确认 gold、权限、期望行为和最小复现。

补充覆盖：no-answer、ambiguous、exact identifier、日期/数字、multi-document、long query、中英文、同义词、拼写错误、metadata filter、ACL、stale doc、prompt injection、backend fallback、超时和空 corpus。

## Experiments and ablations

保留并扩展当前 A/B harness。至少支持：

- VECTOR；
- KEYWORD（明确 PostgreSQL FTS 或 ParadeDB BM25）；
- HYBRID；
- HYBRID_RERANK；
- chunk size / overlap；
- top-k / RRF k；
- contextual prefix on/off；
- query expansion on/off；
- reranker mode/model；
- context token budget。

不要无控制地跑笛卡尔积。先固定 corpus、dataset、embedding 和 generation，按阶段做 one-factor 或小型候选搜索，再在冻结 test set 上确认。每个 experiment 保存 config diff、quality delta、latency delta、cost delta、CI 和 bad-case delta。

lexical backend 必须从服务返回的真实 trace 验证。配置要求 BM25 但实际发生 FTS fallback 时标记 `BACKEND_MISMATCH`，不能把结果命名为 BM25 benchmark。

## Online observability

后端加入 Spring Boot Actuator、Micrometer Observation 和 Prometheus-compatible metrics；可选 OTLP tracing。遵循低基数原则：request ID、tenant ID、document ID、question 不得作为 metric tag，只能在受控 trace/log/event store 中出现。

至少实现这些低基数维度：

```text
strategy
lexical_backend
reranker_mode
model
outcome
error_type (bounded enum)
fallback_reason (bounded enum)
role_class (only if privacy review accepts it)
```

至少监控：

- request count、success/error/timeout；
- total duration、TTFT 和各 stage histogram；
- empty retrieval、candidate/final-context count；
- backend mismatch/fallback；
- input/context/output tokens；
- SSE disconnect/cancel；
- feedback positive/negative；
- async judge queue depth/age/failure；
- active corpus generation、document/chunk counts、ingestion lag；
- DB/model/provider availability。

为一次请求建立从 API -> embedding -> vector/lexical -> fusion -> rerank -> context -> LLM -> SSE 的 observation/trace。结构化日志使用同一 request ID/trace ID；不要记录 secret、完整未脱敏 prompt、完整企业文档或个人身份信息。

把 frontend 展示的 per-request metrics 与服务端正式 telemetry 分开：前端信息用于 demo，可观测性数据用于 SLO。线上没有 ground truth 时，empty retrieval、rephrase、thumbs down、abandonment、fallback 只是 proxy signal，不能称作 correctness。

提供 `docs/online-monitoring.md`，包含 Prometheus query/dashboard panels 和建议告警：error rate、p95 latency、TTFT、empty retrieval、fallback/backend mismatch、negative feedback、judge queue lag、ingestion freshness。阈值先标 provisional。

## Interaction, feedback and async evaluation

用 additive migration 设计受控事件表或等价持久化：

- `rag_interactions`：request/trace、config/corpus、stage timings、source IDs、tokens、outcome；
- `rag_feedback`：request、rating、bounded reason、optional redacted comment；
- `rag_evaluation_queue`：sampling reason、status、attempts、next retry；
- `rag_bad_cases`：taxonomy、evidence、owner/status、linked regression case。

默认不持久化完整文档 chunk 和完整用户问题/回答；如确需保存，做显式 opt-in、redaction、retention 和访问控制。tenant/user 只保存不可逆或受控 pseudonymous ID。

增加 feedback API 和最小前端交互，以 `request_id` 关联。验证 payload、限制枚举、避免把任意用户文本直接打入日志。所有 negative feedback、错误、安全信号、backend mismatch 都进入 evaluation queue；正常流量只做可配置的低比例异步抽样。不得在在线响应中同步调用 DeepEval。

## Bad-case taxonomy and attribution

bad case 必须把**症状**与**根因**分开，允许一个 primary cause + 多个 contributing causes，并保存 rule evidence、confidence 和人工 override。

标准 taxonomy 至少包括：

```text
DATASET_GOLD_INVALID
CORPUS_COVERAGE_MISSING
INGEST_PARSE_FAILURE
INGEST_STALE_OR_DUPLICATE
CHUNK_BOUNDARY_OR_SIZE
METADATA_OR_ACL_INDEXING
EMBEDDING_FAILURE_OR_DRIFT
QUERY_AMBIGUOUS
QUERY_REWRITE_FAILURE
RETRIEVAL_NO_RECALL
FILTER_OR_ACL_MISMATCH
LEXICAL_OR_VECTOR_BACKEND_FAILURE
FUSION_OR_RANKING
RERANKER_REGRESSION
CONTEXT_TRUNCATION
CONTEXT_REDUNDANCY_OR_NOISE
GENERATION_INCORRECT
GENERATION_INCOMPLETE
HALLUCINATION_OR_UNFAITHFUL
CITATION_MISSING_OR_UNSUPPORTED
ABSTENTION_FAILURE
PROMPT_INJECTION
TIMEOUT_RATE_LIMIT_PROVIDER
EVALUATOR_OR_JUDGE_FAILURE
UNKNOWN_REQUIRES_REVIEW
```

实现可测试的 deterministic attribution rules，顺序至少表达以下逻辑：

1. case/gold 无效 -> `DATASET_GOLD_INVALID`；
2. expected doc 不在 ACTIVE corpus -> `CORPUS_COVERAGE_MISSING`；
3. expected doc 对当前 access 本来就无权访问 -> 测试配置/ACL expectation 错误，不算 retriever miss；
4. expected doc 未进入任何 candidates -> `RETRIEVAL_NO_RECALL`，并根据 backend/filter/embedding trace补 contributing cause；
5. expected doc 在 candidates 但 fusion/rerank 后消失 -> `FUSION_OR_RANKING` 或 `RERANKER_REGRESSION`；
6. expected evidence 排名足够但被 context budget/per-document cap 截掉 -> `CONTEXT_TRUNCATION`；
7. evidence 已在 final context，答案仍错误/不完整 -> generation cause；
8. final context 无足够证据但模型确定回答 -> `ABSTENTION_FAILURE` + `HALLUCINATION_OR_UNFAITHFUL`；
9. claim 的 citation 不蕴含 claim 或引用未授权/未检索 source -> citation/security cause；
10. backend fallback、timeout、rate limit 是 infrastructure contributing cause；
11. 只有 judge 调用失败 -> `EVALUATOR_OR_JUDGE_FAILURE`，不是 product quality=0。

每个 bad case bundle 至少包括：case、answer、gold/facts、retrieval stages、final context hashes/snippets、citations、metrics、trace、manifest link、自动根因、人工根因、owner、status、created/fixed/verified run IDs、最小复现命令。

状态流转：

```text
NEW -> AUTO_TRIAGED -> CONFIRMED -> FIX_IN_PROGRESS -> FIXED -> VERIFIED -> PROMOTED_TO_REGRESSION
                               \-> DISMISSED_WITH_REASON
```

按 normalized question + access context + expected docs + symptom 做稳定 fingerprint 去重。修复后必须在同一 case 上重跑；只有 pass 且无相邻指标回归才能 VERIFIED。人工确认后把最小 case 晋升到 versioned regression set，并记录来源与隐私处理。

## Reports and interview-facing output

每个 run 生成：

```text
run-manifest.json
cases.jsonl
metrics.json
summary.json
summary.md
report.html (static, local, no server required)
bad-cases.jsonl
bad-case-summary.md
comparison.json / comparison.md (when baseline supplied)
```

报告首页先展示：run validity、corpus/dataset coverage、case count、hard-gate status、case success rate + 95% CI、关键 retrieval/generation/security/performance 指标、baseline delta、top bad-case causes。随后才能展示明细。

任何 demo fixture 报告必须醒目标注 `SYNTHETIC_FIXTURE — NOT A BENCHMARK RESULT`。前端 evaluation 页面只能显示真实、可验证的 latest measured report；没有时显示 `Not measured yet`，不得嵌入示例高分。

新增以下文档：

- `docs/why-rag-and-evaluation.md`
- `docs/evaluation-architecture.md`
- `docs/metrics-and-thresholds.md`
- `docs/dataset-and-run-manifest.md`
- `docs/judge-calibration.md`
- `docs/bad-case-playbook.md`
- `docs/online-monitoring.md`
- `docs/evaluation-runbook.md`
- `docs/interview-guide.md`

`docs/interview-guide.md` 至少给出一份 60 秒版本和一份 3 分钟版本，并包含这个定位矩阵：

| 现象 | 更可能的根因 |
|---|---|
| Recall@K 低 | corpus 缺失、切块、embedding、query、filter、top-k |
| Recall 高但 nDCG/MRR 低 | fusion/ranking/reranker |
| 检索好但 faithfulness/correctness 低 | prompt、context packing、generation model |
| Faithfulness 高但 answer correctness 低 | 证据本身不完整、gold/rubric、答案遗漏 |
| citation precision 低 | claim-citation mapping 或生成不受约束 |
| no-answer recall 低 | 拒答策略过于激进或 evidence threshold |
| 离线好、线上差 | query/corpus drift、fallback、延迟、provider、权限分布 |
| 平均分高但用户差评多 | 分组长尾、指标与业务目标错位、judge 未校准 |

README 要把项目说成一个独立 EnterpriseRAG case study，说明 quickstart、架构、数据边界、评测命令、如何读报告、已真实测量与尚未测量的内容。不得宣传不存在的生产规模或虚假分数。

## Test requirements

### Backend

- 抽离并保持所有 Enterprise unit tests；
- API/SSE contract tests；
- request/trace/metrics instrumentation tests；
- ACL 和 cross-tenant negative tests；
- feedback validation和 redaction tests；
- migration tests；
- ParadeDB BM25 real integration test 保持显式 opt-in；
- fallback/backend mismatch tests；
- cancellation/timeout/error mapping tests。

### Python evaluation

至少覆盖：

- metric formulas 的手算 fixtures；
- duplicate IDs、empty retrieval、short top-k、multi-gold、no-gold、partial corpus；
- SSE parser 的分帧、Unicode、error、disconnect、event version；
- schema validation 与 dataset hash；
- run manifest compatibility；
- Wilson/bootstrap deterministic seed；
- paired compare 和 regression gates；
- judge success、parse failure、retry、cache invalidation、NOT_EXECUTED；
- bad-case rule priority、多标签、fingerprint、human override、regression promotion；
- secret/redaction rules；
- synthetic fixture end-to-end smoke。

### Frontend

- type check/build；
- SSE 新旧协议兼容；
- feedback UI；
- evaluation 状态不伪造结果；
- error/empty/loading/cancel states；
- 不存在个人主页链接或 Portfolio 文案。

### Full validation

尽可能执行并记录真实输出：

```powershell
cd D:\codex\EnterpriseRAG\backend
mvn -B clean test

cd D:\codex\EnterpriseRAG\evaluation
python -m pip install -e ".[dev]"
python -m pytest
python -m enterprise_rag_eval smoke

cd D:\codex\EnterpriseRAG\frontend
npm ci
npm run build

cd D:\codex\EnterpriseRAG
docker compose config
```

如果配置了 integration profile，再运行 Postgres/ParadeDB smoke/integration。未提供 API key、database、full corpus 或 judge key 时，不要调用付费/生产依赖；把相应项准确记为 `NOT_EXECUTED`，同时证明 local fixture path 可用。

## CI and release gates

创建 GitHub Actions：

1. backend unit tests；
2. Python lint/type/test/smoke；
3. frontend install/build；
4. secret and forbidden-scope scan；
5. ParadeDB integration（service container）；
6. fast deterministic regression eval；
7. optional scheduled/manual full benchmark + DeepEval judge，只有配置 secrets/dataset cache 时执行，并上传 report artifacts。

PR gate 使用小而稳定的 regression profile，不调用付费 judge。full judge 作为 scheduled/manual job；其失败状态要区分 product regression 与 external dependency。保存 baseline artifact/version，不在 CI 中悄悄覆盖 baseline。

## Implementation phases and checkpoints

按下面阶段执行。每阶段先检查前一阶段的测试，做小而清晰的 commit；不要等全部完成才第一次验证。

### Phase 0 — Preflight and baseline

- 检查现场、AGENTS、remotes、dirty state、source dependency graph；
- 写 `docs/baseline.md` 和 extraction map；
- 建立目标 Git，但不触碰源 Git。

### Phase 1 — Standalone extraction

- 建立 backend/frontend/evaluation/docs/deploy 结构；
- package/artifact/application rename；
- 删除 Portfolio/MCP/个人站点依赖和 secrets；
- 恢复 backend unit tests、frontend build、compose config。

### Phase 2 — Trace and data contracts

- versioned SSE events；
- canonical case/trace/run schemas；
- request/trace IDs、stage timings、tokens/config/corpus provenance；
- contract tests。

### Phase 3 — Deterministic offline evaluation

- dataset coverage；
- corpus/ingestion checks；
- retrieval/generation/citation/abstention/security/performance metrics；
- confidence intervals、paired compare、release gates；
- synthetic end-to-end smoke。

### Phase 4 — DeepEval judge

- official-doc compatibility spike；
- adapter、rubrics、cache、retry、structured result、NOT_EXECUTED；
- calibration workflow；
- optional Ragas supplemental adapter only if it remains valuable。

### Phase 5 — Online observability and feedback

- Micrometer/Prometheus/trace；
- interaction/feedback/queue migrations；
- feedback API/UI；
- async sampling interface；
- privacy/redaction/retention documentation。

### Phase 6 — Bad-case loop and reports

- taxonomy/rules/fingerprint/status；
- triage CLI、report、minimal reproducer；
- verified regression promotion；
- static HTML/Markdown report。

### Phase 7 — CI, docs and final audit

- workflows、runbooks、interview guide；
- full local validation；
- forbidden-scope and secret scan；
- clean Git status；
- commits and target push。

## Stop and retry rules

- 对普通本地依赖/编译问题，定位并修复后重试；不要因一次失败就停在计划阶段。
- 对外部 provider、数据库或网络失败，最多做少量有意义的安全重试，然后标记外部依赖状态。
- 不要为了让 CI 变绿而 skip 产品逻辑、吞异常、降低安全门禁或写死 fake result。
- 如果真实数据不可用，完成 schemas、fixtures、tests、CLI 和 NOT_EXECUTED path，不能据此声称真实 benchmark 完成。
- 只有涉及费用、生产数据、远端冲突、权限或破坏性操作时才停下来问用户。

## Git policy

- 源目录全程只读，不在源仓库创建 branch/commit/stash。
- 目标 `origin` 必须是 `https://github.com/Tmakerchima/EnterpriseRAG`。
- 目标远端为空时可建立 `main` 初始历史；远端非空时先 fetch，禁止覆盖。
- 不 force push，不提交 generated reports（除 `.gitkeep` 和明确标注的 tiny synthetic fixture）、大数据、`.env`、DB dump、build output 或 secrets。
- 使用语义明确的小提交，例如 extraction、evaluation core、observability、bad-case loop、docs/CI。
- push 前再次运行 secret scan、forbidden-scope scan、tests，并输出 `git diff --check`、`git status`、`git log --oneline`。

建议 forbidden-scope audit（允许 migration/baseline 文档中必要的来源说明，但 runtime/UI 必须为零）：

```powershell
rg -n -i "portfolio-frontend|ResumeContextProvider|PortfolioInfoTools|AgentToolProvider|GITHUB_MCP_PAT|tmakerchima\.cn|about-mac|interview-system" D:\codex\EnterpriseRAG
rg -n "com\.mac\.portfolio" D:\codex\EnterpriseRAG\backend D:\codex\EnterpriseRAG\frontend
```

## Definition of Done

只有以下条件全部满足才算完成：

- [ ] 目标是独立 EnterpriseRAG repository，源目录未被修改；
- [ ] backend package/artifact/app/config 已去 Portfolio 化；
- [ ] frontend 无个人主页依赖；
- [ ] Python evaluator 是可安装、可测试、可组合 CLI package；
- [ ] corpus/retrieval/generation/citation/abstention/security/performance/cost 指标分层；
- [ ] success rate 有严格分母、hard gates 和 95% CI；
- [ ] DeepEval adapter、failure state、cache、rubric、version 和 calibration workflow 完整；
- [ ] 线上 metrics/traces/logs/feedback/async queue 完整且不在请求路径同步 judge；
- [ ] bad-case taxonomy、deterministic attribution、human override、状态机、回归晋升可运行；
- [ ] reports 和 baseline comparison 可生成；
- [ ] ACL/security 零容忍门禁；
- [ ] CI fast regression 不依赖付费 API；
- [ ] README、runbooks 和面试讲解完整；
- [ ] backend tests、Python tests/smoke、frontend build、compose validation 通过；
- [ ] 未执行的真实 benchmark/judge/integration 被准确标记，没有假数字；
- [ ] secret/forbidden-scope audit 通过；
- [ ] commits 清晰，目标 push 成功或给出唯一、具体的外部 blocker。

## Final report format

最终用中文给出简洁但可审计的报告：

1. Outcome：独立仓库和评测闭环完成了什么；
2. Extraction：保留/删除/重命名了什么；
3. Evaluation：数据契约、指标、DeepEval、success rate、门禁；
4. Online：metrics/traces/feedback/async sampling；
5. Bad cases：归因和回灌流程；
6. Real validation：逐条命令、真实 pass/fail/skip 数；
7. Measured vs Not Executed：哪些是真实数据，哪些缺外部条件；
8. Git：remote、branch、commits、push 状态；
9. Run next：运行本地 smoke、完整 benchmark、judge calibration 的确切命令；
10. Interview：给项目所有者一段可以直接口述的 60 秒总结。

## Authoritative references to verify during implementation

只用实时官方/一手文档确认会变化的 API和版本：

- GPT-5.6 Luna：`https://developers.openai.com/api/docs/models/gpt-5.6-luna`
- GPT-5.6 model/prompt guidance：`https://developers.openai.com/api/docs/guides/latest-model`
- DeepEval RAG evaluation：`https://deepeval.com/tutorials/rag-qa-agent/evaluation`
- DeepEval faithfulness：`https://deepeval.com/docs/metrics-faithfulness`
- Ragas metrics（supplemental only）：`https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/`
- Spring Boot observability：`https://docs.spring.io/spring-boot/reference/actuator/observability.html`
- OpenTelemetry GenAI semantic conventions：`https://github.com/open-telemetry/semantic-conventions-genai/tree/main/docs/gen-ai`
- EnterpriseRAG-Bench：`https://github.com/onyx-dot-app/EnterpriseRAG-Bench`
- EnterpriseRAG-Bench dataset：`https://huggingface.co/datasets/onyx-dot-app/EnterpriseRAG-Bench`

## Start now

现在开始执行。不要只返回计划或代码片段。先完成 preflight，随后持续实现、测试、修复、文档化和提交，直到 Definition of Done，或遇到必须由用户授权的费用/生产/远端冲突阻塞。
