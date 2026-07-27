import {
  ArrowLeft,
  Eye,
  EyeOff,
  KeyRound,
  LoaderCircle,
  LockKeyhole,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { FormEvent, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { loginAdmin } from "../api/adminAuth";
import { ApiError } from "../api/request";
import { Brand } from "../components/Brand";

export function AdminLoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!username.trim() || !password) {
      setError("请输入管理员账号和密码");
      return;
    }

    setIsSubmitting(true);
    setError("");
    try {
      await loginAdmin(username.trim(), password);
      const destination =
        (location.state as { from?: string } | null)?.from || "/admin";
      navigate(destination, { replace: true });
    } catch (loginError) {
      setError(
        loginError instanceof ApiError
          ? loginError.message
          : "登录失败，请稍后重试",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="admin-login-page">
      <div className="admin-login-glow admin-login-glow-one" />
      <div className="admin-login-glow admin-login-glow-two" />

      <button
        type="button"
        className="admin-login-back"
        onClick={() => navigate("/")}
      >
        <ArrowLeft size={17} />
        返回学生端
      </button>

      <section className="admin-login-card">
        <div className="admin-login-brand">
          <Brand />
          <span><ShieldCheck size={15} /> 管理员安全入口</span>
        </div>

        <div className="admin-login-heading">
          <span><LockKeyhole size={21} /></span>
          <div>
            <h1>登录管理后台</h1>
            <p>知识库、采集任务与质量评测仅对授权管理员开放</p>
          </div>
        </div>

        <form onSubmit={(event) => void submit(event)}>
          <label>
            <span>管理员账号</span>
            <div>
              <UserRound size={18} />
              <input
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="请输入管理员账号"
              />
            </div>
          </label>

          <label>
            <span>密码</span>
            <div>
              <KeyRound size={18} />
              <input
                autoComplete="current-password"
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="请输入管理员密码"
              />
              <button
                type="button"
                aria-label={showPassword ? "隐藏密码" : "显示密码"}
                onClick={() => setShowPassword((value) => !value)}
              >
                {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
              </button>
            </div>
          </label>

          {error && <p className="admin-login-error">{error}</p>}

          <button
            type="submit"
            className="admin-login-submit"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <LoaderCircle className="spin" size={18} />
            ) : (
              <ShieldCheck size={18} />
            )}
            {isSubmitting ? "正在验证..." : "安全登录"}
          </button>
        </form>

        <p className="admin-login-note">
          学生问答无需登录。本入口只用于受保护的后台操作。
        </p>
      </section>
    </main>
  );
}
