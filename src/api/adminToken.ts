const ADMIN_TOKEN_KEY = "njupt.adminAccessToken";

export function getAdminToken() {
  return window.sessionStorage.getItem(ADMIN_TOKEN_KEY);
}

export function setAdminToken(token: string) {
  window.sessionStorage.setItem(ADMIN_TOKEN_KEY, token);
}

export function clearAdminToken() {
  window.sessionStorage.removeItem(ADMIN_TOKEN_KEY);
}

export function hasValidAdminToken() {
  const token = getAdminToken();
  if (!token) return false;

  try {
    const [, payload] = token.split(".");
    if (!payload) return false;
    const normalized = payload
      .replace(/-/g, "+")
      .replace(/_/g, "/")
      .padEnd(Math.ceil(payload.length / 4) * 4, "=");
    const claims = JSON.parse(window.atob(normalized)) as {
      exp?: number;
      role?: string;
    };
    return (
      claims.role === "ADMIN" &&
      typeof claims.exp === "number" &&
      claims.exp * 1000 > Date.now() + 5_000
    );
  } catch {
    return false;
  }
}
