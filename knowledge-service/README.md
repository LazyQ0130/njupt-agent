# 南邮智答 Knowledge Service

独立的校园知识库解析与检索服务。当前阶段只负责：

```text
PDF / DOCX / 官网正文
→ 文本提取与清洗
→ 500–1000 token 切块
→ Embedding
→ Chroma 持久化
→ 相似度检索
```

不会调用聊天模型，也不会生成最终答案。

## 技术栈

- Python 3.11–3.14
- FastAPI
- LangChain Core / Text Splitters
- Chroma
- pypdf / python-docx

## 目录

```text
knowledge-service/
├─ app/
│  ├─ api/routes.py              # HTTP 接口
│  ├─ core/config.py             # 环境配置
│  ├─ core/errors.py             # 领域异常
│  ├─ models/schemas.py          # 请求响应模型
│  └─ services/
│     ├─ document_parser.py       # PDF / DOCX 提取与清洗
│     ├─ text_chunker.py          # token 切块
│     ├─ embedding.py             # Embedding 抽象与适配器
│     ├─ vector_store.py          # Chroma 持久化与检索
│     └─ rag_service.py           # 索引、检索编排
├─ tests/
├─ Dockerfile
├─ pyproject.toml
└─ .env.example
```

## 本地启动

PowerShell：

```powershell
cd knowledge-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[test]"
Copy-Item .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload
```

默认使用 `hash` Embedding，仅用于离线开发和自动化测试。接入任意
OpenAI-compatible Embedding 服务时：

```dotenv
EMBEDDING__PROVIDER=openai-compatible
EMBEDDING__API_KEY=your-key
EMBEDDING__BASE_URL=https://embedding-provider.example.com/v1
EMBEDDING__MODEL=your-embedding-model
```

## API

### 健康检查

`GET /health`

### 索引文档

`POST /documents/index`

Content-Type：`multipart/form-data`

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | PDF 或 DOCX，默认最大 50 MB |
| `source` | 是 | 学校部门或学院 |
| `upload_time` | 否 | 原始上传时间；未提供时使用当前 UTC 时间 |

示例：

```bash
curl -X POST http://localhost:8090/documents/index \
  -F "file=@./本科生转专业管理办法.pdf" \
  -F "source=教务处" \
  -F "upload_time=2026-07-25T12:00:00+08:00"
```

每个 chunk 在 Chroma 中保留：

```json
{
  "filename": "本科生转专业管理办法.pdf",
  "source": "教务处",
  "upload_time": "2026-07-25T12:00:00+08:00",
  "page": 1,
  "document_id": "...",
  "chunk_index": 0,
  "start_index": 0
}
```

DOCX 没有稳定的渲染分页信息，因此基础解析阶段统一记录为第 1 页。

### 索引官网正文

`POST /documents/index-text`

由 Spring Boot 内部调用，接收已清洗的网页正文：

```json
{
  "title": "关于做好本科生转专业工作的通知",
  "content": "……",
  "source": "南京邮电大学本科生院",
  "source_url": "https://jwc.njupt.edu.cn/example/page.htm",
  "category": "ACADEMIC",
  "crawl_time": "2026-07-26T12:00:00",
  "last_updated": "2026-07-25T09:00:00",
  "content_hash": "64 位 SHA-256"
}
```

同一 `source_url` 再次索引前会删除旧 chunks，避免网页更新产生重复内容。
网页向量 metadata 额外保留 `source_url`、`category`、`source_type`、
`crawl_time`、`last_updated` 与 `content_hash`。

### 检索

`POST /rag/search`

请求：

```json
{
  "question": "南邮转专业条件",
  "top_k": 5
}
```

`top_k` 可省略，默认返回 5 条。

响应：

```json
[
  {
    "content": "学生申请转专业应关注教务处当年度通知……",
    "filename": "本科生转专业管理办法.pdf",
    "page": 3,
    "source": "教务处",
    "source_url": "https://jwc.njupt.edu.cn/example/page.htm",
    "category": "ACADEMIC",
    "score": 0.93
  }
]
```

`score` 为 0–1 的相关度，越大表示越相关。

## 测试

```powershell
.\.venv\Scripts\python.exe -m pytest
```
