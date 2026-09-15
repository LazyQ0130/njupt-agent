# 南邮智答 Phase 7 API

Base URL：`http://localhost:8080`

统一响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

业务异常仍使用统一结构。HTTP 状态码表达传输结果，`code` 表达具体业务错误。

## 1. AI 问答

`POST /api/chat/ask`

必填请求头：`X-Anonymous-Session-Id: <uuid>`。UUID 由
`POST /api/session/anonymous` 签发，学生无需登录。

请求：

```json
{
  "question": "南邮转专业需要什么条件"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "根据南京邮电大学本科生转专业管理相关规定……",
    "sources": [
      {
        "title": "本科生转专业管理办法.pdf",
        "type": "OFFICIAL_WEBSITE",
        "page": 3,
        "score": 0.91,
        "source": "南京邮电大学教务处",
        "url": "https://jwc.njupt.edu.cn/example/page.htm",
        "category": "ACADEMIC"
      }
    ],
    "confidence": 95
  }
}
```

## 2. 历史聊天

`GET /api/chat/history`

必填请求头：`X-Anonymous-Session-Id: <uuid>`。只返回当前匿名会话的数据。

## 3. 知识库文件列表

`GET /api/documents`

按上传时间倒序返回文档元数据，不返回全文内容和服务器存储路径。

## 4. 文档详情

`GET /api/documents/{id}`

返回指定文档的元数据与 `content` 字段。当前阶段上传后尚未解析，因此
`content` 通常为 `null`；不存在的 ID 返回 HTTP 404 与业务码 `40401`。

## 4.1 人工核验的官方知识

`POST /api/admin/documents/curated`

必填请求头：`Authorization: Bearer <admin-jwt>`。官方 HTTPS 来源网址是幂等键；
再次提交同一网址会更新原文档，并以人工正文替换同网址的爬虫网页原文。

```json
{
  "title": "本科生选课与退课规则",
  "content": "结论：……\n适用对象：……\n流程：……",
  "source": "本科生院",
  "sourceUrl": "https://jwc.njupt.edu.cn/example/page.htm",
  "category": "ACADEMIC",
  "publishedTime": "2026-04-17T00:00:00+08:00",
  "verifiedTime": "2026-07-27T00:00:00+08:00"
}
```

`publishedTime` 可省略，`verifiedTime` 必填。条目保存为
`CURATED_OFFICIAL`，检索时优先于普通官网原文。

## 4.2 单文档删除

`DELETE /api/admin/documents/{id}?suppressReingest=true`

接口只删除一个明确 ID。系统先删除向量文档；向量删除失败时保留数据库记录和上传
文件。`suppressReingest=true` 时，官网来源网址会加入排除清单，后续爬虫返回
`EXCLUDED`，强制重建也不会恢复。

## 5. 管理员上传知识库文件

`POST /api/admin/documents/upload`

必填请求头：`Authorization: Bearer <admin-jwt>`。

Content-Type：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | File | 是 | PDF、DOC 或 DOCX，默认最大 50 MB |
| `title` | String | 否 | 为空时使用不含扩展名的文件名 |
| `source` | String | 否 | 来源部门，默认“未知来源” |

示例：

```bash
curl -X POST "http://localhost:8080/api/admin/documents/upload" \
  -F "file=@./本科生转专业管理办法.pdf" \
  -F "title=本科生转专业管理办法" \
  -F "source=教务处"
```

文件落盘和元数据写入后状态为 `PROCESSING`。事务提交后自动发送至 Knowledge
Service 解析并写入 Chroma；成功更新为 `COMPLETED`，失败更新为 `FAILED`。

## 业务错误码

| code | 说明 |
| --- | --- |
| `400` | 参数校验失败 |
| `40001` | 文件为空 |
| `40002` | 文件类型不支持 |
| `40003` | 文件超过大小限制 |
| `40401` | 文档不存在 |
| `50201` | Knowledge Service 返回异常 |
| `50202` | DeepSeek 返回异常 |
| `50301` | Knowledge Service 不可用 |
| `50302` | DeepSeek 不可用、网络超时或未配置 Key |
| `50001` | 文件保存失败 |
| `50002` | JSON 序列化失败 |
| `500` | 未处理的服务器异常 |

## 6. RAG 检索代理

`POST /api/rag/search`

Spring Boot 通过 `RagClient` 调用 Knowledge Service 的 `/rag/search`。该接口
仍只返回检索片段，`/api/chat/ask` 在应用服务层继续完成 Prompt 和答案生成。

请求：

```json
{
  "question": "南邮转专业条件",
  "top_k": 5
}
```

响应使用 Spring Boot 统一信封：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "content": "学生申请转专业应关注教务处当年度通知……",
      "filename": "本科生转专业管理办法.pdf",
      "page": 3,
      "source": "教务处",
      "source_url": "https://jwc.njupt.edu.cn/example/page.htm",
      "source_type": "OFFICIAL_WEBSITE",
      "category": "ACADEMIC",
      "score": 0.93
    }
  ]
}
```

## 7. 置信度规则

置信度完全由最高检索 `score` 计算，不采信模型自报值：

| 最高 score | confidence |
| --- | --- |
| `>= 0.85` | `95` |
| `>= 0.70` | `85` |
| `< 0.70` | `round(score × 100)`，最高 `59` |

当所有检索结果低于 `RAG_MIN_RELEVANCE_SCORE` 时，不调用 DeepSeek，返回
“知识库中暂无相关信息，请咨询相关部门。”、空来源和 `confidence: 0`。

## 8. 采集管理

前端管理页 `/admin/crawler` 使用以下 Spring 代理接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/crawler` | 采集状态、总网页数、最近任务 |
| `POST` | `/api/admin/crawler/run` | 启动增量采集 |
| `POST` | `/api/admin/crawler/custom` | 提交南邮官方栏目网址并按近 N 年定向采集 |
| `POST` | `/api/admin/crawler/reindex` | 强制重新索引已发现页面 |

定向采集请求：

```json
{
  "seed_url": "https://www.njupt.edu.cn/jxjg/list.htm",
  "date_scope": "RECENT",
  "crawl_scope": "DIRECT_NJUPT_SUBDOMAINS",
  "years": 2,
  "max_pages": 50,
  "force_reindex": false
}
```

Spring 与 Crawler Service 都会校验 HTTPS、`*.njupt.edu.cn` 官方子域名、端口和
用户信息。`crawl_scope` 默认为 `EXACT_HOST`，任务只跟随提交网址的精确主机；
管理员也可显式选择 `DIRECT_NJUPT_SUBDOMAINS`，从起始页正文发现直接链接的南邮
子域名。扩展模式最多处理 30 个站点（包含起始站点）、每站最多 `max_pages`
个页面且服务端硬限制为 50；进入学院站后仍只跟随该精确主机，不继续跨站扩散。

默认 `date_scope` 为 `RECENT`，任务跳过早于时间边界或无法验证发布日期的页面；
`ALL` 会允许旧页面和无可靠发布日期的合格页面。
当后端数据与爬虫增量状态不一致，或需要覆盖已有向量时，可将
`force_reindex` 设为 `true`。

接口保持 Spring 统一响应结构；Crawler Service 不直接暴露给浏览器。

## 9. Crawler 内部入库

`POST /api/internal/crawler/documents`

必须携带 `X-Crawler-Token`。服务会再次验证 `source_url` 为允许域名的 HTTPS
地址，以 URL 唯一键和 `content_hash` 实现幂等更新：

- 新 URL：`action = CREATED`
- URL 相同且 hash 未变：`action = UNCHANGED`，不触发 Embedding
- hash 变化：`action = UPDATED`
- 强制重建：`action = REINDEXED`

成功入库后通过事务提交事件调用 Knowledge Service `/documents/index-text`，
索引成功将 Document 状态更新为 `COMPLETED`。

## 10. 多轮会话

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/chat/conversation` | 创建当前匿名访客的会话 |
| `POST` | `/api/chat/message` | 在会话内发送消息 |
| `GET` | `/api/chat/conversation/{id}` | 获取会话和完整消息 |

发送消息请求：

```json
{
  "conversationId": 12,
  "question": "大一应该怎么学习？"
}
```

响应包含 `conversationId`、`messageId`、`chatId`、`answer`、`sources`、
`confidence` 和 `createdTime`。模型上下文默认只保留最近 12 条、最多 6000
字符，且历史仅用于理解指代，事实仍必须来自知识库。

## 11. 回答反馈

`POST /api/chat/feedback`

```json
{
  "chatId": 108,
  "feedback": "INCORRECT",
  "reason": "缺少当年度申请时间"
}
```

`feedback` 仅允许 `HELPFUL` 或 `INCORRECT`。同一 `chatId` 重复提交会更新
原记录，不重复计数。

## 12. AI 质量统计

`GET /api/admin/quality/stats`

返回总回答数、去重匿名用户数、反馈数、好评/差评数、好评率、低置信度回答数、
低置信度差评数、前 10 个高频问题和前 10 个低质量问题簇。

## 13. RAG 自动评测

`POST /api/admin/evaluation/run`

读取 `resources/evaluation/questions.json` 的全部问题并逐条执行当前 Provider，
结果写入 `evaluation_result`。单题分数：

- 有来源：35 分
- 预期来源匹配：30 分
- 预期答案关键词命中：25 分
- 非空且非兜底回答：10 分

返回总平均分、来源覆盖率、来源匹配率、关键词命中率、非空率、人工准确率、
评测门禁结果、分类平均分和逐题明细。模型调用次数与问题集当前总数一致，接口
仅允许管理员访问。门禁要求非空率至少 98%、来源覆盖率至少 95%、来源匹配率
至少 85%、人工准确率至少 90%；未完成人工复核时门禁保持未通过。

## 14. 分类优先检索

Spring 在检索前按关键词判断 `NEW_STUDENT`、`ACADEMIC`、`LIFE`、`MAJOR`、
`CAREER`、`SCHOOL_OVERVIEW`、`ORGANIZATION` 或 `RESEARCH`，向 Knowledge Service
传递分类。Chroma 先检索该分类，再用全库
结果补足 Top K；无分类时保持原向量检索逻辑。

## Phase 6 错误码

| code | 说明 |
| --- | --- |
| `40402` | 会话不存在或不属于当前用户 |
| `40403` | 反馈对应的回答不存在 |

## 15. 匿名会话

`POST /api/session/anonymous`

无需请求体或登录。返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "anonymousSessionId": "e3e085a2-ebd7-4cbf-a109-d4dc9b2c0905",
    "expiresAt": "2026-10-24T12:00:00"
  }
}
```

所有 `/api/chat/**` 请求都必须携带该 UUID。会话默认保存 90 天，访问会话时
刷新有效期；不同 UUID 读取同一 `conversationId` 返回 40402。

## 16. 管理员认证

`POST /api/admin/auth/login`

```json
{"username":"admin","password":"<password>"}
```

成功返回 `accessToken`、`tokenType: Bearer` 和 `expiresIn`。除登录外，所有
`/api/admin/**` 都要求带有 `ADMIN` 角色的 Bearer JWT。学生端不使用此接口。

## 17. 操作审计

`GET /api/admin/operations`

返回最近 100 条管理员操作。当前记录 `DOCUMENT_UPLOAD`、`CRAWLER_RUN`、
`CRAWLER_REINDEX`、`EVALUATION_RUN` 和 `EVALUATION_REVIEW`。

## 18. 评测报告与人工复核

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/evaluation/{runId}` | 获取某个评测批次报告 |
| `PUT` | `/api/admin/evaluation/results/{id}/review` | 标记人工准确性 |

复核请求：

```json
{"accurate":true,"note":"政策条件和引用来源一致"}
```

报告额外返回 `humanReviewedCount`、`humanAccuracyRate`、`gatePassed` 和
`gateFailures`；尚未人工复核时准确率为 `null`，不会用自动规则分数冒充人工准确率。

## Phase 7 错误码

| code | HTTP | 说明 |
| --- | --- | --- |
| `40102` | 401 | 匿名会话缺失或 UUID 无效 |
| `40103` | 401 | 管理员凭据或令牌无效 |
| `40301` | 403 | 不具备管理员权限 |
| `42901` | 429 | IP 或匿名会话超过限流 |
## 19. AI 服务设置

以下接口均需要管理员 JWT，且返回统一 `ApiResponse`。

- `GET /api/admin/ai/config`：返回配置来源、启用状态、Key 掩码、模型、验证时间与加密存储可用性。
- `PUT /api/admin/ai/config`：请求
  `{"apiKey":"...","model":"deepseek-v4-flash"}`。先验证 DeepSeek 账户，
  成功后原子替换；验证失败不覆盖旧配置。
- `PATCH /api/admin/ai/config/status`：请求 `{"enabled":true|false}`。
- `DELETE /api/admin/ai/config`：清除数据库密钥并写入显式禁用状态，不回退环境变量。
- `GET /api/admin/ai/overview?period=today|7d|30d|all&refreshBalance=false`：
  返回 DeepSeek 实时余额、本应用 Token 统计和按 `AI_INPUT_COST_PER_MILLION_USD` /
  `AI_OUTPUT_COST_PER_MILLION_USD` 配置计算的估算费用。余额同步 60 秒内复用缓存。

后台只允许选择 `deepseek-v4-flash` 和 `deepseek-v4-pro`，Base URL 不通过接口开放。
每次替换、启停和清除都会写入 `operation_log`，审计内容不包含密钥。
