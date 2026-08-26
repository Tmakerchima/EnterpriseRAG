# 切片池 API

切片池用于两件事：

1. 网页搜索和浏览当前 ACTIVE corpus 中有权限查看的切片；
2. 用户展开回答来源时，按 `chunk_id` 读取完整的可引用原文。

## 列表

```http
GET /api/enterprise/chunks?role=engineering&tenantId=default&q=upload&page=0&size=12
Accept: application/json
```

参数：

- `role`：演示角色，支持 `public`、`engineering`、`finance`、`hr`、`admin`；
- `tenantId`：演示 tenant，默认 `default`；
- `q`：可选，最长使用前 200 字；正文/检索上下文走 PostgreSQL GIN 全文索引，标题和 external ID 补充匹配；
- `page`：从 0 开始，最大 10,000；
- `size`：默认 12，最大 50。

响应：

```json
{
  "items": [
    {
      "chunkId": "github:upload-guide:0",
      "documentId": "doc-123",
      "externalId": "upload-guide.md",
      "source": "github",
      "sourceType": "github",
      "title": "Upload guide",
      "content": "Complete original citable text...",
      "chunkIndex": 0,
      "tokenCount": 312,
      "embedded": true,
      "department": "engineering",
      "accessLevel": "internal",
      "corpusVersion": "v2.0.0",
      "metadata": {}
    }
  ],
  "page": 0,
  "size": 12,
  "total": 1,
  "totalPages": 1,
  "query": "upload"
}
```

## 单条完整原文

```http
GET /api/enterprise/chunks/github%3Aupload-guide%3A0?role=engineering&tenantId=default
Accept: application/json
```

有权限时返回同一个 chunk 对象；不存在或无权限时都返回 `404`，避免泄漏私有 chunk 是否存在。

## 安全边界

- 只读取 ACTIVE corpus；
- 复用在线 vector/FTS 检索的 tenant/ACL SQL；
- 只返回原始可引用 `content`，不返回 embedding、`contextual_prefix` 或 `index_content`；
- API 是只读的；
- 当前请求参数是演示身份。生产环境必须从已验证的 SSO/OIDC claims 生成访问上下文。

## 查询与切片逻辑

- 导入器先按 Markdown 标题、段落和 fenced code block 形成语义块，再按 `cl100k_base` token 合并/拆分；默认上限 700 tokens，同一章节相邻切片最多重叠 80 tokens。
- `content` 是可引用原文；`contextual_prefix + content` 构成 `index_content`，后者只用于 Embedding 和全文检索，不能作为引用证据展示。
- 列表查询先锁定唯一 ACTIVE corpus，再在 SQL 中执行 tenant / department / access level 过滤。
- 空查询按文档标题、切片序号分页；关键词查询使用 `search_vector @@ websearch_to_tsquery` 和 GIN 索引，标题或 external ID 命中具有更高优先级。
- 正常分页使用 `count(*) OVER()` 一次返回页面和总数，避免旧实现为 COUNT 与页面各扫描一次、产生两次远程数据库往返。
