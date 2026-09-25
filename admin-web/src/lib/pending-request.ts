export interface PendingRequest {
  requestId: string;
  payload: string;
}

import { createRequestId } from "./request-id.ts";

export function stablePayload(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stablePayload).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => `${JSON.stringify(key)}:${stablePayload(item)}`)
      .join(",")}}`;
  }
  return JSON.stringify(value) ?? "null";
}

export function requestForPayload(
  previous: PendingRequest | null,
  payload: unknown,
  createId: () => string = createRequestId,
): PendingRequest {
  const serialized = stablePayload(payload);
  return previous?.payload === serialized
    ? previous
    : { requestId: createId(), payload: serialized };
}

export function loadPendingRequest(key: string): PendingRequest | null {
  try {
    const value = sessionStorage.getItem(key);
    if (!value) return null;
    const parsed = JSON.parse(value) as PendingRequest;
    return parsed.requestId && parsed.payload ? parsed : null;
  } catch {
    return null;
  }
}

export function savePendingRequest(key: string, value: PendingRequest): boolean {
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

export function clearPendingRequest(key: string) {
  try {
    sessionStorage.removeItem(key);
  } catch {
    // Storage is an optional recovery aid; successful API operations must still complete.
  }
}

export function pendingStorageKey(apiBaseUrl: string, action: string, accountId = "admin"): string {
  return `component-vault:pending:${encodeURIComponent(apiBaseUrl)}:${encodeURIComponent(accountId)}:${action}`;
}

export function shouldRetainPending(status: number): boolean {
  return status === 0 || status >= 500;
}
