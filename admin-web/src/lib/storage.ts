import type { AdminSession } from "./types";
const SESSION_KEY = "component-vault-admin-session";
// Only the API address persists. HttpOnly cookies own authentication.
export function loadStoredSession(): Pick<AdminSession, "apiBaseUrl"> | null {
  try {
    const raw = window.localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { apiBaseUrl?: unknown };
    if (typeof parsed.apiBaseUrl !== "string" || !parsed.apiBaseUrl) return null;
    const stored = { apiBaseUrl: parsed.apiBaseUrl };
    // Also remove raw keys retained by previous releases.
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(stored));
    return stored;
  } catch { return null; }
}
export function saveStoredSession(session: Pick<AdminSession, "apiBaseUrl">) {
  try { window.localStorage.setItem(SESSION_KEY, JSON.stringify({ apiBaseUrl: session.apiBaseUrl })); } catch { /* Session still works for this tab. */ }
}
export function clearStoredSession() { try { window.localStorage.removeItem(SESSION_KEY); } catch { /* Cookie revocation remains authoritative. */ } }
