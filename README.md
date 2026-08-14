# EnterpriseRAG

EnterpriseRAG 是一个可独立构建、运行和审计的企业知识库 RAG case study。它把 versioned corpus、SQL 层 ACL、向量/词法/混合召回、重排、grounded SSE、线上 telemetry、反馈异步评测和 bad-case 回灌放在同一条可复现闭环中。

仓库不包含完整企业语料、私有问题答案、数据库 dump、密钥或真实 benchmark 报告。没有外部数据库、模型或 benchmark 时，报告会明确显示 `NOT_EXECUTED` / `SKIPPED_EXTERNAL_DEPENDENCY`，不会用 fixture 数字冒充真实结果。

## Quickstart

```powershell
Copy-Item .env.example .env
docker compose up -d postgres

cd backend
mvn -B test

cd ..\evaluation
python -m pip install -e ".[dev]"
python -m pytest
python -m enterprise_rag_eval smoke

cd ..\frontend
npm ci
npm run build
```

应用启动前需要把 `backend/src/main/resources/db/migration/V1__...` 到 `V5__...` 以受控方式应用到目标 PostgreSQL。`POST /api/enterprise/chat` 返回版本化 SSE：`sources`、`token`、`metrics`、`error`、`done`；每个请求都有 `request_id` 和 `trace_id`。管理员入库、corpus activate/rollback 和反馈接口见 [evaluation runbook](docs/evaluation-runbook.md)。

## Architecture and evaluation

- 后端：Java 21 / Spring Boot WebFlux / Spring AI / PostgreSQL + PGVector；ParadeDB BM25 是显式可切换的 lexical backend，失败时 trace 会记录 fallback。
- 前端：Vue 3 + TypeScript，只展示服务端真实返回的来源和 metrics；没有 measured report 时显示 `Not measured yet`。
- 评测：`evaluation` 是可安装 Python package。确定性 retrieval/generation/citation/security/performance 指标与可选 DeepEval judge 分离。
- 闭环：run manifest → 分层指标 → baseline/candidate compare → deterministic triage → 人工确认 → regression promotion。

常用命令：

```powershell
cd evaluation
python -m enterprise_rag_eval dataset validate --cases fixtures/smoke-cases.jsonl
python -m enterprise_rag_eval collect --cases <cases.jsonl> --api-base http://localhost:8080 --out reports/<run-id>
python -m enterprise_rag_eval score retrieval --run reports/<run-id>
python -m enterprise_rag_eval score generation --run reports/<run-id> --judge none
python -m enterprise_rag_eval triage --run reports/<run-id>
python -m enterprise_rag_eval report --run reports/<run-id>
```

`fixtures/smoke-cases.jsonl` 仅用于本地路径验证，报告必须标记 `SYNTHETIC_FIXTURE — NOT A BENCHMARK RESULT`。官方 EnterpriseRAG-Bench 的下载、corpus coverage 分类和 full benchmark 命令见 [dataset-and-run-manifest](docs/dataset-and-run-manifest.md)。

## What is measured

确定性报告包含 corpus coverage、HitRate/Recall/Precision/MRR/nDCG/MAP、gold-in-candidates、gold-in-final-context、答案 exact/token-F1/fact coverage、citation schema、拒答和运行错误。比例指标提供 Wilson 95% CI；分层聚合使用 case count，安全门禁单独判断。DeepEval 是 optional primary judge adapter，没有 key 时结果为 `NOT_EXECUTED`，不是 0 分。

详细设计、阈值、隐私边界、监控面板和面试口径见 `docs/`。真实 benchmark、真实 judge、数据库 integration 和线上 SLO 在本工作区没有执行，除非相应外部依赖明确配置。
