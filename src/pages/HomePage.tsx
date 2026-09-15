import {
  ArrowRight,
  BookOpenText,
  Bot,
  BriefcaseBusiness,
  ChevronRight,
  GraduationCap,
  Library,
  School,
  Sparkles,
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { Header } from "../components/Header";
import { QueryComposer } from "../components/QueryComposer";
import { SiteFooter } from "../components/SiteFooter";
import { homeCategories } from "../data";

const categoryIcons = [GraduationCap, BookOpenText, School, BriefcaseBusiness];

export function HomePage() {
  const navigate = useNavigate();

  return (
    <div className="app-shell home-shell">
      <Header />
      <main>
        <section className="hero">
          <div className="hero-glow hero-glow-left" />
          <div className="hero-glow hero-glow-right" />
          <div className="hero-content">
            <div className="eyebrow">
              <span><Sparkles size={14} /></span>
              南京邮电大学专属校园助手
            </div>
            <h1>你的南邮校园智能助手</h1>
            <p>入学、学习、生活，一个问题，让 AI 帮你解决</p>
            <QueryComposer />
            <div className="hero-trust">
              <span><Bot size={16} /> 基于校内知识库回答</span>
              <span>回答来源可追溯</span>
              <span>资料持续更新</span>
            </div>
          </div>
        </section>

        <section className="question-section section-wrap" aria-labelledby="hot-title">
          <div className="section-heading">
            <div>
              <span className="section-kicker">从这里开始</span>
              <h2 id="hot-title">热门校园问题</h2>
            </div>
            <Link to="/knowledge" className="text-link">
              浏览知识库 <ArrowRight size={16} />
            </Link>
          </div>

          <div className="category-grid">
            {homeCategories.map((category, index) => {
              const Icon = categoryIcons[index];
              return (
                <article className={`category-card accent-${category.accent}`} key={category.title}>
                  <div className="category-top">
                    <span className="category-icon"><Icon size={23} /></span>
                    <ChevronRight size={18} />
                  </div>
                  <h3>{category.title}</h3>
                  <p>{category.description}</p>
                  <div className="category-links">
                    {category.items.map((item) => (
                      <button
                        key={item}
                        type="button"
                        onClick={() => navigate("/chat", { state: { query: item } })}
                      >
                        {item}
                      </button>
                    ))}
                  </div>
                </article>
              );
            })}
          </div>
        </section>

        <section className="knowledge-proof">
          <div className="section-wrap proof-inner">
            <div className="proof-heading">
              <span className="proof-icon"><Library size={23} /></span>
              <div>
                <span className="section-kicker">可靠答案的背后</span>
                <h2>我已经学习了你的南邮知识库</h2>
              </div>
            </div>
            <p className="proof-copy">
            汇集校内权威知识，让每一次回答更准确、更有依据。
            </p>
          </div>
          <div className="campus-illustration">
            <img
              src="/assets/njupt-campus-lineart.png"
              alt="蓝色线稿绘制的大学校园建筑与校门"
            />
          </div>
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}
