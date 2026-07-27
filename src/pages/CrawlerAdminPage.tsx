import {
  Activity,
  AlertCircle,
  ArrowRight,
  BarChart3,
  CalendarRange,
  CheckCircle2,
  Clock3,
  Database,
  ExternalLink,
  FolderOpen,
  Globe2,
  LayoutDashboard,
  Link2,
  MoreHorizontal,
  RefreshCw,
  RotateCcw,
  Settings,
  ShieldCheck,
  Sparkles,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  getCrawlerStatus,
  reindexCrawler,
  runCrawler,
  runCustomCrawler,
  type CrawlerStatus,
} from "../api/crawler";
import { ApiError } from "../api/request";
import { Brand } from "../components/Brand";

const adminNav = [
  ["总览", LayoutDashboard],
  ["知识文件", FolderOpen],
  ["解析任务", RefreshCw],
  ["知识统计", BarChart3],
  ["设置", Settings],
] as const;

const sites = [
  {
    name: "南京邮电大学官网",
    url: "https://www.njupt.edu.cn/",
    scope: "新生通知、校园服务、综合资讯",
    tone: "blue",
  },
  {
    name: "南京邮电大学本科生院",
    url: "https://jwc.njupt.edu.cn/",
    scope: "选课考试、转专业、培养方案",
    tone: "cyan",
  },
  {
    name: "计算机学院",
    url: "https://cs.njupt.edu.cn/",
    scope: "专业介绍、培养方向、就业信息",
    tone: "violet",
  },
] as const;

export function CrawlerAdminPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState<CrawlerStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [action, setAction] = useState<"run" | "reindex" | "custom" | null>(null);
  const [customUrl, setCustomUrl] = useState(
    "https://coa.njupt.edu.cn/2277/list.htm",
  );
  const [customYears, setCustomYears] = useState(2);
  const [customDateScope, setCustomDateScope] = useState<"RECENT" | "ALL">(
    "RECENT",
  );
  const [customCrawlScope, setCustomCrawlScope] = useState<
    "EXACT_HOST" | "DIRECT_NJUPT_SUBDOMAINS"
  >("EXACT_HOST");
  const [customForceReindex, setCustomForceReindex] = useState(false);
  const [toast, setToast] = useState("");
  const [toastTone, setToastTone] = useState<"success" | "error">("success");

  const loadStatus = useCallback(async (quiet = false) => {
    if (!quiet) setIsLoading(true);
    setLoadError("");
    try {
      setStatus(await getCrawlerStatus());
    } catch (error) {
      setLoadError(
        error instanceof ApiError ? error.message : "采集状态加载失败",
      );
    } finally {
      if (!quiet) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  useEffect(() => {
    if (!status?.running) return;
    const timer = window.setInterval(() => void loadStatus(true), 3000);
    return () => window.clearInterval(timer);
  }, [loadStatus, status?.running]);

  const trigger = async (type: "run" | "reindex") => {
    setAction(type);
    try {
      const result = type === "run" ? await runCrawler() : await reindexCrawler();
      setToastTone("success");
      setToast(
        result.accepted
          ? type === "run"
            ? "采集任务已启动"
            : "全量重新索引任务已启动"
          : result.message,
      );
      await loadStatus(true);
    } catch (error) {
      setToastTone("error");
      setToast(error instanceof ApiError ? error.message : "任务启动失败");
    } finally {
      setAction(null);
    }
  };

  const triggerCustom = async () => {
    const value = customUrl.trim();
    try {
      const parsed = new URL(value);
      if (
        parsed.protocol !== "https:"
        || !parsed.hostname.toLowerCase().endsWith(".njupt.edu.cn")
      ) {
        throw new Error("not official");
      }
    } catch {
      setToastTone("error");
      setToast("请输入南邮官方 HTTPS 网址（*.njupt.edu.cn）");
      return;
    }

    setAction("custom");
    try {
      const result = await runCustomCrawler(
        value,
        customYears,
        50,
        customForceReindex,
        customDateScope,
        customCrawlScope,
      );
      setToastTone("success");
      const scopePrefix = customCrawlScope === "DIRECT_NJUPT_SUBDOMAINS"
        ? "学院目录扩展采集已启动；"
        : "";
      setToast(
        result.accepted
          ? scopePrefix + (customDateScope === "ALL"
            ? customForceReindex
              ? "定向重新索引已启动，将覆盖旧页面和无可靠发布日期页面"
              : "定向采集已启动，将收录旧页面和无可靠发布日期页面"
            : customForceReindex
              ? `定向重新索引已启动，将覆盖近 ${customYears} 年已有内容`
              : `定向采集已启动，将只收录近 ${customYears} 年内容`)
          : result.message,
      );
      await loadStatus(true);
    } catch (error) {
      setToastTone("error");
      setToast(error instanceof ApiError ? error.message : "定向采集启动失败");
    } finally {
      setAction(null);
    }
  };

  const run = status?.latestRun;
  const successRate = useMemo(() => {
    if (!run?.totalPages) return 0;
    return Math.round((run.successCount / run.totalPages) * 100);
  }, [run]);
  const progress = useMemo(() => {
    if (!run?.totalPages) return status?.running ? 12 : 0;
    const handled = run.successCount + run.failedCount + run.robotsDeniedCount;
    return Math.min(100, Math.round((handled / run.totalPages) * 100));
  }, [run, status?.running]);

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
              className={label === "解析任务" ? "active" : ""}
              onClick={() => {
                if (label === "设置") navigate("/admin/settings");
                if (label === "知识文件") navigate("/admin");
                if (label === "知识统计") navigate("/admin/quality");
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
        <header className="admin-topbar crawler-topbar">
          <div>
            <h1>官网知识自动采集</h1>
            <p>受控采集南邮官方站点，自动清洗、分类并更新知识库</p>
          </div>
          <div className={`system-status${loadError ? " offline" : ""}`}>
            <i /> {loadError ? "采集服务连接异常" : status?.running ? "采集任务运行中" : "采集服务就绪"}
          </div>
        </header>

        <div className="admin-content crawler-content">
          <section className="crawler-hero">
            <div>
              <span className="crawler-eyebrow"><Sparkles size={15} /> 自动化知识管线</span>
              <h2>让校园知识持续保持最新</h2>
              <p>系统遵循 robots.txt、域名白名单和访问频率限制，只采集学生相关的南邮官方公开内容。</p>
            </div>
            <div className="crawler-actions">
              <button
                type="button"
                className="secondary"
                disabled={Boolean(action) || status?.running}
                onClick={() => void trigger("reindex")}
              >
                <RotateCcw className={action === "reindex" ? "spin" : ""} size={17} />
                重新索引
              </button>
              <button
                type="button"
                className="primary"
                disabled={Boolean(action) || status?.running}
                onClick={() => void trigger("run")}
              >
                <RefreshCw className={action === "run" || status?.running ? "spin" : ""} size={17} />
                {status?.running ? "正在采集" : "立即执行爬取"}
              </button>
            </div>
          </section>

          <section className="crawler-custom-card">
            <div className="crawler-custom-heading">
              <span><Link2 size={19} /></span>
              <div>
                <h2>定向采集官方页面</h2>
                <p>粘贴学院通知列表或栏目网址，系统会发现详情页并仅收录指定时间范围内的内容。</p>
              </div>
              <span className="crawler-official-badge"><ShieldCheck size={14} /> 南邮官方域名</span>
            </div>

            <form
              className="crawler-custom-form"
              onSubmit={(event) => {
                event.preventDefault();
                void triggerCustom();
              }}
            >
              <label className="crawler-url-field">
                <span>采集网址</span>
                <div>
                  <Globe2 size={17} />
                  <input
                    type="url"
                    value={customUrl}
                    onChange={(event) => setCustomUrl(event.target.value)}
                    placeholder="https://学院域名.njupt.edu.cn/通知栏目/list.htm"
                    autoComplete="url"
                    spellCheck={false}
                    required
                  />
                </div>
              </label>
              <label className="crawler-years-field">
                <span>内容范围</span>
                <div>
                  <CalendarRange size={16} />
                  <select
                    value={customDateScope === "ALL" ? "ALL" : customYears}
                    onChange={(event) => {
                      if (event.target.value === "ALL") {
                        setCustomDateScope("ALL");
                        return;
                      }
                      setCustomDateScope("RECENT");
                      setCustomYears(Number(event.target.value));
                    }}
                  >
                    <option value={1}>近 1 年</option>
                    <option value={2}>近 2 年</option>
                    <option value={3}>近 3 年</option>
                    <option value={5}>近 5 年</option>
                    <option value="ALL">无发布日期限制</option>
                  </select>
                </div>
              </label>
              <button
                type="submit"
                disabled={Boolean(action) || status?.running}
              >
                <RefreshCw className={action === "custom" ? "spin" : ""} size={17} />
                {action === "custom" ? "正在启动" : "开始定向采集"}
                {action !== "custom" && <ArrowRight size={15} />}
              </button>
            </form>

            <label className="crawler-scope-option">
              <input
                type="checkbox"
                checked={customCrawlScope === "DIRECT_NJUPT_SUBDOMAINS"}
                onChange={(event) => setCustomCrawlScope(
                  event.target.checked
                    ? "DIRECT_NJUPT_SUBDOMAINS"
                    : "EXACT_HOST",
                )}
              />
              <span aria-hidden="true" />
              <div>
                <strong>扩展到目录正文中的学院官网</strong>
                <small>仅接纳南邮 HTTPS 子域名直链；最多 30 个站点，每站 50 页，不继续跨站扩散。</small>
              </div>
            </label>

            <div className="crawler-custom-note">
              <span>时间边界</span>
              {customDateScope === "ALL"
                ? "旧页面和无法识别发布日期的合格详情页均可进入知识库。"
                : `自 ${formatCutoff(customYears)} 起发布的页面才会进入知识库；无法识别发布日期的页面会跳过。`}
              <i />
              {customCrawlScope === "DIRECT_NJUPT_SUBDOMAINS"
                ? "目录页负责发现学院入口，进入学院后仅跟随该精确域名。"
                : "仅跟随当前网址相同域名，最多处理 50 个页面。"}
              <label className="crawler-force-option">
                <input
                  type="checkbox"
                  checked={customForceReindex}
                  onChange={(event) => setCustomForceReindex(event.target.checked)}
                />
                <span aria-hidden="true" />
                覆盖已有索引
              </label>
            </div>
          </section>

          <section className="admin-stats crawler-stats">
            {([
              ["已收录网页", status?.totalWebpages ?? 0, "官方网页总数", Globe2, "blue"],
              ["本次成功", run?.successCount ?? 0, `${successRate}% 成功率`, CheckCircle2, "green"],
              ["本次失败", run?.failedCount ?? 0, "异常已隔离", AlertCircle, "orange"],
              ["最后更新", formatRelative(status?.lastUpdated), "自动增量同步", Clock3, "violet"],
            ] as const).map(([label, value, sub, Icon, tone]) => (
              <article key={label as string} className={`crawler-stat ${tone}`}>
                <span><Icon size={20} /></span>
                <div>
                  <small>{label as string}</small>
                  <strong>{typeof value === "number" ? value.toLocaleString() : value as string}</strong>
                  <p>{sub as string}</p>
                </div>
              </article>
            ))}
          </section>

          {isLoading ? (
            <section className="crawler-state-card"><RefreshCw className="spin" size={22} /> 正在连接采集服务…</section>
          ) : loadError ? (
            <section className="crawler-state-card error">
              <AlertCircle size={22} />
              <div><strong>无法读取采集状态</strong><p>{loadError}</p></div>
              <button type="button" onClick={() => void loadStatus()}>重新连接</button>
            </section>
          ) : (
            <section className="crawler-run-card">
              <div className="crawler-run-heading">
                <div className={`run-icon${status?.running ? " running" : ""}`}>
                  <Activity size={22} />
                </div>
                <div>
                  <span className="run-title">
                    {status?.running ? "正在采集官方知识" : run ? "最近一次采集任务" : "等待首次采集"}
                    <i className={run?.status.toLowerCase()}>{translateStatus(run?.status)}</i>
                  </span>
                  <p>{run ? `任务 #${run.id} · ${formatDateTime(run.startedAt)}` : "点击「立即执行爬取」开始建立官网知识库"}</p>
                </div>
                {run?.forceReindex && <span className="reindex-tag">全量重建</span>}
              </div>

              <div className="crawler-progress">
                <div>
                  <span>{status?.running ? "任务进度" : "处理概览"}</span>
                  <strong>{progress}%</strong>
                </div>
                <span><i style={{ width: `${progress}%` }} /></span>
              </div>

              <div className="crawler-run-metrics">
                <div><strong>{run?.totalPages ?? 0}</strong><span>发现页面</span></div>
                <div><strong>{run?.indexedCount ?? 0}</strong><span>新增 / 更新</span></div>
                <div><strong>{run?.unchangedCount ?? 0}</strong><span>内容未变化</span></div>
                <div><strong>{run?.robotsDeniedCount ?? 0}</strong><span>规则阻止</span></div>
              </div>
              {run?.lastError && <div className="crawler-last-error"><AlertCircle size={15} /> {run.lastError}</div>}
            </section>
          )}

          <section className="crawler-sites-section">
            <div className="admin-section-heading">
              <div><h2>预设数据源</h2><p>定向采集可扩展到经双重校验的南邮官方 HTTPS 子域名</p></div>
              <span><ShieldCheck size={15} /> 安全采集已启用</span>
            </div>
            <div className="crawler-sites">
              {sites.map((site) => (
                <article key={site.url}>
                  <span className={`site-icon ${site.tone}`}><Globe2 size={21} /></span>
                  <div>
                    <strong>{site.name}</strong>
                    <p>{site.scope}</p>
                    <a href={site.url} target="_blank" rel="noreferrer">
                      {new URL(site.url).hostname} <ExternalLink size={13} />
                    </a>
                  </div>
                  <span className="site-status"><i /> 预设</span>
                </article>
              ))}
            </div>
          </section>

          <section className="crawler-policy">
            <Database size={20} />
            <div><strong>增量更新策略</strong><p>正文清洗后计算 SHA-256；内容未变化时跳过 Embedding，仅更新时间戳。</p></div>
            <span>Hash 去重</span>
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

function translateStatus(status?: string) {
  const labels: Record<string, string> = {
    RUNNING: "运行中",
    COMPLETED: "已完成",
    FAILED: "失败",
  };
  return status ? labels[status] ?? status : "未运行";
}

function formatDateTime(value?: string | null) {
  if (!value) return "尚无记录";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatRelative(value?: string | null) {
  if (!value) return "尚未";
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000));
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)} 小时前`;
  return `${Math.floor(minutes / 1440)} 天前`;
}

function formatCutoff(years: number) {
  const cutoff = new Date();
  cutoff.setFullYear(cutoff.getFullYear() - years);
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(cutoff);
}
