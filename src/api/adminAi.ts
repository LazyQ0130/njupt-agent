import { request } from "./request";

export type UsagePeriod = "today" | "7d" | "30d" | "all";

export interface AiProviderConfig {
  configured: boolean;
  enabled: boolean;
  source: "DATABASE" | "ENVIRONMENT" | "NONE";
  keyMask: string;
  model: string;
  verifiedAt: string | null;
  storageAvailable: boolean;
}

export interface AiBalanceItem {
  currency: string;
  totalBalance: string;
  grantedBalance: string;
  toppedUpBalance: string;
}

export interface AiBalance {
  available: boolean;
  syncStatus: "SUCCESS" | "STALE" | "ERROR" | "NOT_CONFIGURED";
  syncedAt: string | null;
  message: string;
  balances: AiBalanceItem[];
}

export interface AiUsageSummary {
  period: UsagePeriod;
  requestCount: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  cacheHitTokens: number;
  cacheMissTokens: number;
}

export interface AiOverview {
  config: AiProviderConfig;
  balance: AiBalance;
  usage: AiUsageSummary;
}

export interface AiProviderSaveResult {
  config: AiProviderConfig;
  balance: AiBalance;
}

export function getAiConfig() {
  return request<AiProviderConfig>("/api/admin/ai/config");
}

export function saveAiConfig(apiKey: string, model: string) {
  return request<AiProviderSaveResult>("/api/admin/ai/config", {
    method: "PUT",
    body: JSON.stringify({ apiKey, model }),
  });
}

export function setAiConfigStatus(enabled: boolean) {
  return request<AiProviderConfig>("/api/admin/ai/config/status", {
    method: "PATCH",
    body: JSON.stringify({ enabled }),
  });
}

export function clearAiConfig() {
  return request<AiProviderConfig>("/api/admin/ai/config", {
    method: "DELETE",
  });
}

export function getAiOverview(
  period: UsagePeriod,
  refreshBalance = false,
) {
  const params = new URLSearchParams({
    period,
    refreshBalance: String(refreshBalance),
  });
  return request<AiOverview>(`/api/admin/ai/overview?${params}`);
}
