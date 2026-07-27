import {
  clearAdminToken,
  getAdminToken,
} from "./adminToken";

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export class ApiError extends Error {
  readonly code: number;
  readonly status: number;

  constructor(message: string, code: number, status: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

type LoadingListener = (loading: boolean, pendingRequests: number) => void;

export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080"
).replace(/\/+$/, "");

const ANONYMOUS_SESSION_KEY = "njupt.anonymousSessionId";
const ANONYMOUS_HEADER = "X-Anonymous-Session-Id";
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

let pendingRequests = 0;
let sessionRequest: Promise<string> | null = null;
const loadingListeners = new Set<LoadingListener>();

function getStoredAnonymousSession() {
  const value = window.localStorage.getItem(ANONYMOUS_SESSION_KEY);
  return value && UUID_PATTERN.test(value) ? value : null;
}

function clearAnonymousSession() {
  window.localStorage.removeItem(ANONYMOUS_SESSION_KEY);
}

export async function ensureAnonymousSession(force = false): Promise<string> {
  if (!force) {
    const stored = getStoredAnonymousSession();
    if (stored) return stored;
  } else {
    clearAnonymousSession();
  }

  if (sessionRequest) return sessionRequest;

  sessionRequest = fetch(`${API_BASE_URL}/api/session/anonymous`, {
    method: "POST",
    headers: { Accept: "application/json" },
  })
    .then(async (response) => {
      const payload = (await response.json()) as ApiResponse<{
        anonymousSessionId: string;
      }>;
      if (!response.ok || payload.code !== 200) {
        throw new ApiError(
          payload.message || "无法创建匿名会话，请稍后重试",
          payload.code,
          response.status,
        );
      }
      if (!UUID_PATTERN.test(payload.data.anonymousSessionId)) {
        throw new ApiError("服务端返回了无效的匿名会话", 40102, 401);
      }
      window.localStorage.setItem(
        ANONYMOUS_SESSION_KEY,
        payload.data.anonymousSessionId,
      );
      return payload.data.anonymousSessionId;
    })
    .catch((error: unknown) => {
      if (error instanceof ApiError) throw error;
      throw new ApiError("无法连接会话服务，请稍后重试", 0, 0);
    })
    .finally(() => {
      sessionRequest = null;
    });

  return sessionRequest;
}

export function subscribeToRequestLoading(listener: LoadingListener) {
  loadingListeners.add(listener);
  listener(pendingRequests > 0, pendingRequests);
  return () => loadingListeners.delete(listener);
}

export function isRequestLoading() {
  return pendingRequests > 0;
}

function updateLoading(delta: number) {
  pendingRequests = Math.max(0, pendingRequests + delta);
  loadingListeners.forEach((listener) =>
    listener(pendingRequests > 0, pendingRequests),
  );
}

async function buildRequest(path: string, options: RequestInit) {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");

  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  if (path.startsWith("/api/chat/")) {
    headers.set(ANONYMOUS_HEADER, await ensureAnonymousSession());
  }

  if (
    path.startsWith("/api/admin/") &&
    path !== "/api/admin/auth/login"
  ) {
    const token = getAdminToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }

  return { ...options, headers };
}

async function execute<T>(
  path: string,
  options: RequestInit,
  allowSessionRetry: boolean,
): Promise<T> {
  const response = await fetch(
    `${API_BASE_URL}${path}`,
    await buildRequest(path, options),
  );
  let payload: ApiResponse<T> | null = null;

  try {
    payload = (await response.json()) as ApiResponse<T>;
  } catch {
    throw new ApiError(
      response.ok ? "服务器返回了无法解析的数据" : "服务器暂时不可用",
      response.status,
      response.status,
    );
  }

  if (!response.ok || payload.code !== 200) {
    if (
      path.startsWith("/api/chat/") &&
      payload.code === 40102 &&
      allowSessionRetry
    ) {
      await ensureAnonymousSession(true);
      return execute<T>(path, options, false);
    }

    if (
      path.startsWith("/api/admin/") &&
      path !== "/api/admin/auth/login" &&
      (response.status === 401 || response.status === 403)
    ) {
      clearAdminToken();
      if (window.location.pathname.startsWith("/admin")) {
        window.location.replace("/admin/login");
      }
    }

    throw new ApiError(
      payload.message || "请求失败，请稍后重试",
      payload.code,
      response.status,
    );
  }

  return payload.data;
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  updateLoading(1);
  try {
    return await execute<T>(path, options, true);
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new ApiError("请求已取消", 499, 499);
    }
    throw new ApiError("无法连接服务器，请检查网络或后端服务", 0, 0);
  } finally {
    updateLoading(-1);
  }
}
