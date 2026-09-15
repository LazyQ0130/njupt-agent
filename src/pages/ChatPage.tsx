import {
  AlertCircle,
  BookOpenCheck,
  BookOpenText,
  Bot,
  BriefcaseBusiness,
  Check,
  ChevronRight,
  Clock3,
  Copy,
  FileText,
  GraduationCap,
  Home,
  Medal,
  MessageCircleQuestion,
  Plus,
  RefreshCw,
  School,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import {
  createConversation,
  getChatHistory,
  sendConversationMessage,
  submitAnswerFeedback,
  type ChatAnswer,
  type ChatHistoryItem,
  type FeedbackType,
} from "../api/chat";
import { ApiError } from "../api/request";
import { Header } from "../components/Header";
import { QueryComposer } from "../components/QueryComposer";
import { SiteFooter } from "../components/SiteFooter";
import { chatCategories } from "../data";
import type { ChatMessage } from "../types";

const categoryIcons = [
  GraduationCap,
  BookOpenText,
  Home,
  Medal,
  School,
  BriefcaseBusiness,
];

const starterMessages: ChatMessage[] = [
  {
    id: "welcome",
    role: "assistant",
    content:
      "你好，我是南邮智答。我可以帮你查询新生入学、教务规则、校园生活和专业培养等问题。你可以直接描述遇到的情况，我会尽量依据学校官方资料回答。",
  },
];

function toAssistantMessage(
  answer: ChatAnswer & { chatId?: number | null },
  id = `assistant-${Date.now()}`,
): ChatMessage {
  return {
    id,
    role: "assistant",
    content: answer.answer,
    sources: answer.sources,
    confidence: answer.confidence,
    chatId: answer.chatId,
  };
}

export function ChatPage() {
  const location = useLocation();
  const seededQuery = (location.state as { query?: string } | null)?.query;
  const [activeCategory, setActiveCategory] = useState("教务规则");
  const [messages, setMessages] = useState<ChatMessage[]>(starterMessages);
  const [isThinking, setIsThinking] = useState(false);
  const [thinkingStage, setThinkingStage] = useState<"searching" | "generating">("searching");
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [history, setHistory] = useState<ChatHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState("");
  const [chatError, setChatError] = useState("");
  const [failedQuestion, setFailedQuestion] = useState("");
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [feedbackReasonFor, setFeedbackReasonFor] = useState<string | null>(null);
  const [feedbackReason, setFeedbackReason] = useState("");
  const [feedbackSubmitting, setFeedbackSubmitting] = useState<string | null>(null);
  const didSeed = useRef(false);
  const conversationIdRef = useRef<number | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  const loadHistory = useCallback(async () => {
    setHistoryLoading(true);
    setHistoryError("");
    try {
      setHistory(await getChatHistory());
    } catch (error) {
      setHistoryError(
        error instanceof ApiError ? error.message : "历史记录加载失败",
      );
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  const sendQuestion = async (query: string, showUserMessage = true) => {
    if (isThinking) return;
    if (showUserMessage) {
      const userMessage: ChatMessage = {
        id: `user-${Date.now()}`,
        role: "user",
        content: query,
      };
      setMessages((current) => [...current, userMessage]);
    }
    setChatError("");
    setFailedQuestion("");
    setIsThinking(true);
    setThinkingStage("searching");
    let stageTimer = 0;
    try {
      let activeConversationId = conversationIdRef.current;
      if (!activeConversationId) {
        const created = await createConversation();
        activeConversationId = created.id;
        conversationIdRef.current = created.id;
        setConversationId(created.id);
      }
      stageTimer = window.setTimeout(() => setThinkingStage("generating"), 850);
      const answer = await sendConversationMessage(activeConversationId, query);
      setMessages((current) => [...current, toAssistantMessage(answer)]);
      setHistory((current) => [
        {
          id: Date.now(),
          userId: null,
          question: query,
          answer: answer.answer,
          sources: answer.sources,
          confidence: answer.confidence,
          createdTime: new Date().toISOString(),
        },
        ...current,
      ]);
    } catch (error) {
      setFailedQuestion(query);
      setChatError(
        error instanceof ApiError ? error.message : "回答生成失败，请稍后重试",
      );
    } finally {
      window.clearTimeout(stageTimer);
      setIsThinking(false);
    }
  };

  const startNewConversation = async () => {
    setChatError("");
    setMessages(starterMessages);
    setFeedbackReasonFor(null);
    try {
      const created = await createConversation();
      conversationIdRef.current = created.id;
      setConversationId(created.id);
    } catch (error) {
      setChatError(
        error instanceof ApiError ? error.message : "新会话创建失败",
      );
    }
  };

  const submitFeedback = async (
    message: ChatMessage,
    feedback: FeedbackType,
    reason?: string,
  ) => {
    if (!message.chatId || feedbackSubmitting) return;
    setFeedbackSubmitting(message.id);
    try {
      await submitAnswerFeedback(message.chatId, feedback, reason);
      setMessages((current) =>
        current.map((item) =>
          item.id === message.id ? { ...item, feedback } : item,
        ),
      );
      setFeedbackReasonFor(null);
      setFeedbackReason("");
    } catch (error) {
      setChatError(
        error instanceof ApiError ? error.message : "反馈提交失败，请稍后重试",
      );
    } finally {
      setFeedbackSubmitting(null);
    }
  };

  useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  useEffect(() => {
    if (seededQuery && !didSeed.current) {
      didSeed.current = true;
      void sendQuestion(seededQuery);
      window.history.replaceState({}, document.title);
    }
    // Seeded navigation is intentionally handled once.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seededQuery]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isThinking]);

  const copyMessage = async (message: ChatMessage) => {
    await navigator.clipboard?.writeText(message.content);
    setCopiedId(message.id);
    window.setTimeout(() => setCopiedId(null), 1500);
  };

  return (
    <div className="app-shell chat-shell">
      <Header />
      <main className="chat-layout">
        <aside className="chat-sidebar">
          <button
            className="new-chat"
            type="button"
            onClick={() => void startNewConversation()}
          >
            <Plus size={17} /> 新对话
          </button>
          <div className="sidebar-section-label">问题分类</div>
          <div className="category-nav">
            {chatCategories.map((category, index) => {
              const Icon = categoryIcons[index];
              return (
                <button
                  key={category}
                  type="button"
                  className={activeCategory === category ? "active" : ""}
                  onClick={() => setActiveCategory(category)}
                >
                  <Icon size={18} />
                  <span>{category}</span>
                  <ChevronRight size={15} />
                </button>
              );
            })}
          </div>
          <div className="sidebar-history">
            <div className="sidebar-section-label">最近对话</div>
            {historyLoading && <div className="history-status">正在加载历史记录…</div>}
            {!historyLoading && historyError && (
              <button className="history-retry" type="button" onClick={() => void loadHistory()}>
                <RefreshCw size={15} />
                <span>重新加载历史记录</span>
              </button>
            )}
            {!historyLoading && !historyError && history.length === 0 && (
              <div className="history-status">还没有历史对话</div>
            )}
            {history.slice(0, 6).map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() =>
                  setMessages([
                    starterMessages[0],
                    {
                      id: `history-user-${item.id}`,
                      role: "user",
                      content: item.question,
                    },
                    toAssistantMessage(
                      { ...item, chatId: item.id },
                      `history-answer-${item.id}`,
                    ),
                  ])
                }
              >
                <MessageCircleQuestion size={16} />
                <span>{item.question}</span>
              </button>
            ))}
          </div>
          <div className="sidebar-note">
            <Sparkles size={18} />
            <div><strong>知识库持续更新</strong><span>当前收录 1,600+ 份资料</span></div>
          </div>
        </aside>

        <section className="conversation">
          <div className="conversation-header">
            <div>
              <span className="online-dot" />
              <h1>南邮智答</h1>
              <small>校园知识库已连接</small>
            </div>
            <span className="category-pill">{activeCategory}</span>
          </div>

          <div className="mobile-category-strip">
            {chatCategories.map((category) => (
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

          <div className="messages" aria-live="polite">
            <div className="chat-date"><Clock3 size={13} /> 今天</div>
            {messages.map((message) => (
              <article className={`message-row ${message.role}`} key={message.id}>
                <div className={message.role === "assistant" ? "message-avatar ai" : "message-avatar user"}>
                  {message.role === "assistant" ? <Bot size={19} /> : "你"}
                </div>
                <div className="message-body">
                  <div className="message-label">
                    {message.role === "assistant" ? "南邮智答" : "你"}
                  </div>
                  <div className="message-bubble">
                    {message.content.split("\n").map((line, index) => (
                      line ? <p key={`${message.id}-${index}`}>{line}</p> : <br key={`${message.id}-${index}`} />
                    ))}
                  </div>
                  {message.role === "assistant" && message.sources ? (
                    <div className="answer-evidence">
                      {message.sources.length ? (
                        <div className="source-card">
                          <span><FileText size={18} /></span>
                          <div>
                            <small>参考资料</small>
                            {message.sources[0].url ? (
                              <a
                                href={message.sources[0].url}
                                target="_blank"
                                rel="noreferrer"
                              >
                                <strong>{message.sources[0].title}</strong>
                              </a>
                            ) : (
                              <strong>{message.sources[0].title}</strong>
                            )}
                            <p>
                              {message.sources[0].page ? `第 ${message.sources[0].page} 页 · ` : ""}
                              {message.sources[0].source
                                || (message.sources[0].type === "official"
                                  ? "南邮官方资料"
                                  : message.sources[0].type)}
                              {message.sources.length > 1 ? ` · 另有 ${message.sources.length - 1} 个来源` : ""}
                            </p>
                          </div>
                          <ChevronRight size={17} />
                        </div>
                      ) : (
                        <div className="source-card source-card-empty">
                          <span><FileText size={18} /></span>
                          <div>
                            <small>参考资料</small>
                            <strong>暂无相关资料</strong>
                            <p>建议咨询相关部门或查看学校最新通知</p>
                          </div>
                        </div>
                      )}
                      {message.sources.length && message.confidence !== undefined ? (
                        <div className="confidence">
                          <div><BookOpenCheck size={17} /> 可信度</div>
                          <strong>{message.confidence}%</strong>
                          <span><i style={{ width: `${message.confidence}%` }} /></span>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                  {message.role === "assistant" && (
                    <div className="message-tools">
                      <button type="button" onClick={() => copyMessage(message)}>
                        {copiedId === message.id ? <Check size={14} /> : <Copy size={14} />}
                        {copiedId === message.id ? "已复制" : "复制"}
                      </button>
                      {message.chatId ? (
                        <>
                          <button
                            type="button"
                            className={message.feedback === "HELPFUL" ? "active" : ""}
                            aria-label="回答有帮助"
                            aria-pressed={message.feedback === "HELPFUL"}
                            disabled={feedbackSubmitting === message.id}
                            onClick={() => void submitFeedback(message, "HELPFUL")}
                          >
                            <ThumbsUp size={14} /> 有帮助
                          </button>
                          <button
                            type="button"
                            className={message.feedback === "INCORRECT" ? "active incorrect" : ""}
                            aria-label="回答不准确"
                            aria-pressed={message.feedback === "INCORRECT"}
                            disabled={feedbackSubmitting === message.id}
                            onClick={() => {
                              setFeedbackReasonFor(
                                feedbackReasonFor === message.id ? null : message.id,
                              );
                              setFeedbackReason("");
                            }}
                          >
                            <ThumbsDown size={14} /> 不准确
                          </button>
                        </>
                      ) : null}
                    </div>
                  )}
                  {feedbackReasonFor === message.id && message.chatId ? (
                    <form
                      className="feedback-reason"
                      onSubmit={(event) => {
                        event.preventDefault();
                        void submitFeedback(
                          message,
                          "INCORRECT",
                          feedbackReason.trim() || "用户标记为不准确",
                        );
                      }}
                    >
                      <input
                        value={feedbackReason}
                        onChange={(event) => setFeedbackReason(event.target.value)}
                        placeholder="哪里不准确？可选填"
                        maxLength={1000}
                      />
                      <button type="submit">提交反馈</button>
                    </form>
                  ) : null}
                </div>
              </article>
            ))}
            {isThinking && (
              <article className="message-row assistant">
                <div className="message-avatar ai"><Bot size={19} /></div>
                <div className="message-body">
                  <div className="message-label">南邮智答</div>
                  <div className="thinking-bubble">
                    <i /><i /><i />
                    <span>
                      {thinkingStage === "searching"
                        ? "正在查询南邮知识库..."
                        : "正在生成答案..."}
                    </span>
                  </div>
                </div>
              </article>
            )}
            {chatError && (
              <div className="chat-error" role="alert">
                <AlertCircle size={18} />
                <div>
                  <strong>暂时没有获得回答</strong>
                  <span>{chatError}</span>
                </div>
                <button
                  type="button"
                  onClick={() => void sendQuestion(failedQuestion, false)}
                  disabled={!failedQuestion || isThinking}
                >
                  <RefreshCw size={14} /> 重新提问
                </button>
              </div>
            )}
            <div ref={endRef} />
          </div>

          <div className="chat-composer-wrap">
            <QueryComposer compact onSubmit={(question) => void sendQuestion(question)} />
            <p>
              {conversationId ? `当前会话 #${conversationId} · ` : ""}
              AI 回答可能存在偏差，重要事项请以学校官方通知为准。
            </p>
            <SiteFooter compact />
          </div>
        </section>
      </main>
    </div>
  );
}
