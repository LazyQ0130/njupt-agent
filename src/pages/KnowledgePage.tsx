import {
  AlertCircle,
  ArrowRight,
  BookOpen,
  Building2,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  FileText,
  RefreshCw,
  Search,
  Sparkles,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  getDocuments,
  type KnowledgeDocument,
} from "../api/document";
import { ApiError } from "../api/request";
import { Header } from "../components/Header";
import { SiteFooter } from "../components/SiteFooter";

const categoryLabels: Record<
  NonNullable<KnowledgeDocument["category"]>,
  string
> = {
  NEW_STUDENT: "新生指南",
  ACADEMIC: "教务规则",
  LIFE: "校园生活",
  MAJOR: "专业培养",
  CAREER: "就业发展",
  SCHOOL_OVERVIEW: "学校概况",
  ORGANIZATION: "组织机构",
  RESEARCH: "科研学术",
};

const categories = [
  "全部资料",
  "新生指南",
  "教务规则",
  "校园生活",
  "奖学金",
  "专业培养",
  "就业发展",
  "学校概况",
  "组织机构",
  "科研学术",
];

function inferCategory(document: KnowledgeDocument) {
  const text = `${document.title}${document.source}`;
  if (/奖学金|资助/.test(text)) return "奖学金";
  if (document.category) return categoryLabels[document.category];
  if (/学校简介|学校章程|南邮精神|校标|校训|校史|校歌|现任领导|校区地图|校园景观|校园景色|校园风光/.test(text)) {
    return "学校概况";
  }
  if (/党政群部门|教学机构|科研机构|直属单位|基层党组织|独立学院|组织机构/.test(text)) {
    return "组织机构";
  }
  if (/学科建设|科研平台|重点实验室|研究中心|学术刊物|科研成果|学术讲座|学术报告/.test(text)) {
    return "科研学术";
  }
  if (/新生|报到|军训/.test(text)) return "新生指南";
  if (/培养方案|学院|专业/.test(text)) return "专业培养";
  if (/图书馆|宿舍|食堂|校园/.test(text)) return "校园生活";
  return "教务规则";
}

function formatFileSize(bytes: number) {
  if (bytes < 1024 * 1024) {
    return `${Math.max(bytes / 1024, 0.1).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function statusLabel(status: KnowledgeDocument["status"]) {
  return {
    UPLOADING: "上传中",
    PROCESSING: "解析中",
    COMPLETED: "已解析",
    FAILED: "解析失败",
  }[status];
}

export function KnowledgePage() {
  const [query, setQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState("全部资料");
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setDocuments(await getDocuments());
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "知识库加载失败",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDocuments();
  }, [loadDocuments]);

  const filteredDocuments = useMemo(() => {
    return documents.filter((document) => {
      const categoryMatch =
        activeCategory === "全部资料" ||
        inferCategory(document) === activeCategory;
      const queryMatch = `${document.title}${document.source}${document.filename}`
        .toLowerCase()
        .includes(query.toLowerCase());
      return categoryMatch && queryMatch;
    });
  }, [activeCategory, documents, query]);

  return (
    <div className="app-shell knowledge-shell">
      <Header />
      <main>
        <section className="knowledge-hero">
          <div className="section-wrap">
            <div className="knowledge-hero-copy">
              <div className="eyebrow">
                <span><Sparkles size={14} /></span>
                南邮官方资料
              </div>
              <h1>校园知识库</h1>
              <p>查找可信、可追溯的学校政策、办事指南与培养方案。</p>
            </div>
            <label className="knowledge-search">
              <Search size={21} />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="搜索文件、来源部门或校园问题"
              />
              <kbd>⌘ K</kbd>
            </label>
            <div className="knowledge-metrics">
              <span><strong>{documents.length}</strong> 份实时资料</span>
              <span><strong>{new Set(documents.map((item) => item.source)).size}</strong> 个来源部门</span>
              <span><strong>{documents.filter((item) => item.status === "PROCESSING").length}</strong> 份正在解析</span>
            </div>
          </div>
        </section>

        <section className="section-wrap knowledge-content">
          <div className="filter-row">
            <div className="category-filters">
              {categories.map((category) => (
                <button
                  key={category}
                  type="button"
                  className={activeCategory === category ? "active" : ""}
                  onClick={() => setActiveCategory(category)}
                >
                  {category}
                </button>
              ))}
            </div>
          </div>

          <div className="result-header">
            <div>
              <h2>{activeCategory}</h2>
              <span>找到 {filteredDocuments.length} 份相关资料</span>
            </div>
            <span className="updated-badge">
              <CheckCircle2 size={14} /> 数据已同步
            </span>
          </div>

          {loading ? (
            <div className="empty-state loading-state" aria-live="polite">
              <span><RefreshCw className="spin" size={28} /></span>
              <h3>正在同步校园知识库</h3>
              <p>从南邮智答后端获取最新文档资料。</p>
            </div>
          ) : error ? (
            <div className="empty-state" role="alert">
              <span><AlertCircle size={28} /></span>
              <h3>知识库暂时无法加载</h3>
              <p>{error}</p>
              <button type="button" onClick={() => void loadDocuments()}>
                重新加载 <RefreshCw size={15} />
              </button>
            </div>
          ) : filteredDocuments.length > 0 ? (
            <div className="document-grid">
              {filteredDocuments.map((document) => (
                <article className="document-card" key={document.id}>
                  <div className="document-card-head">
                    <span className={`file-type ${document.type === "PDF" ? "pdf" : "word"}`}>
                      <FileText size={23} />
                      <small>{document.type}</small>
                    </span>
                    <span className="document-category">{inferCategory(document)}</span>
                  </div>
                  <h3>{document.title}</h3>
                  <p>
                    来源于{document.source}的校内资料，当前状态为
                    {statusLabel(document.status)}。
                  </p>
                  <div className="document-meta">
                    <span><Building2 size={14} /> {document.source}</span>
                    <span><CalendarDays size={14} /> {document.createdTime.slice(0, 10)}</span>
                  </div>
                  <div className="document-footer">
                    <small>
                      {formatFileSize(document.fileSize)} · {statusLabel(document.status)}
                    </small>
                    <button
                      type="button"
                      onClick={() =>
                        navigate("/chat", {
                          state: { query: `请概括《${document.title}》的重点内容` },
                        })
                      }
                    >
                      向 AI 提问 <ArrowRight size={15} />
                    </button>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <span><BookOpen size={28} /></span>
              <h3>没有找到相关资料</h3>
              <p>试试更换关键词或查看其他分类。</p>
              <button
                type="button"
                onClick={() => {
                  setQuery("");
                  setActiveCategory("全部资料");
                }}
              >
                查看全部资料 <ChevronRight size={16} />
              </button>
            </div>
          )}
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}
