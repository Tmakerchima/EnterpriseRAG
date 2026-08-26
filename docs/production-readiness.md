# 生产可用性审查

审查日期：2026-08-26。

## 结论

当前项目适合作为企业 RAG 的生产化参考实现和预发布 PoC，**不适合未经补强就承载真实敏感数据**。

这不是因为 RAG 主链路缺失。相反，检索、ACL、语料版本、拒答、引用、观测、反馈和离线评测已经形成闭环。主要差距在企业身份、安全运营、真实数据验证和平台可靠性。

## 当前报告说明了什么

仓库发布的 `frontend/public/evaluation/latest.json` 是 5,000 份 GitHub 文档上的有限切片：

| 项目 | 当前值 | 当前门槛/解释 | 结论 |
| --- | ---: | --- | --- |
| 有效问题 | 24 / 500 | 只评价完整覆盖题 | 范围太小 |
| Recall@5 | 70.8% | ≥ 85% | 未通过 |
| nDCG@10 | 61.0% | ≥ 80% | 未通过 |
| Faithfulness | 未执行 | 需 LLM Judge 或人工复核 | 无法判断回答语义正确性 |
| ACL hard gate | PASS | 不允许越权 | 当前样本通过 |
| P95 | 6.385 秒 | 尚未定义业务 SLO | 需要业务确认和压测 |

因此当前报告不能证明系统已经生产可用，也不能代表完整 EnterpriseRAG-Bench 或真实企业业务。

## 已具备的生产基础

- 所有检索分支先在 SQL 层限定 ACTIVE corpus、tenant、department 和 access level；
- 管理员仍受 tenant 边界约束；
- 无授权证据时不调用 LLM，直接拒答；
- 引用原文与只用于检索的 contextual prefix 分开保存；
- STAGING 语料需要显式 activate，支持 rollback；
- BM25 和 reranker 失败有显式 fallback 与指标；
- 不持久化完整 prompt/answer，交互 trace 只保存脱敏 ID 和阶段耗时；
- 离线评测把检索、回答、安全、性能分层，安全门禁不会被平均分掩盖。

## 上线阻断项

### 1. 身份不能来自浏览器

当前 `role` 和 `tenantId` 是为了演示而由请求传入。生产必须接入 OIDC/SAML/企业 SSO，在 API 网关或 Spring Security 验证 token，再从服务端可信 claims 构造 `EnterpriseAccessContext`。前端参数只能作为 UI 展示，不能参与授权决策。

### 2. 平台安全与运营

- 在网关配置 TLS、限流、请求体限制、WAF 和 DDoS 防护；
- 密钥进入云 Secret Manager/Vault，并配置轮换；
- 管理接口使用短期服务身份或 mTLS，不长期依赖共享 token；
- 对 ACL 拒绝、跨 tenant 尝试、异常下载量建立告警；
- 明确日志保留、隐私删除、数据驻留和合规策略。

### 3. 数据库与恢复

- 使用 Flyway/Liquibase 或平台变更流程管理 V1-V5，不在生产手工粘贴 SQL；
- 对 corpus 激活、回滚和 schema 变更设置审批；
- 验证备份恢复、RPO/RTO、连接池上限和慢查询；
- 切片池的正文搜索是运维查看功能，大规模语料应增加 trigram/专用搜索索引或限制为管理员工具。

### 4. 真实质量门禁

- 建立企业自己的分层问题集，覆盖部门、权限、拒答、时效和提示注入；
- 运行完整 retrieval + generation + security + performance 评测；
- 对语义 Judge 做人工校准，不能直接相信单一模型评分；
- 以真实并发测试 P50/P95/P99、错误率、fallback 率和成本；
- 候选版本必须与已批准 baseline 比较，失败 case 进入回归集。

## 建议的上线验收

以下只是起始模板，最终阈值应由业务风险决定：

- 0 个跨 tenant 或 forbidden chunk 泄漏；
- Recall@5 ≥ 85%，nDCG@10 ≥ 80%；
- 经校准的 Faithfulness ≥ 90%；
- 关键拒答/提示注入 case 100% 通过；
- 目标并发下 P95、错误率和模型成本满足业务 SLO；
- SSO、审计、限流、密钥轮换、备份恢复和回滚演练全部完成。

达到这些条件后，项目才可以从“参考实现”进入受控试点；试点通过后才能逐步扩大真实用户范围。
