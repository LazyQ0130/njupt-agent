import { ArrowUp, Mic, Sparkles } from "lucide-react";
import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { quickQuestions } from "../data";

interface QueryComposerProps {
  compact?: boolean;
  onSubmit?: (query: string) => void;
  initialValue?: string;
}

export function QueryComposer({
  compact = false,
  onSubmit,
  initialValue = "",
}: QueryComposerProps) {
  const [query, setQuery] = useState(initialValue);
  const navigate = useNavigate();

  const submit = (event?: FormEvent) => {
    event?.preventDefault();
    const value = query.trim();
    if (!value) return;
    if (onSubmit) {
      onSubmit(value);
      setQuery("");
    } else {
      navigate("/chat", { state: { query: value } });
    }
  };

  return (
    <form
      className={compact ? "query-composer compact" : "query-composer"}
      onSubmit={submit}
    >
      {!compact && (
        <div className="composer-kicker">
          <Sparkles size={15} />
          <span>问问你的校园 AI 助手</span>
        </div>
      )}
      <label className="sr-only" htmlFor={compact ? "chat-query" : "home-query"}>
        输入你的校园问题
      </label>
      <textarea
        id={compact ? "chat-query" : "home-query"}
        value={query}
        rows={compact ? 1 : 2}
        onChange={(event) => setQuery(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            submit();
          }
        }}
        placeholder="例如：南邮转专业需要什么条件？"
      />
      {!compact && (
        <div className="prompt-suggestions">
          {quickQuestions.map((question) => (
            <button key={question} type="button" onClick={() => setQuery(question)}>
              {question}
            </button>
          ))}
        </div>
      )}
      <div className="composer-actions">
        <button className="voice-button" type="button" aria-label="语音输入">
          <Mic size={19} />
        </button>
        <span>{compact ? "Enter 发送，Shift + Enter 换行" : "答案将优先引用南邮官方资料"}</span>
        <button
          className="send-button"
          type="submit"
          disabled={!query.trim()}
          aria-label="发送问题"
        >
          <ArrowUp size={20} strokeWidth={2.5} />
        </button>
      </div>
    </form>
  );
}
