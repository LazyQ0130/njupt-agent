# Prototype Instructions

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

Build app UI in `src/`. Keep `.openai/hosting.json`, `worker/index.js`, `scripts/prepare-sites-build.mjs`, and `tests/sites-worker.test.mjs` intact so the same local prototype can be handed to Sites. Before a Sites handoff, run `npm run build` and `npm run test:sites`; the build must leave `dist/client/index.html`, `dist/server/index.js`, and `dist/.openai/hosting.json`.

Phase 6 product decisions:
- Preserve the existing blue-white visual language and page structure while adding AI quality features.
- Treat 390px as a required mobile QA width; no page may introduce horizontal scrolling.

Phase 7 product decisions:
- 学生端始终免登录。不要展示“统一身份认证”“登录后同步问答记录”或任何学生注册/登录入口。
- 顶部学生侧主操作使用“开始提问”，并直接导航到 `/chat`；管理员认证只存在于 `/admin/login`。

Admin crawler product decisions:
- 管理员可在 `/admin/crawler` 粘贴任意南邮官方 HTTPS 子域名栏目网址进行定向采集。
- 定向采集默认仅收录近 2 年内容；无可靠发布日期的新闻、通知、活动和普通目录页跳过。
- 管理员可主动选择“无发布日期限制”；该范围会收录旧页面和无可靠发布日期页面，但默认仍保持近 2 年。
- 每次定向任务只跟随提交网址的同一精确主机，并保持页面上可见的数量上限与安全说明。
- 管理员可显式启用“目录直链扩展”；此模式仅从起始页正文接纳直接链接的南邮官方 HTTPS 子域名，最多 30 个站点（包含起始站点）、每站 50 页，进入学院站后只跟随该精确主机且不继续跨站扩散。
- 官网静态知识使用 `SCHOOL_OVERVIEW`、`ORGANIZATION`、`RESEARCH` 三类；仅这三类的静态 `list.htm` 页面可按 50 字最低正文长度入库，其他类别仍保持 120 字。
- 高置信度的常青静态知识（学校概况、机构职责、学院专业、科研平台、图书馆及校园服务介绍）可在默认近 2 年范围内无发布日期入库；其他无日期页面仍仅在管理员主动选择“无发布日期限制”时入库。
- 常青页沿用现有八类知识分类，不新增校园新闻或活动类别；`LIFE`、`MAJOR` 等非学校静态三类仍保持 120 字最低正文长度。
- “甲专业转乙专业”问答以乙专业/学院接收细则为首要依据，再补充校级规则；仅在甲专业存在明确转出限制时引用甲方细则。

Production deployment decisions:
- 生产主站使用裸域名 `https://njupt-ai.top`，不以 `www` 作为主站。
- 当前目标环境为阿里云香港 Ubuntu 22.04 ECS；生产入口只公开 HTTP/HTTPS，数据库与应用服务端口保持内网访问。
