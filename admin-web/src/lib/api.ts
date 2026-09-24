import type { AdminSession, DeploymentConfiguration } from "@/lib/types";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export function normalizeBaseUrl(value: string) {
  return value.trim().replace(/\/+$/, "");
}

export async function pingAuth(apiBaseUrl: string, token: string) {
  const response = await fetch(`${normalizeBaseUrl(apiBaseUrl)}/auth/ping`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token.trim()}`,
    },
  });

  if (!response.ok) {
    const message = response.status === 401 ? "令牌验证失败。" : "无法连接 API。";
    throw new ApiError(response.status, message);
  }
}

export async function requestJson<T>(session: AdminSession, path: string): Promise<T> {
  const response = await fetch(`${normalizeBaseUrl(session.apiBaseUrl)}${path}`, {
    headers: {
      Authorization: `Bearer ${session.token}`,
    },
  });

  if (!response.ok) {
    const message =
      response.status === 401
        ? "登录会话已失效。"
        : `请求失败，状态码 ${response.status}。`;
    throw new ApiError(response.status, message);
  }

  return (await response.json()) as T;
}

export async function postJson<T>(session: AdminSession, path: string, body: unknown): Promise<T> {
  return mutateJson<T>(session, path, "POST", body);
}

export async function saveDeploymentConfiguration(
  session: AdminSession,
  body: { api_token: string; admin_web_origins: string[]; admin_web_url: string; web_inventory_enabled: boolean },
): Promise<DeploymentConfiguration> {
  let response: Response;
  try {
    response = await fetch(`${normalizeBaseUrl(session.apiBaseUrl)}/setup/config`, {
      method: "POST",
      headers: { Authorization: `Bearer ${session.token}`, "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    throw new ApiError(0, "无法确认配置是否已保存。请重新登录并读取服务端配置后再决定是否重试。");
  }
  const result = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = typeof result.detail === "string" ? result.detail : `请求失败，状态码 ${response.status}。`;
    throw new ApiError(response.status, detail);
  }
  return result as DeploymentConfiguration;
}

export async function putJson<T>(session: AdminSession, path: string, body: unknown): Promise<T> {
  return mutateJson<T>(session, path, "PUT", body);
}

async function mutateJson<T>(session: AdminSession, path: string, method: "POST" | "PUT", body: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${normalizeBaseUrl(session.apiBaseUrl)}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${session.token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    throw new ApiError(0, "网络请求未能确认结果。请保留当前表单并重试，或刷新库存核对最终状态。");
  }
  if (!response.ok) {
    const messages: Record<number, string> = {
      401: "登录会话已失效。",
      403: "Web 库存操作尚未启用。",
      404: "目标已不存在，请刷新后重试。",
      409: "数据已被其他设备更新，请刷新后重新提交。",
      422: "提交内容不完整或格式不正确，请检查表单。",
    };
    throw new ApiError(
      response.status,
      messages[response.status] ?? `请求失败，状态码 ${response.status}。`,
    );
  }
  return (await response.json()) as T;
}
