# 南邮智答后端

Phase 7 的 Spring Boot 后端。`Retriever` 从 Knowledge Service 检索学校资料，
`DeepSeekLLMService` 使用受约束 Prompt 生成答案，并返回文件名、页码、来源部门
及由检索分数计算的置信度。

## 环境

- Java 17+
- Maven 3.6.3+
- MySQL 8
- Redis 6+（Bucket4j 分布式限流）
- Knowledge Service（默认 `http://localhost:8090`）
- Crawler Service（默认 `http://localhost:8091`）

## 推荐启动方式

从仓库根目录启动开发依赖：

```bash
docker compose up -d
```

默认启动 MySQL `3307` 与 Redis `6380`，首次创建数据卷时自动执行
`sql/schema.sql`。这些端口可在根目录 `.env` 中覆盖，变量模板见
`.env.example`。

随后配置环境并启动后端：

```powershell
$env:DATABASE_URL = "jdbc:mysql://localhost:3307/njupt_ai_assistant?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:MYSQL_USERNAME = "njupt"
$env:MYSQL_PASSWORD = "njupt_dev_password"
$env:REDIS_PORT = "6380"
$env:AI_CONFIG_MASTER_KEY = "<Base64 编码的 32 字节随机密钥>"
$env:AI_PROVIDER = "deepseek"
$env:CRAWLER_SHARED_TOKEN = "njupt-crawler-local-token"
$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD_HASH = "{bcrypt}..."
$env:ADMIN_JWT_SECRET = "replace-with-at-least-32-random-bytes"
$env:RATE_LIMIT_REDIS_URI = "redis://localhost:6380"
mvn spring-boot:run
```

## 手动初始化数据库

在 MySQL 中执行：

```bash
mysql -u root -p < sql/schema.sql
```

## 配置

所有敏感配置均通过环境变量注入：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DATABASE_URL` | 未设置 | 首选 MySQL JDBC 地址 |
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/njupt_ai_assistant...` | 兼容的 MySQL JDBC 地址 |
| `MYSQL_USERNAME` | `root` | 数据库用户名 |
| `MYSQL_PASSWORD` | 空 | 数据库密码 |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `FILE_UPLOAD_PATH` | `./data/uploads` | 上传文件目录 |
| `MAX_UPLOAD_SIZE` | `50MB` | 单文件大小上限 |
| `AI_PROVIDER` | `deepseek` | 回答 Provider；离线演示可设为 `mock` |
| `DEEPSEEK_API_KEY` | 空 | DeepSeek API Key，禁止提交到仓库 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | OpenAI-compatible API 地址 |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | 环境变量兼容模式的模型名称；旧 `deepseek-chat` 仍可使用 |
| `AI_CONFIG_MASTER_KEY` | 空 | Base64 编码的 32 字节 AES 主密钥；缺失时后台禁止保存 Key |
| `DEEPSEEK_CONNECT_TIMEOUT` | `3s` | 模型连接超时 |
| `DEEPSEEK_READ_TIMEOUT` | `45s` | 模型响应超时 |
| `DEEPSEEK_MAX_TOKENS` | `1200` | 单次最大回答 Token |
| `ADMIN_USERNAME` | 空 | 管理员登录名，生产必填 |
| `ADMIN_PASSWORD_HASH` | 空 | 管理员密码摘要，支持 Spring `{bcrypt}` 格式 |
| `ADMIN_JWT_SECRET` | 空 | 管理员 JWT HS256 密钥，至少 32 字节 |
| `ADMIN_JWT_TTL` | `2h` | 管理员访问令牌有效期 |
| `ANONYMOUS_SESSION_RETENTION` | `90d` | 匿名聊天保留时间 |
| `RATE_LIMIT_REDIS_URI` | `redis://localhost:6379` | Bucket4j Redis 后端 |
| `CHAT_IP_RATE_LIMIT` | `60` | 单 IP 每分钟聊天接口上限 |
| `CHAT_SESSION_RATE_LIMIT` | `30` | 单匿名会话每分钟聊天接口上限 |
| `RAG_SERVICE_BASE_URL` | `http://localhost:8090` | Knowledge Service 地址 |
| `RAG_CONNECT_TIMEOUT` | `2s` | RAG 连接超时 |
| `RAG_READ_TIMEOUT` | `10s` | RAG 检索读取超时 |
| `RAG_INDEX_READ_TIMEOUT` | `60s` | 文档和网页知识索引读取超时 |
| `RAG_INDEXING_ENABLED` | `true` | 上传后自动写入知识库 |
| `RAG_TOP_K` | `5` | 送入回答链路的最大检索数 |
| `RAG_CANDIDATE_K` | `20` | 相关度过滤前的候选检索数 |
| `RAG_MIN_RELEVANCE_SCORE` | `0.35` | 最低相关性；生产可按 Embedding 调高 |
| `CHAT_MAX_HISTORY_MESSAGES` | `12` | 送入模型的最近对话消息数 |
| `CHAT_MAX_HISTORY_CHARACTERS` | `6000` | 历史对话字符上限，防止 Prompt 无界增长 |
| `CRAWLER_SERVICE_BASE_URL` | `http://localhost:8091` | Crawler Service 地址 |
| `CRAWLER_SHARED_TOKEN` | 空 | Crawler 调用内部入库接口的共享密钥 |
| `CRAWLER_ALLOWED_DOMAINS` | 三个南邮官方域名 | Spring 入库二次域名白名单 |

服务默认运行在 `http://localhost:8080`。

## 测试和打包

```bash
mvn test
mvn package
```

测试环境使用 H2 MySQL 兼容模式，不依赖本地 MySQL 或 Redis。

## 目录结构

```text
backend/
├─ sql/
│  └─ schema.sql
├─ src/main/java/com/njupt/aiassistant/
│  ├─ common/       # 统一响应、错误码
│  ├─ client/       # Knowledge Service 等外部服务客户端
│  ├─ config/       # CORS、AI、上传、JWT 配置
│  ├─ controller/   # REST Controller
│  ├─ dto/          # 请求对象
│  ├─ entity/       # JPA 实体
│  ├─ exception/    # 业务异常与全局处理
│  ├─ mapper/       # Spring Data JPA 持久化接口
│  ├─ service/      # 应用服务、文件存储、AI Provider
│  └─ vo/           # API 返回对象
├─ API.md
└─ pom.xml
```

## AI / RAG 扩展点

- `ChatAnswerProvider`：完整回答策略边界，当前有 `mock` 与 `deepseek`
- `LLMService`：模型调用边界，DeepSeek 只是其中一个实现
- `RagClient`：检索与文档索引边界
- `Retriever`：检索策略边界，当前为分类优先的 Chroma 向量检索，预留关键词/混合检索
- `RagPromptBuilder`：集中维护只依据知识库回答的系统规则
- `RagAnswerProperties`：Top K 与最低相关性可配置
- `CrawlerClient`：管理端触发与状态代理
- `CrawlerDocumentService`：安全校验、URL/hash 幂等入库边界

管理员上传 PDF/DOCX 后，事务提交监听器会将文件发送给 Knowledge Service，
成功后状态变为 `COMPLETED`，失败变为 `FAILED`。旧版 DOC 仍可保存，但由于
Knowledge Service 不解析 DOC，状态会变为 `FAILED`。

## 生产安全

- `/api/admin/**` 已由 Spring Security 管理员 JWT 和 `ADMIN` 角色保护。
- 学生端不登录；后端签发匿名 UUID，并按该 UUID 隔离会话、回答与反馈。
- 匿名聊天默认保留 90 天，定时任务清理消息、历史和会话。
- Redis + Bucket4j 对聊天 IP、匿名会话、后台 IP 和登录 IP 分别限流。
- 上传、采集、重建索引、评测和人工复核写入 `operation_log`。
- `/api/internal/crawler/documents` 需要共享 Token，并对 HTTPS 来源域名二次校验。
- 管理员 JWT 密钥和密码摘要必须从环境变量注入；学生请求不使用 JWT。
- 密码字段只允许保存 BCrypt/Argon2 等摘要，不保存明文。
- 上传同时校验扩展名、MIME 与 PDF/DOC/DOCX 文件签名；服务端文件名随机化并校验落盘路径。
- `DeepSeekProperties#toString` 会遮蔽 API Key，异常响应不包含上游堆栈。
- 管理员可在 `/admin/settings` 验证并保存 DeepSeek Key。Key 使用
  `AI_CONFIG_MASTER_KEY` 做 AES-256-GCM 加密，接口只返回掩码。
- 数据库中一旦存在 DeepSeek 配置行（包括停用或清除状态），系统就不会回退到旧的
  `DEEPSEEK_API_KEY`，避免意外重新启用。
- `ai_usage_record` 仅保存 Token、缓存、成功/失败和延迟计数，不保存 Prompt、回答或学生身份。

已有 MySQL 数据卷不会重新执行 `schema.sql`。升级时需执行：

```bash
mysql -u njupt -p njupt_ai_assistant < sql/migration/V8__ai_provider_config_and_usage.sql
```

上线前必须完成的配置与当前边界见 [PRODUCTION_SECURITY.md](PRODUCTION_SECURITY.md)。
