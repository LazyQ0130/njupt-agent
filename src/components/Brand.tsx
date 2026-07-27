import { Link } from "react-router-dom";
import njuptEmblem from "../assets/njupt-emblem.png";

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <Link to="/" className="brand" aria-label="南邮智答首页">
      <span
        className={`brand-mark${compact ? " compact" : ""}`}
        aria-hidden="true"
      >
        <img src={njuptEmblem} alt="" />
      </span>
      <span className="brand-copy">
        <strong>南邮智答</strong>
        {!compact && <small>NJUPT AI Assistant</small>}
      </span>
    </Link>
  );
}
