import type { AdminSession } from "@/lib/types";

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
    const message = response.status === 401 ? "Token validation failed." : "Unable to reach the API.";
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
        ? "Session is no longer valid."
        : `Request failed with status ${response.status}.`;
    throw new ApiError(response.status, message);
  }

  return (await response.json()) as T;
}
