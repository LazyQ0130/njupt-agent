# 南邮智答 Crawler Service

受控采集南京邮电大学官方公开网页，生成标准化 `Document`，并通过 Spring Boot
内部接口送入现有 Knowledge Service / Chroma 管线。服务不直接访问数据库或
Chroma，因此不会破坏既有分层。

## 数据流

```text
njupt.edu.cn / jwc.njupt.edu.cn / cs.njupt.edu.cn
→ robots.txt 与域名白名单
→ 有界广度爬取
→ BeautifulSoup 正文清洗
→ 关键词分类
→ SHA-256 增量判断
→ POST /api/internal/crawler/documents
→ Spring 事务入库
→ Knowledge Service / Chroma
```

## 目录

```text
crawler-service/
├─ app/
│  ├─ api/routes.py            # 状态、执行与重新索引接口
│  ├─ core/
│  │  ├─ config.py             # 白名单、限额、定时与连接配置
│  │  └─ errors.py
│  ├─ models/schemas.py        # Document 与任务状态模型
│  └─ services/
│     ├─ backend_client.py      # Spring 内部接口客户端
│     ├─ classifier.py         # 学生信息规则分类器
│     ├─ crawler.py            # 有界、增量采集编排
│     ├─ manager.py            # 单任务互斥与后台运行
│     ├─ page_parser.py        # 正文、日期和链接提取
│     ├─ robots.py             # robots.txt 缓存与校验
│     └─ state_store.py        # SQLite URL/hash/任务状态
├─ tests/
├─ Dockerfile
├─ pyproject.toml
└─ .env.example
```

## 采集规则

常规自动采集预设以下 HTTPS 域名：

- `www.njupt.edu.cn`
- `jwc.njupt.edu.cn`
- `cs.njupt.edu.cn`

管理员定向采集还可以提交任意 `*.njupt.edu.cn` 官方 HTTPS 子域名。定向任务
默认只跟随提交网址的同一精确主机，并优先限定在该列表栏目及详情页。管理员可
显式启用 `DIRECT_NJUPT_SUBDOMAINS`，从起始目录页正文发现直接链接的学院站；
进入学院站后重新从深度 0 开始，但仍只跟随该精确主机，不会继续跨站扩散。

分类按有序关键词执行：

| 分类 | 主要关键词 |
| --- | --- |
| `NEW_STUDENT` | 新生、入学、报到、迎新、军训、校园卡、新生手册 |
| `ACADEMIC` | 选课、考试、补考、转专业、培养方案、学分、校历、课程、教务 |
| `LIFE` | 图书馆、食堂、宿舍、快递、校园服务、后勤、医疗、保卫 |
| `MAJOR` | 专业介绍、学院介绍、培养方向、专业方向、专业设置 |
| `CAREER` | 就业、招聘、考研、升学、推免、职业发展 |

无学生相关关键词的详情页不入库，但栏目页仍可用于发现链接。解析时移除
`nav/header/footer/aside/script/style`、常见导航容器和无意义链接，保留标题、
正文、发布时间、来源与原始 URL。

## 安全与增量策略

- 单站页面数默认和硬上限均为 `50`
- 目录扩展任务最多 `30` 个站点（包含起始站点），总硬上限 `1500` 页
- 最大链接深度默认 `2`
- 同域请求间隔默认 `1.5s`
- 网络错误和 5xx 最多重试 `2` 次
- 尊重 `robots.txt`；401/403 或 robots 获取网络失败时保守拒绝
- `robots.txt` 明确不存在（404）时按无显式规则处理，其他限制仍生效
- 保存 ETag、Last-Modified 和清洗后正文 SHA-256
- hash 未变化时不重新 Embedding；“重新索引”会强制覆盖 URL 对应向量
- Crawler → Spring 使用 `X-Crawler-Token`，两端必须配置相同密钥
- 定向采集按正文发布日期过滤；正文未提供日期时可从南邮标准
  `/YYYY/MMDD/.../page.htm` URL 回退识别，仍无可靠日期则跳过

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/health` | 健康、调度开关、域名白名单 |
| `GET` | `/crawler/status` | 汇总数量与最近任务 |
| `POST` | `/crawler/run` | 启动增量采集；已有任务时拒绝重复启动 |
| `POST` | `/crawler/custom` | 定向采集官方栏目；支持 `seed_url`、`date_scope`、`crawl_scope`、`years`、`max_pages`、`force_reindex` |
| `POST` | `/crawler/reindex` | 启动强制重新索引 |

任务接口立即返回 `run_id`，采集在服务内后台执行。管理端每 3 秒轮询状态。

## 本地启动

```powershell
Set-Location crawler-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[test]"
Copy-Item .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8091
```

先在 Crawler 与 Spring 环境中设置同一个 `CRAWLER_SHARED_TOKEN`。也可以在仓库
根目录使用 `docker compose up -d crawler-service`。

## 测试

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

测试覆盖分类、HTML 清洗、正文链接隔离、目录直链扩展、逐站页面与站点上限、
跨主机重定向拒绝、robots 拒绝、ETag/hash 增量跳过以及 FastAPI 状态接口。
