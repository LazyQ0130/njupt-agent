# 南邮智答（NJUPT AI Assistant）

面向南京邮电大学学生的 AI 校园智能问答产品。当前仓库已完成：

- **Phase 1**：React 前端页面与响应式 UI
- **Phase 2**：Spring Boot 后端基础架构、MySQL 表结构、文件上传与模拟 AI 接口
- **Phase 3**：前后端真实 API 联调、MySQL 持久化、Docker 开发环境
- **Phase 4.1**：独立 Knowledge Service、文档切块、Embedding 与 Chroma 检索
- **Phase 4.2**：DeepSeek Chat、完整 RAG 问答、来源与检索置信度
- **Phase 5**：南邮官网自动采集、正文清洗、分类、增量索引与采集管理台
- **Phase 6**：多轮会话、回答反馈、质量统计、50 题 RAG 评测与分类优先检索
- **Phase 7**：匿名会话隔离、管理员 JWT、Redis 限流、操作审计与 100 题上线评测
- **AI 设置中心**：管理员在线配置加密 DeepSeek Key、同步账户余额并查看 Token 用量

> 管理员上传 PDF/DOCX 后会自动进入 Knowledge Service 索引；`/api/chat/ask`
> 会检索知识片段并调用 DeepSeek 生成有来源的答案。测试用 `mock` Provider 仍保留。

## 已完成页面

| 路由 | 页面 | 主要能力 |
| --- | --- | --- |
| `/` | 首页 | AI 问题输入、热门问题分类、知识库数据概览 |
| `/chat` | AI 问答 | 真实问答 API、历史记录、来源文件、可信度、失败重试 |
| `/knowledge` | 校园知识库 | 实时文档列表、搜索、分类筛选、从资料发起 AI 提问 |
| `/admin` | 管理后台 | 真实文件上传、上传状态、文件搜索、实时统计 |
| `/admin/crawler` | 官网采集 | 自定义南邮官方网址、近 N 年过滤、任务统计、增量采集与重新索引 |
| `/admin/login` | 管理员登录 | 独立的后台 JWT 安全入口 |
| `/admin/quality` | AI 质量 | 好评率、高频问题、100 题 RAG 质量报告 |

所有页面均包含桌面端、平板端和移动端响应式布局。

## 技术栈

- React 19 + TypeScript
- Vite
- Tailwind CSS 4
- React Router
- Lucide Icons
- Spring Boot 3.5 + MySQL
- FastAPI + LangChain + Chroma
- FastAPI + BeautifulSoup4 + APScheduler

## 本地启动

环境要求：Node.js 20 或更高版本。

1. 启动 MySQL、Redis 与 Knowledge Service：

```bash
docker compose up -d
```

默认使用 MySQL `localhost:3307` 和 Redis `localhost:6380`，避免与机器上常见的
3306/6379 服务冲突；Knowledge Service 使用 `localhost:8090`。容器首次启动会
执行 `backend/sql/schema.sql`，Chroma 与 Crawler 状态分别保存在独立数据卷。
Compose 还会启动 Crawler Service `localhost:8091`。

2. 启动后端（PowerShell）：

```powershell
$env:DATABASE_URL="jdbc:mysql://localhost:3307/njupt_ai_assistant?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:MYSQL_USERNAME="njupt"
$env:MYSQL_PASSWORD="njupt_dev_password"
$env:REDIS_PORT="6380"
$env:AI_CONFIG_MASTER_KEY="<Base64 编码的 32 字节随机密钥>"
$env:AI_PROVIDER="deepseek"
$env:CRAWLER_SHARED_TOKEN="njupt-crawler-local-token"
$env:ADMIN_USERNAME="admin"
$env:ADMIN_PASSWORD_HASH="{bcrypt}..."
$env:ADMIN_JWT_SECRET="replace-with-at-least-32-random-bytes"
$env:RATE_LIMIT_REDIS_URI="redis://localhost:6380"
Set-Location backend
mvn spring-boot:run
```

3. 新开终端启动前端：

```bash
npm install
npm run dev
```

前端默认请求 `http://localhost:8080`。生产环境通过
`VITE_API_BASE_URL` 修改 API 地址；完整环境变量示例见 `.env.example`。

后端启动、环境变量与数据库初始化方式见 [backend/README.md](backend/README.md)，接口定义见 [backend/API.md](backend/API.md)。
Phase 7 的自动评测基线与正式上线门槛见
[上线质量报告](backend/PHASE7_QUALITY_REPORT.md)。

## 质量检查

```bash
# TypeScript 类型检查
npm run check

# 生产构建
npm run build

# 静态站点产物检查
npm run test:sites
```

## 目录结构

```text
.
├─ public/
│  └─ assets/                  # 校园线稿等视觉资产
├─ src/
│  ├─ components/              # 品牌、导航、问题输入框
│  ├─ api/                     # 请求封装、聊天与文档 API
│  ├─ pages/                   # Home / Chat / Knowledge / Admin
│  ├─ App.tsx                  # 路由定义
│  ├─ data.ts                  # 首页静态展示数据
│  ├─ types.ts                 # 共享类型
│  └─ styles.css               # Tailwind 入口与产品视觉样式
├─ backend/                     # Spring Boot 3.5 后端
│  ├─ sql/schema.sql            # MySQL 初始化脚本
│  ├─ src/main/                 # 后端业务代码与配置
│  ├─ src/test/                 # 后端自动化测试
│  └─ API.md                    # REST API 文档
├─ knowledge-service/           # FastAPI + LangChain + Chroma
├─ crawler-service/             # 官方站点采集、分类与增量状态
├─ output/pdf/                  # Phase 4.2 可上传联调 PDF（明确标注非正式）
├─ docker-compose.yml           # MySQL / Redis / Knowledge Service
├─ .env.example                 # 前后端环境变量模板
├─ design-qa.md                # 视觉与交互 QA 记录
└─ README.md
```

## Phase 4.2 RAG 流程

当前实现保持模型与检索边界独立：

```text
POST /api/chat/ask
→ RagClient 检索 Top 5
→ 相关性阈值过滤
→ RagPromptBuilder 拼接受约束上下文
→ LLMService / DeepSeekLLMService
→ 答案 + 文件名 + 页码 + 来源部门 + 置信度
```

生产环境可继续增加混合检索、Reranker、索引删除/重建和流式输出。

## Phase 6 对话与质量闭环

```text
会话历史（最近 12 条 / 6000 字符）
→ 分类器识别 NEW_STUDENT / ACADEMIC / LIFE / MAJOR / CAREER
→ Retriever 分类优先检索（无结果自动回退全库）
→ DeepSeek 受约束生成
→ 来源、置信度与 chatId
→ HELPFUL / INCORRECT 反馈
→ 后台质量统计、100 题自动评测与人工准确性复核
```

已有数据库执行
`backend/sql/migration/V6__conversation_feedback_evaluation.sql`。生产发布前按
[生产安全清单](backend/PRODUCTION_SECURITY.md) 完成认证、密钥和上传隔离配置。

## Phase 7 匿名访问与后台安全

```text
首次访问 → POST /api/session/anonymous → 浏览器 localStorage 保存随机 UUID
学生聊天 → X-Anonymous-Session-Id → 会话归属校验 → 90 天自动过期
管理员登录 → ADMIN JWT（sessionStorage）→ /api/admin/** 角色校验
所有聊天/后台请求 → Redis + Bucket4j 双维度限流
高风险后台操作 → operation_log 审计
```

学生端没有注册、登录和学生 JWT。管理员认证只影响 `/admin/**` 页面与
`/api/admin/**` 接口，不会阻断首页、知识库浏览或问答。

## Phase 5 自动知识管线

```text
南邮官网 / 本科生院 / 计算机学院
→ robots.txt + 域名白名单 + 页面上限
→ 正文清洗与学生相关规则分类
→ ETag / Last-Modified / SHA-256 增量判断
→ Spring Document 持久化
→ Knowledge Service 网页切块
→ Chroma 覆盖索引
→ RAG 回答展示官网 URL、来源和分类
```

首次部署执行 `backend/sql/schema.sql`。已有 Phase 4 数据库执行
`backend/sql/migration/V5__crawler_documents.sql`。
