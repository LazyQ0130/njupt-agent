import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import {
  clearAdminToken,
  hasValidAdminToken,
} from "../api/adminToken";

export function AdminGuard({ children }: { children: ReactNode }) {
  const location = useLocation();

  if (!hasValidAdminToken()) {
    clearAdminToken();
    return (
      <Navigate
        to="/admin/login"
        replace
        state={{ from: `${location.pathname}${location.search}` }}
      />
    );
  }

  return children;
}
