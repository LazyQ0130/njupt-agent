import {
  Activity,
  AlertCircle,
  BarChart3,
  CheckCircle2,
  Clock3,
  CloudUpload,
  Database,
  File,
  FileText,
  FolderOpen,
  LayoutDashboard,
  MoreHorizontal,
  RefreshCw,
  Search,
  Settings,
  Sparkles,
  Trash2,
  X,
} from "lucide-react";
import {
  type ChangeEvent,
  type DragEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import { useNavigate } from "react-router-dom";
import {
  getDocuments,
  deleteDocument,
  reindexUploadedDocuments,
  upsertCuratedDocument,
  uploadDocument,
  type DocumentCategory,
  type KnowledgeDocument,
} from "../api/document";
import { ApiError } from "../api/request";
import { Brand } from "../components/Brand";

const adminNav = [
  ["总览", LayoutDashboard],
  ["知识文件", FolderOpen],
  ["解析任务", RefreshCw],
  ["知识统计", BarChart3],
  ["设置", Settings],
] as const;

const statusClass: Record<KnowledgeDocument["status"], string> = {
  UPLOADING: "pending",
  PROCESSING: "processing",
  COMPLETED: "success",
  FAILED: "failed",
};

const statusText: Record<KnowledgeDocument["status"], string> = {
  UPLOADING: "上传中",
  PROCESSING: "解析中",
  COMPLETED: "解析完成",
  FAILED: "解析失败",
};

const categoryOptions: Array<[DocumentCategory, string]> = [
  ["NEW_STUDENT", "新生指南"],
  ["ACADEMIC", "教务规则"],
  ["LIFE", "校园生活"],
  ["MAJOR", "专业培养"],
  ["CAREER", "就业发展"],
  ["SCHOOL_OVERVIEW", "学校概况"],
  ["ORGANIZATION", "组织机构"],
  ["RESEARCH", "科研学术"],
];

const categoryLabels = Object.fromEntries(categoryOptions) as Record<
  DocumentCategory,
  string
>;

export function AdminPage() {
  const navigate = useNavigate();
  const [activeNav, setActiveNav] = useState("知识文件");
  const [items, setItems] = useState<KnowledgeDocument[]>([]);
  const [query, setQuery] = useState("");
  const [isDragging, setIsDragging] = useState(false);
  const [toast, setToast] = useState("");
  const [toastTone, setToastTone] = useState<"success" | "error">("success");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [isUploading, setIsUploading] = useState(false);
  const [isReindexing, setIsReindexing] = useState(false);
  const [uploadingCount, setUploadingCount] = useState(0);
  const [isCurating, setIsCurating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [curated, setCurated] = useState({
    title: "",
    content: "",
    source: "",
    sourceUrl: "",
    category: "ACADEMIC" as DocumentCategory,
    publishedTime: "",
  });
  const inputRef = useRef<HTMLInputElement>(null);

  const loadDocuments = useCallback(async (quiet = false) => {
    if (!quiet) setIsLoading(true);
    setLoadError("");
    try {
      setItems(await getDocuments());
    } catch (error) {
      setLoadError(error instanceof ApiError ? error.message : "文件列表加载失败");
    } finally {
      if (!quiet) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDocuments();
  }, [loadDocuments]);

  const addFiles = async (fileList: FileList | null) => {
    if (!fileList?.length) return;
    const accepted = Array.from(fileList).filter((file) =>
      /\.(pdf|doc|docx)$/i.test(file.name),
    );
    if (!accepted.length) {
      setToastTone("error");
      setToast("请选择 PDF 或 Word 文件");
      return;
    }

    setIsUploading(true);
    setUploadingCount(accepted.length);
    const results = await Promise.allSettled(
      accepted.map((file) =>
        uploadDocument(file, {
          title: file.name.replace(/\.(pdf|docx?)$/i, ""),
          source: "待补充",
        }),
      ),
    );
    const uploaded = results
      .filter((result): result is PromiseFulfilledResult<KnowledgeDocument> =>
        result.status === "fulfilled",
      )
      .map((result) => result.value);

    if (uploaded.length) {
      setItems((current) => [
        ...uploaded,
        ...current.filter(
          (item) => !uploaded.some((document) => document.id === item.id),
        ),
      ]);
    }

    const failedCount = results.length - uploaded.length;
    setToastTone(failedCount ? "error" : "success");
    setToast(
      failedCount
        ? `成功上传 ${uploaded.length} 个，失败 ${failedCount} 个`
        : `已上传 ${uploaded.length} 个文件，正在解析`,
    );
    setIsUploading(false);
    setUploadingCount(0);
    if (inputRef.current) inputRef.current.value = "";
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragging(false);
    void addFiles(event.dataTransfer.files);
  };

  const normalizedQuery = query.trim().toLowerCase();
  const filteredItems = items.filter((item) => {
    const category = item.category
      ? `${item.category}${categoryLabels[item.category]}`
      : "未分类";
    return `${item.filename}${item.source}${category}`
      .toLowerCase()
      .includes(normalizedQuery);
  });
  const completedCount = items.filter((item) => item.status === "COMPLETED").length;
  const processingCount = items.filter((item) =>
    item.status === "PROCESSING" || item.status === "UPLOADING",
  ).length;
  const failedCount = items.filter((item) => item.status === "FAILED").length;

  useEffect(() => {
    if (!processingCount) return;
    const timer = window.setInterval(() => void loadDocuments(true), 3000);
    return () => window.clearInterval(timer);
  }, [loadDocuments, processingCount]);

  const triggerUploadedReindex = async () => {
    setIsReindexing(true);
    try {
      const result = await reindexUploadedDocuments();
      setToastTone("success");
      setToast(
        `已安排 ${result.scheduledCount} 份上传文件重建索引，跳过 ${result.skippedCount} 份`,
      );
      await loadDocuments(true);
    } catch (error) {
      setToastTone("error");
      setToast(
        error instanceof ApiError ? error.message : "上传文件索引重建启动失败",
      );
    } finally {
      setIsReindexing(false);
    }
  };

  const submitCurated = async () => {
    setIsCurating(true);
    try {
      const saved = await upsertCuratedDocument({
        ...curated,
        publishedTime: curated.publishedTime
          ? new Date(curated.publishedTime).toISOString()
          : undefined,
        verifiedTime: new Date().toISOString(),
      });
      setItems((current) => [
        saved,
        ...current.filter((item) => item.id !== saved.id),
      ]);
      setCurated({
        title: "",
        content: "",
        source: "",
        sourceUrl: "",
        category: "ACADEMIC",
        publishedTime: "",
      });
      setToastTone("success");
      setToast("人工知识已保存，正在建立索引");
    } catch (error) {
      setToastTone("error");
      setToast(error instanceof ApiError ? error.message : "人工知识保存失败");
    } finally {
      setIsCurating(false);
    }
  };

  const removeDocument = async (document: KnowledgeDocument) => {
    const confirmed = window.confirm(
      `确认删除“${document.title}”？\n删除后将同步移除检索向量，官网来源默认禁止重新入库。`,
    );
    if (!confirmed) return;
    setDeletingId(document.id);
    try {
      await deleteDocument(document.id, true);
      setItems((current) => current.filter((item) => item.id !== document.id));
      setToastTone("success");
      setToast(`已删除：${document.title}`);
    } catch (error) {
      setToastTone("error");
      setToast(error instanceof ApiError ? error.message : "文档删除失败");
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="admin-app">
      <aside className="admin-sidebar">
        <div className="admin-brand"><Brand /></div>
        <nav aria-label="管理后台导航">
          <span>工作台</span>
          {adminNav.map(([label, Icon]) => (
            <button
              key={label}
              type="button"
              className={activeNav === label ? "active" : ""}
              onClick={() => {
                if (label === "设置") {
                  navigate("/admin/settings");
                  return;
                }
                if (label === "解析任务") {
                  navigate("/admin/crawler");
                  return;
                }
                if (label === "知识统计") {
                  navigate("/admin/quality");
                  return;
                }
                setActiveNav(label);
              }}
            >
              <Icon size={18} />
              {label}
            </button>
          ))}
        </nav>
        <div className="admin-account">
          <span>管</span>
          <div><strong>知识库管理员</strong><small>admin@njupt.edu.cn</small></div>
          <MoreHorizontal size={17} />
        </div>
      </aside>

      <main className="admin-main">
        <header className="admin-topbar">
          <div>
            <h1>校园知识库管理</h1>
            <p>上传、解析并维护南邮官方资料</p>
          </div>
          <div className={`system-status${loadError ? " offline" : ""}`}>
            <i /> {loadError ? "数据服务连接异常" : "系统运行正常"}
          </div>
        </header>

        <div className="admin-content">
          <section className="admin-stats">
            {[
              ["文档总数", items.length.toLocaleString(), "知识库资料", Database],
              ["解析完成", completedCount.toLocaleString(), `${items.length ? Math.round((completedCount / items.length) * 100) : 0}%`, CheckCircle2],
              ["解析任务", processingCount.toLocaleString(), "正在处理", Activity],
              ["待处理", failedCount.toLocaleString(), "需要关注", Clock3],
            ].map(([label, value, sub, Icon]) => (
              <article key={label as string}>
                <span><Icon size={20} /></span>
                <div><small>{label as string}</small><strong>{value as string}</strong><p>{sub as string}</p></div>
              </article>
            ))}
          </section>

          <section className="upload-section">
            <div className="admin-section-heading">
              <div><h2>上传知识库文件</h2><p>文件将自动进入解析与内容切分流程</p></div>
              <span>单文件最大 50 MB</span>
            </div>
            <div
              className={`dropzone${isDragging ? " dragging" : ""}${isUploading ? " uploading" : ""}`}
              onDragEnter={(event) => { event.preventDefault(); setIsDragging(true); }}
              onDragOver={(event) => event.preventDefault()}
              onDragLeave={() => setIsDragging(false)}
              onDrop={handleDrop}
              onClick={() => !isUploading && inputRef.current?.click()}
              role="button"
              tabIndex={0}
              onKeyDown={(event) => {
                if (!isUploading && (event.key === "Enter" || event.key === " ")) {
                  inputRef.current?.click();
                }
              }}
            >
              <input
                ref={inputRef}
                type="file"
                accept=".pdf,.doc,.docx"
                multiple
                disabled={isUploading}
                onChange={(event: ChangeEvent<HTMLInputElement>) => {
                  void addFiles(event.target.files);
                }}
              />
              <span className="upload-icon">
                {isUploading ? <RefreshCw className="spin" size={30} /> : <CloudUpload size={30} />}
              </span>
              <h3>{isUploading ? `正在上传 ${uploadingCount} 个文件` : "拖拽上传 PDF / Word"}</h3>
              <p>{isUploading ? "请稍候，文件信息正在写入知识库" : "或点击选择本地文件，支持多文件上传"}</p>
              <div><span><FileText size={15} /> PDF</span><span><File size={15} /> DOCX</span></div>
            </div>
          </section>

          <section className="curated-section">
            <div className="admin-section-heading">
              <div>
                <h2><Sparkles size={18} /> 人工知识条目</h2>
                <p>写入可直接回答学生问题的事实、条件和办理流程</p>
              </div>
              <span>官方来源必填</span>
            </div>
            <form
              className="curated-form"
              onSubmit={(event) => {
                event.preventDefault();
                void submitCurated();
              }}
            >
              <label>
                <span>知识标题</span>
                <input
                  required
                  maxLength={255}
                  value={curated.title}
                  onChange={(event) => setCurated((value) => ({
                    ...value,
                    title: event.target.value,
                  }))}
                  placeholder="例如：本科生缓考申请条件与流程"
                />
              </label>
              <label>
                <span>负责单位</span>
                <input
                  required
                  maxLength={128}
                  value={curated.source}
                  onChange={(event) => setCurated((value) => ({
                    ...value,
                    source: event.target.value,
                  }))}
                  placeholder="例如：南京邮电大学本科生院"
                />
              </label>
              <label>
                <span>知识分类</span>
                <select
                  value={curated.category}
                  onChange={(event) => setCurated((value) => ({
                    ...value,
                    category: event.target.value as DocumentCategory,
                  }))}
                >
                  {categoryOptions.map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              </label>
              <label>
                <span>原文发布日期（可选）</span>
                <input
                  type="datetime-local"
                  value={curated.publishedTime}
                  onChange={(event) => setCurated((value) => ({
                    ...value,
                    publishedTime: event.target.value,
                  }))}
                />
              </label>
              <label className="curated-wide">
                <span>官方来源网址</span>
                <input
                  required
                  type="url"
                  value={curated.sourceUrl}
                  onChange={(event) => setCurated((value) => ({
                    ...value,
                    sourceUrl: event.target.value,
                  }))}
                  placeholder="https://…njupt.edu.cn/…"
                />
              </label>
              <label className="curated-wide">
                <span>可回答知识正文</span>
                <textarea
                  required
                  minLength={120}
                  maxLength={20000}
                  rows={9}
                  value={curated.content}
                  onChange={(event) => setCurated((value) => ({
                    ...value,
                    content: event.target.value,
                  }))}
                  placeholder={"结论：…\n适用对象：…\n条件：…\n办理流程：…\n材料：…\n时限：…\n负责部门与公开联系方式：…\n有效期：…"}
                />
              </label>
              <div className="curated-actions">
                <small>{curated.content.length}/20000 字</small>
                <button type="submit" disabled={isCurating}>
                  {isCurating ? <RefreshCw className="spin" size={16} /> : <Sparkles size={16} />}
                  {isCurating ? "正在保存" : "保存并建立索引"}
                </button>
              </div>
            </form>
          </section>

          <section className="file-table-section">
            <div className="table-toolbar">
              <div><h2>文件列表</h2><span>{items.length} 份资料</span></div>
              <button
                type="button"
                className="reindex-upload-button"
                disabled={isReindexing}
                onClick={() => void triggerUploadedReindex()}
              >
                <RefreshCw className={isReindexing ? "spin" : ""} size={16} />
                {isReindexing ? "正在安排重建" : "重建上传文件索引"}
              </button>
              <label>
                <Search size={17} />
                <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索文件" />
              </label>
            </div>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>文件名称</th>
                    <th>来源部门</th>
                    <th>知识分类</th>
                    <th>上传时间</th>
                    <th>文件大小</th>
                    <th>解析状态</th>
                    <th aria-label="操作" />
                  </tr>
                </thead>
                <tbody>
                  {isLoading && (
                    <tr><td className="table-message" colSpan={7}>正在加载知识库文件…</td></tr>
                  )}
                  {!isLoading && loadError && (
                    <tr>
                      <td className="table-message" colSpan={7}>
                        <AlertCircle size={16} /> {loadError}
                        <button type="button" onClick={() => void loadDocuments()}>重新加载</button>
                      </td>
                    </tr>
                  )}
                  {!isLoading && !loadError && filteredItems.length === 0 && (
                    <tr><td className="table-message" colSpan={7}>暂无符合条件的知识库文件</td></tr>
                  )}
                  {!isLoading && !loadError && filteredItems.map((document) => (
                    <tr key={document.id}>
                      <td>
                        <span className={`table-file-icon ${document.type.toLowerCase()}`}><FileText size={18} /></span>
                        <div><strong>{document.filename}</strong><small>{document.type.toUpperCase()}</small></div>
                      </td>
                      <td>{document.source}</td>
                      <td>
                        <span className={`admin-category ${document.category ? "" : "uncategorized"}`}>
                          {document.category ? categoryLabels[document.category] : "未分类"}
                        </span>
                      </td>
                      <td>{document.createdTime.slice(0, 10)}</td>
                      <td>{formatFileSize(document.fileSize)}</td>
                      <td><span className={`status ${statusClass[document.status]}`}><i /> {statusText[document.status]}</span></td>
                      <td>
                        <div className="row-actions">
                          <button type="button" aria-label={`查看 ${document.filename}`}><MoreHorizontal size={18} /></button>
                          <button
                            type="button"
                            className="danger"
                            disabled={
                              deletingId === document.id
                              || document.status === "PROCESSING"
                              || document.status === "UPLOADING"
                            }
                            onClick={() => void removeDocument(document)}
                            aria-label={`删除 ${document.filename}`}
                          >
                            {deletingId === document.id
                              ? <RefreshCw className="spin" size={17} />
                              : <Trash2 size={17} />}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="table-footer">
              <span>显示 1–{filteredItems.length}，共 {items.length} 条</span>
              <div><button type="button" disabled>上一页</button><button type="button" className="active">1</button><button type="button">2</button><button type="button">下一页</button></div>
            </div>
          </section>
        </div>
      </main>

      {toast && (
        <div className={`toast ${toastTone}`} role="status">
          {toastTone === "success" ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
          <span>{toast}</span>
          <button type="button" onClick={() => setToast("")} aria-label="关闭提示"><X size={16} /></button>
        </div>
      )}
    </div>
  );
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
