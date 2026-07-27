import { request } from "./request";
import { setAdminToken } from "./adminToken";

export interface AdminToken {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  adminName: string;
}

export async function loginAdmin(username: string, password: string) {
  const token = await request<AdminToken>("/api/admin/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  setAdminToken(token.accessToken);
  return token;
}
