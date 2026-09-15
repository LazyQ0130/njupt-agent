import {
  AlertCircle,
  BarChart3,
  CheckCircle2,
  Clock3,
  Database,
  FlaskConical,
  FolderOpen,
  LayoutDashboard,
  MessageSquareText,
  MoreHorizontal,
  RefreshCw,
  Settings,
  ShieldCheck,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
  X,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  getQualityStats,
  runEvaluation,
  type EvaluationReport,
  type QualityStats,
} from "../api/quality";
import { ApiError } from "../api/request";
import { Brand } from "../components/Brand";

const adminNav = [
  ["总览", LayoutDashboard],
  ["知识文件", FolderOpen],
  ["解析任务", RefreshCw],
  ["知识统计", BarChart3],
  ["设置", Settings],
] as const;

const categoryNames: Record<string, string> = {
  NEW_STUDENT: "新生入学",
  ACADEMIC: "教务学习",
  LIFE: "校园生活",
  MAJOR: "专业发展",
  CAREER: "就业考研",
  SCHOOL_OVERVIEW: "学校概况",
  ORGANIZATION: "组织机构",
  RESEARCH: "科研学术",
};

export function QualityAdminPage() {
  const navigate = useNavigate();
  const [stats, setStats] = useState<QualityStats | null>(null);
  const [report, setReport] = useState<EvaluationReport | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isEvaluating, setIsEvaluating] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");

  const loadStats = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      setStats(await getQualityStats());
    } catch (loadError) {
      setError(
        loadError instanceof ApiError ? loadError.message : "质量统计加载失败",
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  const evaluate = async () => {
    setIsEvaluating(true);
    setError("");
    try {
      const result = await runEvaluation();
      setReport(result);
      setToast(`${result.total} 题评测完成，平均得分 ${result.averageScore}`);
    } catch (evaluationError) {
      setError(
        evaluationError instanceof ApiError
          ? evaluationError.message
          : "评测执行失败",
      );
    } finally {
      setIsEvaluating(false);
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
              className={label === "知识统计" ? "active" : ""}
              onClick={() => {
                if (label === "设置") navigate("/admin/settings");
                if (label === "知识文件") navigate("/admin");
                if (label === "解析任务") navigate("/admin/crawler");
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
            <h1>AI 质量中心</h1>
            <p>跟踪回答反馈，运行标准问题集并持续优化知识库</p>
          </div>
          <div className={`system-status${error ? " offline" : ""}`}>
            <i /> {error ? "质量服务连接异常" : "质量监控已启用"}
          </div>
        </header>

        <div className="admin-content quality-content">
          <section className="quality-hero">
            <div>
              <span><Sparkles size={15} /> RAG Evaluation</span>
              <h2>用真实问题衡量答案质量</h2>
              <p>使用内置南邮真实场景问题集，检查来源覆盖、来源匹配、关键词命中与空回答。</p>
            </div>
            <button type="button" disabled={isEvaluating} onClick={() => void evaluate()}>
              <FlaskConical className={isEvaluating ? "spin" : ""} size={17} />
              {isEvaluating ? "正在运行评测" : "运行 RAG 评测"}
            </button>
          </section>

          <section className="admin-stats quality-stats">
            {([
              ["总回答次数", stats?.totalAnswers ?? 0, "已生成回答", MessageSquareText],
              ["匿名用户数", stats?.uniqueAnonymousUsers ?? 0, "去重会话标识", Database],
              ["好评率", `${stats?.helpfulRate ?? 0}%`, `${stats?.helpfulCount ?? 0} 条好评`, ThumbsUp],
              ["差评数量", stats?.incorrectCount ?? 0, "待核查回答", ThumbsDown],
              ["已收集反馈", stats?.feedbackCount ?? 0, "质量样本", Database],
            ] as const).map(([label, value, sub, Icon]) => (
              <article key={label as string}>
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
            <section className="crawler-state-card"><RefreshCw className="spin" size={20} /> 正在加载质量统计…</section>
          ) : error && !stats ? (
            <section className="crawler-state-card error">
              <AlertCircle size={20} />
              <div><strong>质量统计不可用</strong><p>{error}</p></div>
              <button type="button" onClick={() => void loadStats()}>重新连接</button>
            </section>
          ) : (
            <div className="quality-grid">
              <section className="quality-panel frequent-panel">
                <div className="quality-panel-heading">
                  <div><h2>高频学生问题</h2><p>根据历史问答实时汇总</p></div>
                  <BarChart3 size={19} />
                </div>
                <div className="frequent-list">
                  {stats?.highFrequencyQuestions.length ? (
                    stats.highFrequencyQuestions.slice(0, 8).map((item, index) => (
                      <div key={item.question}>
                        <span>{index + 1}</span>
                        <p>{item.question}</p>
                        <strong>{item.count} 次</strong>
                      </div>
                    ))
                  ) : (
                    <div className="quality-empty">暂无历史问题，完成问答后将在这里统计。</div>
                  )}
                </div>
              </section>

              <section className="quality-panel evaluation-panel">
                <div className="quality-panel-heading">
                  <div><h2>最近评测报告</h2><p>规则评分满分 100</p></div>
                  {report ? <CheckCircle2 size={19} /> : <Clock3 size={19} />}
                </div>
                {report ? (
                  <>
                    <div className="quality-score">
                      <strong>{report.averageScore}</strong><span>平均分</span>
                      <i><b style={{ width: `${report.averageScore}%` }} /></i>
                    </div>
                    <div className={`evaluation-gate ${report.gatePassed ? "passed" : "failed"}`}>
                      <strong>{report.gatePassed ? "达到上线门槛" : "未达到上线门槛"}</strong>
                      {!report.gatePassed && <span>{report.gateFailures.join("、")}</span>}
                    </div>
                    <div className="quality-rates">
                      <div><span>来源覆盖</span><strong>{report.sourceCoverage}%</strong></div>
                      <div><span>来源匹配</span><strong>{report.sourceMatchRate}%</strong></div>
                      <div><span>关键词命中</span><strong>{report.keywordMatchRate}%</strong></div>
                      <div><span>非空回答</span><strong>{report.nonEmptyRate}%</strong></div>
                      <div>
                        <span>人工准确率</span>
                        <strong>
                          {report.humanAccuracyRate === null
                            ? "待复核"
                            : `${report.humanAccuracyRate}%`}
                        </strong>
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="evaluation-placeholder">
                    <FlaskConical size={28} />
                    <strong>尚未运行评测</strong>
                    <p>评测会调用当前 RAG 问答链路，请先确认知识库和模型服务可用。</p>
                  </div>
                )}
              </section>
            </div>
          )}

          {report && (
            <section className="quality-panel category-report">
              <div className="quality-panel-heading">
                <div><h2>分类得分</h2><p>{report.total} 个问题 · {new Date(report.executedAt).toLocaleString("zh-CN")}</p></div>
                <ShieldCheck size={19} />
              </div>
              <div className="category-score-grid">
                {Object.entries(report.categoryScores).map(([category, score]) => (
                  <div key={category}>
                    <span>{categoryNames[category] ?? category}</span>
                    <strong>{score}</strong>
                    <i><b style={{ width: `${score}%` }} /></i>
                  </div>
                ))}
              </div>
            </section>
          )}

          {!isLoading && stats && (
            <section className="quality-panel low-quality-panel">
              <div className="quality-panel-heading">
                <div>
                  <h2>低质量问题簇</h2>
                  <p>
                    置信度低于 70 或被标记为不准确的问题，当前 {stats.lowConfidenceIncorrectCount} 条同时满足两项
                  </p>
                </div>
                <ThumbsDown size={19} />
              </div>
              {stats.lowQualityQuestions.length ? (
                <div className="low-quality-list">
                  {stats.lowQualityQuestions.map((item) => (
                    <div key={item.question}>
                      <strong>{item.question}</strong>
                      <span>{item.occurrences} 次 · {item.incorrectCount} 条差评 · 平均置信度 {item.averageConfidence}%</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="quality-empty">暂未发现低置信度或差评问题。</div>
              )}
            </section>
          )}
        </div>
      </main>

      {toast && (
        <div className="toast success" role="status">
          <CheckCircle2 size={18} />
          <span>{toast}</span>
          <button type="button" onClick={() => setToast("")} aria-label="关闭提示"><X size={16} /></button>
        </div>
      )}
    </div>
  );
}
