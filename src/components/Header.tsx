import { ArrowUpRight, Menu, ShieldCheck, Sparkles, X } from "lucide-react";
import { useState } from "react";
import { NavLink } from "react-router-dom";
import { Brand } from "./Brand";

const navItems = [
  { label: "首页", to: "/" },
  { label: "AI 问答", to: "/chat" },
  { label: "知识库", to: "/knowledge" },
];

export function Header({ admin = false }: { admin?: boolean }) {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="site-header">
      <div className="header-inner">
        <Brand />

        <nav className="desktop-nav" aria-label="主导航">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="header-actions">
          {admin ? (
            <div className="admin-label">
              <ShieldCheck size={17} />
              管理员
            </div>
          ) : (
            <NavLink
              className="student-entry"
              to="/chat"
              aria-label="开始提问"
            >
              <span className="avatar">
                <Sparkles size={17} />
              </span>
              <span>开始提问</span>
              <ArrowUpRight size={14} />
            </NavLink>
          )}
          <button
            type="button"
            className="mobile-menu-button"
            onClick={() => setMobileOpen((open) => !open)}
            aria-label={mobileOpen ? "关闭导航" : "打开导航"}
          >
            {mobileOpen ? <X size={21} /> : <Menu size={21} />}
          </button>
        </div>
      </div>

      {mobileOpen && (
        <nav className="mobile-nav" aria-label="移动端导航">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) => (isActive ? "active" : "")}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      )}
    </header>
  );
}
