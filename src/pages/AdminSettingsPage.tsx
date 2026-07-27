import {
  Activity,
  AlertTriangle,
  BarChart3,
  CheckCircle2,
  Clock3,
  Coins,
  Database,
  Eye,
  EyeOff,
  FolderOpen,
  KeyRound,
  LayoutDashboard,
  MoreHorizontal,
  RefreshCw,
  Settings,
  ShieldCheck,
  Sparkles,
  Trash2,
  X,
  Zap,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  clearAiConfig,
  getAiOverview,
  saveAiConfig,
  setAiConfigStatus,
  type AiOverview,
  type UsagePeriod,
} from "../api/adminAi";
import { ApiError } from "../api/request";
import { Brand } from "../components/Brand";

const adminNav = [
  ["总览", LayoutDashboard, "/admin"],
  ["知识文件", FolderOpen, "/admin"],
  ["解析任务", RefreshCw, "/admin/crawler"],
  ["知识统计", BarChart3, "/admin/quality"],
  ["设置", Settings, "/admin/settings"],
] as const;

const periods: Array<[UsagePeriod, string]> = [
  ["today", "今日"],
  ["7d", "近 7 天"],
  ["30d", "近 30 天"],
  ["all", "累计"],
];

export function AdminSettingsPage() {
  const navigate = useNavigate();
  const [overview, setOverview] = useState<AiOverview | null>(null);
  const [period, setPeriod] = useState<UsagePeriod>("today");
  const [apiKey, setApiKey] = useState("");
  const [model, setModel] = useState("deepseek-v4-flash");
  const [showKey, setShowKey] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [action, setAction] = useState<
    "save" | "status" | "clear" | "refresh" | null
  >(null);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [toastTone, setToastTone] = useState<"success" | "error">("success");
  const [confirmClear, setConfirmClear] = useState(false);
  const [nextRefreshAt, setNextRefreshAt] = useState(0);

  const loadOverview = useCallback(
    async (selectedPeriod: UsagePeriod, refreshBalance = false) => {
      setError("");
      try {
        const data = await getAiOverview(selectedPeriod, refreshBalance);
        setOverview(data);
        setModel(
          data.config.model === "deepseek-v4-pro"
            ? "deepseek-v4-pro"
            : "deepseek-v4-flash",
        );
        if (refreshBalance) setNextRefreshAt(Date.now() + 60_000);
      } catch (loadError) {
        setError(
          loadError instanceof ApiError
            ? loadError.message
            : "AI 服务设置加载失败",
        );
      } finally {
        setIsLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    void loadOverview("today");
  }, [loadOverview]);

  const changePeriod = (value: UsagePeriod) => {
    setPeriod(value);
    void loadOverview(value);
  };

  const save = async () => {
    if (!apiKey.trim()) {
      setToastTone("error");
      setToast("请输入 DeepSeek API Key");
      return;
    }
    setAction("save");
    setError("");
    try {
      const result = await saveAiConfig(apiKey.trim(), model);
      setApiKey("");
      setShowKey(false);
      setOverview((current) =>
        current
          ? { ...current, config: result.config, balance: result.balance }
          : current,
      );
      setNextRefreshAt(Date.now() + 60_000);
      setToastTone("success");
      setToast("连接验证通过，学生问答已切换到新配置");
      await loadOverview(period);
    } catch (saveError) {
      setToastTone("error");
      setToast(
        saveError instanceof ApiError
          ? saveError.message
          : "配置保存失败，请稍后重试",
      );
    } finally {
      setAction(null);
    }
  };

  const toggleStatus = async () => {
    if (!overview) return;
    setAction("status");
    try {
      const config = await setAiConfigStatus(!overview.config.enabled);
      setOverview({ ...overview, config });
      setToastTone("success");
      setToast(config.enabled ? "DeepSeek 已启用" : "DeepSeek 已停用");
      await loadOverview(period);
    } catch (statusError) {
      setToastTone("error");
      setToast(
        statusError instanceof ApiError
          ? statusError.message
          : "状态更新失败",
      );
    } finally {
      setAction(null);
    }
  };

  const clear = async () => {
    if (!confirmClear) {
      setConfirmClear(true);
      return;
    }
    setAction("clear");
    try {
      const config = await clearAiConfig();
      setApiKey("");
      setOverview((current) =>
        current ? { ...current, config } : current,
      );
      setConfirmClear(false);
      setToastTone("success");
      setToast("数据库 Key 已清除，环境变量不会被自动恢复");
      await loadOverview(period);
    } catch (clearError) {
      setToastTone("error");
      setToast(
        clearError instanceof ApiError ? clearError.message : "Key 清除失败",
      );
    } finally {
      setAction(null);
    }
  };

  const refreshBalance = async () => {
    if (Date.now() < nextRefreshAt) {
      setToastTone("error");
      setToast("余额刚刚同步过，请 60 秒后再试");
      return;
    }
    setAction("refresh");
    await loadOverview(period, true);
    setAction(null);
  };

  const config = overview?.config;
  const usage = overview?.usage;
  const balance = overview?.balance;
  const statusLabel = !config?.configured
    ? "尚未配置"
    : config.enabled
      ? "服务已启用"
      : "服务已停用";

  const metrics = useMemo(
    () => [
      {
        label: "请求总数",
        value: formatNumber(usage?.requestCount),
        helper: `${formatNumber(usage?.failureCount)} 次失败`,
        icon: Activity,
      },
      {
        label: "调用成功率",
        value: `${usage?.successRate ?? 0}%`,
        helper: `${formatNumber(usage?.successCount)} 次成功`,
        icon: CheckCircle2,
      },
      {
        label: "输入 / 输出 Token",
        value: `${formatCompact(usage?.promptTokens)} / ${formatCompact(usage?.completionTokens)}`,
        helper: `总计 ${formatCompact(usage?.totalTokens)}`,
        icon: Zap,
      },
      {
        label: "缓存 Token",
        value: formatCompact(usage?.cacheHitTokens),
        helper: `未命中 ${formatCompact(usage?.cacheMissTokens)}`,
        icon: Database,
      },
    ],
    [usage],
  );

  return (
    <div className="admin-app">
      <aside className="admin-sidebar">
        <div className="admin-brand"><Brand /></div>
        <nav aria-label="管理后台导航">
          <span>工作台</span>
          {adminNav.map(([label, Icon, path]) => (
            <button
              key={label}
              type="button"
              className={label === "设置" ? "active" : ""}
              onClick={() => path && navigate(path)}
            >
              <Icon size={18} />
              {label}
            </button>
          ))}
        </nav>
        <div className="admin-account">
          <span>管</span>
          <div>
            <strong>知识库管理员</strong>
            <small>admin@njupt.edu.cn</small>
          </div>
          <MoreHorizontal size={17} />
        </div>
      </aside>

      <main className="admin-main">
        <header className="admin-topbar">
          <div>
            <h1>AI 服务设置</h1>
            <p>安全配置 DeepSeek，并查看账户余额与本应用 Token 用量</p>
          </div>
          <div
            className={`system-status${error || !config?.enabled ? " offline" : ""}`}
          >
            <i /> {error || statusLabel}
          </div>
        </header>

        <div className="admin-content ai-settings-content">
          {error && (
            <section className="ai-settings-alert error">
              <AlertTriangle size={17} />
              <div><strong>设置加载失败</strong><p>{error}</p></div>
              <button type="button" onClick={() => void loadOverview(period)}>
                重新连接
              </button>
            </section>
          )}

          {!config?.storageAvailable && !isLoading && (
            <section className="ai-settings-alert">
              <ShieldCheck size={18} />
              <div>
                <strong>服务器加密主密钥尚未配置</strong>
                <p>
                  请先设置 AI_CONFIG_MASTER_KEY。现有环境变量 Key 仍可使用，但后台不能保存新 Key。
                </p>
              </div>
            </section>
          )}

          <section className="ai-settings-grid">
            <article className="ai-settings-panel ai-config-panel">
              <div className="ai-panel-heading">
                <span><KeyRound size={19} /></span>
                <div>
                  <h2>DeepSeek 连接配置</h2>
                  <p>保存前自动验证；验证失败不会覆盖原有可用配置</p>
                </div>
                <span className={`ai-config-badge${config?.enabled ? " active" : ""}`}>
                  {statusLabel}
                </span>
              </div>

              <div className="ai-current-config">
                <div>
                  <span>当前 Key</span>
                  <strong>{config?.keyMask || "未配置"}</strong>
                </div>
                <div>
                  <span>配置来源</span>
                  <strong>{sourceLabel(config?.source)}</strong>
                </div>
                <div>
                  <span>当前模型</span>
                  <strong>{config?.model || "deepseek-v4-flash"}</strong>
                </div>
                <div>
                  <span>上次验证</span>
                  <strong>{formatDate(config?.verifiedAt)}</strong>
                </div>
              </div>

              <label className="ai-settings-field">
                <span className="ai-key-field-heading">
                  <b>DeepSeek API Key（可随时设置或修改）</b>
                  <em>{config?.configured ? `当前 ${config.keyMask}` : "当前未设置"}</em>
                </span>
                <div className="ai-key-input">
                  <input
                    value={apiKey}
                    type={showKey ? "text" : "password"}
                    autoComplete="new-password"
                    spellCheck={false}
                    aria-label="设置或修改 DeepSeek API Key"
                    placeholder={
                      config?.configured
                        ? "在此输入新的 API Key，保存后替换当前 Key"
                        : "在此粘贴 DeepSeek API Key"
                    }
                    onChange={(event) => setApiKey(event.target.value)}
                  />
                  <button
                    type="button"
                    aria-label={showKey ? "隐藏 API Key" : "显示 API Key"}
                    onClick={() => setShowKey((value) => !value)}
                  >
                    {showKey ? <EyeOff size={17} /> : <Eye size={17} />}
                  </button>
                </div>
                <small>
                  {config?.configured
                    ? "当前 Key 不会被回显；直接输入新 Key 并点击下方按钮即可验证、修改并立即应用。"
                    : "输入后点击下方按钮完成验证并保存，学生问答无需重启即可使用。"}
                </small>
              </label>

              <label className="ai-settings-field">
                <span>聊天模型</span>
                <select value={model} onChange={(event) => setModel(event.target.value)}>
                  <option value="deepseek-v4-flash">deepseek-v4-flash（推荐）</option>
                  <option value="deepseek-v4-pro">deepseek-v4-pro</option>
                </select>
              </label>

              <div className="ai-config-actions">
                <button
                  className="primary"
                  type="button"
                  disabled={Boolean(action) || !config?.storageAvailable}
                  onClick={() => void save()}
                >
                  {action === "save"
                    ? <RefreshCw className="spin" size={16} />
                    : <ShieldCheck size={16} />}
                  {config?.configured ? "验证、修改并立即应用" : "验证、保存并立即应用"}
                </button>
                {config?.configured && (
                  <>
                    <button
                      type="button"
                      disabled={Boolean(action)}
                      onClick={() => void toggleStatus()}
                    >
                      {config.enabled ? "停用服务" : "重新启用"}
                    </button>
                    <button
                      className={confirmClear ? "danger confirm" : "danger"}
                      type="button"
                      disabled={Boolean(action)}
                      onClick={() => void clear()}
                      onBlur={() => setConfirmClear(false)}
                    >
                      <Trash2 size={15} />
                      {confirmClear ? "再次点击确认清除" : "清除 Key"}
                    </button>
                  </>
                )}
              </div>
            </article>

            <article className="ai-settings-panel ai-balance-panel">
              <div className="ai-panel-heading compact">
                <span><Coins size={19} /></span>
                <div>
                  <h2>账户余额</h2>
                  <p>来自 DeepSeek 官方账户余额接口</p>
                </div>
                <button
                  type="button"
                  disabled={Boolean(action) || !config?.enabled}
                  onClick={() => void refreshBalance()}
                >
                  <RefreshCw
                    className={action === "refresh" ? "spin" : ""}
                    size={15}
                  />
                  刷新
                </button>
              </div>

              <div className={`ai-balance-state ${balance?.syncStatus?.toLowerCase() || ""}`}>
                {balance?.available
                  ? <CheckCircle2 size={17} />
                  : <AlertTriangle size={17} />}
                <div>
                  <strong>{balance?.message || "正在同步账户余额"}</strong>
                  <span>
                    {balance?.syncedAt
                      ? `同步于 ${formatDate(balance.syncedAt)}`
                      : "尚无同步记录"}
                  </span>
                </div>
              </div>

              <div className="ai-balance-list">
                {balance?.balances.length ? (
                  balance.balances.map((item) => (
                    <div key={item.currency}>
                      <div>
                        <span>{item.currency}</span>
                        <strong>{item.totalBalance}</strong>
                      </div>
                      <dl>
                        <div><dt>赠送余额</dt><dd>{item.grantedBalance}</dd></div>
                        <div><dt>充值余额</dt><dd>{item.toppedUpBalance}</dd></div>
                      </dl>
                    </div>
                  ))
                ) : (
                  <div className="ai-balance-empty">
                    <Coins size={28} />
                    <p>{config?.configured ? "暂无余额数据" : "保存 Key 后显示账户余额"}</p>
                  </div>
                )}
              </div>

              <p className="ai-scope-note">
                <ShieldCheck size={14} />
                余额是该 DeepSeek 账户的实时总余额，可能包含其他应用产生的消费。
              </p>
            </article>
          </section>

          <section className="ai-usage-section">
            <div className="ai-usage-heading">
              <div>
                <span><Sparkles size={15} /> Usage Analytics</span>
                <h2>本应用 Token 用量</h2>
                <p>仅统计经过“南邮智答”的模型调用，不记录问题、回答或学生身份。</p>
              </div>
              <div className="ai-period-tabs" role="tablist" aria-label="用量时间范围">
                {periods.map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    className={period === value ? "active" : ""}
                    onClick={() => changePeriod(value)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            <div className="ai-usage-metrics">
              {metrics.map(({ label, value, helper, icon: Icon }) => (
                <article key={label}>
                  <span><Icon size={18} /></span>
                  <div><small>{label}</small><strong>{value}</strong><p>{helper}</p></div>
                </article>
              ))}
            </div>

            <div className="ai-usage-footer">
              <span><Clock3 size={14} /> 统计时区：Asia/Shanghai</span>
              <span><ShieldCheck size={14} /> 不保存 Prompt、回答正文或完整 API Key</span>
            </div>
          </section>
        </div>
      </main>

      {toast && (
        <div className={`toast ${toastTone}`}>
          {toastTone === "success"
            ? <CheckCircle2 size={17} />
            : <AlertTriangle size={17} />}
          <span>{toast}</span>
          <button type="button" onClick={() => setToast("")} aria-label="关闭提示">
            <X size={16} />
          </button>
        </div>
      )}
    </div>
  );
}

function sourceLabel(source?: string) {
  if (source === "DATABASE") return "后台加密配置";
  if (source === "ENVIRONMENT") return "服务器环境变量";
  return "暂无配置";
}

function formatDate(value?: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatNumber(value?: number) {
  return (value ?? 0).toLocaleString("zh-CN");
}

function formatCompact(value?: number) {
  const amount = value ?? 0;
  return new Intl.NumberFormat("zh-CN", {
    notation: amount >= 10_000 ? "compact" : "standard",
    maximumFractionDigits: 1,
  }).format(amount);
}
