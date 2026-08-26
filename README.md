# EnterpriseRAG

一个可以看见“答案为什么这样说”的企业知识库 RAG 示例。

它只做一条清晰的主链路：

```text
文档 → 切片 → 向量 + 关键词检索 → 权限过滤 → 选出证据 → 大模型回答
```

## 先说结论：现在能直接给企业用吗？

**不能直接把它接上真实敏感数据后上线。** 当前仓库是生产化参考实现，不是开箱即用的企业 SaaS。

已经具备的生产基础：

- PostgreSQL SQL 层 tenant/ACL 过滤；
- PGVector + PostgreSQL FTS，可选 ParadeDB BM25；
- 语料版本、STAGING/ACTIVE 切换和回滚；
- 有证据才调用模型，没有证据时确定性拒答；
- request_id、trace、指标、反馈和离线评测；
- 可分页查看切片，并按同一套 ACL 读取完整引用原文。

上线前必须补齐的部分：

- 企业 SSO/OIDC，并由服务端身份 claims 生成 tenant 和角色；
- API 网关限流、WAF、TLS、密钥托管、审计告警和备份恢复演练；
- 受控数据库迁移；
- 用企业自己的问题、文档和权限做完整评测、人工验收与压力测试。

当前仓库内报告也**不支持发布结论**：只覆盖 24/500 道题，Recall@5 为 70.8%（门槛 85%），nDCG@10 为 61.0%（门槛 80%），语义 Judge 未执行。完整判断见 [生产可用性审查](docs/production-readiness.md)。

## 初学者先看这三个页面

- **问答**：提问、选择演示角色，查看答案和完整引用切片。
- **切片池**：搜索 ACTIVE 语料的切片，理解文档如何变成模型上下文。
- **评测**：默认只回答“能找到资料吗、回答可靠吗、会越权吗、要等多久”；Recall、nDCG 等专业指标放在折叠区。

> 页面里的角色选择器只用于演示 ACL。生产环境不能相信浏览器传入的 `role` 或 `tenantId`。

## 项目只有四块

| 目录 | 作用 | 初学者从哪里看 |
| --- | --- | --- |
| `backend/` | 在线检索、问答、切片 API | `EnterpriseChatService`、`EnterpriseChunkController` |
| `frontend/` | Vue 网页 | `App.vue`、`ChunkPool.vue`、`EvaluationDashboard.vue` |
| `evaluation/` | 离线评测和批量入库 worker | `README.md`、`enterprise_rag_worker.py` |
| `docs/` | 部署、评测和生产清单 | `simplified-architecture.md` |

## 一条命令跑完整测试

Windows PowerShell：

```powershell
.\scripts\test-all.ps1
```

它会执行与 CI 相同的后端测试、Python 测试与 lint、合成 smoke 评测和前端构建；已安装 Docker 时还会检查 Compose 配置。合成 smoke 只验证程序链路，不能当作 benchmark 成绩。

也可以分开执行：

```powershell
cd backend
mvn -B test

cd ..\evaluation
python -m pip install -e ".[dev]"
python -m pytest
python -m ruff check src tests
python -m enterprise_rag_eval smoke

cd ..\frontend
npm ci
npm run build
```

## 本地启动

要求：Java 21、Maven、Python 3.11+、Node.js 22+、Docker。

1. 启动 PostgreSQL：

   ```powershell
   docker compose up -d postgres
   ```

2. 按顺序应用 `backend/src/main/resources/db/migration/V1__...` 到 `V5__...`。本地可使用：

   ```powershell
   Get-ChildItem backend/src/main/resources/db/migration/*.sql |
     Sort-Object Name |
     ForEach-Object { Get-Content -Raw $_ | docker compose exec -T postgres psql -U postgres -d enterprise_rag }
   ```

3. 复制环境变量并填写真实模型配置：

   ```powershell
   Copy-Item .env.example .env
   ```

4. 使用唯一入库入口做一次 dry-run；确认数据、许可证和配置后再执行真实入库：

   ```powershell
   python evaluation/enterprise_rag_worker.py --archive <documents.zip> --dry-run
   ```

5. 启动后端和前端：

   ```powershell
   cd backend
   mvn spring-boot:run
   ```

   新开一个 PowerShell：

   ```powershell
   cd frontend
   Copy-Item .env.example .env.local
   # 把 .env.local 中的 VITE_API_BASE_URL 改成 http://localhost:8080
   npm ci
   npm run dev
   ```

## 切片 API

```http
GET /api/enterprise/chunks?role=engineering&tenantId=default&q=upload&page=0&size=12
GET /api/enterprise/chunks/{chunkId}?role=engineering&tenantId=default
```

列表有分页，单条接口返回完整可引用 `content`。两个接口都只读 ACTIVE corpus，并复用在线检索的 tenant/ACL SQL；不会返回 embedding、`contextual_prefix` 或 `index_content`。请求与响应示例见 [切片 API 文档](docs/chunk-api.md)。

## 最简单的评测理解

1. **Recall@5**：正确证据有没有进前 5 条。
2. **Faithfulness**：回答有没有忠于找到的证据；需要 LLM Judge 或人工判断。
3. **ACL gate**：有没有检索到无权查看的切片；失败就阻断发布。
4. **P95**：95% 请求能在多久内完成。

评测命令、数据边界和报告发布方式见 [evaluation/README.md](evaluation/README.md)。架构细节见 [简化架构](docs/simplified-architecture.md)。
