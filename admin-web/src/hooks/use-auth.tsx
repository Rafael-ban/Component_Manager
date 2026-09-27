import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";

import { ApiError, openSession, deleteJson, normalizeBaseUrl } from "@/lib/api";
import { clearStoredSession, loadStoredSession, saveStoredSession } from "@/lib/storage";
import type { AccountIdentity, AdminSession } from "@/lib/types";

interface AuthContextValue {
  isReady: boolean;
  session: AdminSession | null;
  identity: AccountIdentity | null;
  login: (apiBaseUrl: string, token: string) => Promise<void>;
  logout: () => void;
  logoutError: string | null;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: PropsWithChildren) {
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const [isReady, setIsReady] = useState(false);
  const [session, setSession] = useState<AdminSession | null>(null);
  const [identity, setIdentity] = useState<AccountIdentity | null>(null);

  useEffect(() => {
    let active = true;
    const stored = loadStoredSession();
    if (!stored) { setIsReady(true); return; }
    void openSession(stored.apiBaseUrl).then((result) => {
      if (!active) return;
      setSession({ ...stored, csrfToken: result.csrf_token });
      setIdentity(result.identity);
    }).catch((error) => {
      if (active && error instanceof ApiError && error.status === 401) clearStoredSession();
    }).finally(() => { if (active) setIsReady(true); });
    return () => { active = false; };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      isReady,
      session,
      identity,
      logoutError,
      async login(apiBaseUrl: string, token: string) {
        const normalizedUrl = normalizeBaseUrl(apiBaseUrl);
        await openSession(normalizedUrl, token.trim());
        // Confirm the browser accepted the cookie before entering the console.
        const result = await openSession(normalizedUrl);
        const next = { apiBaseUrl: normalizedUrl, csrfToken: result.csrf_token };
        saveStoredSession(next);
        setSession(next);
        setIdentity(result.identity);
        setLogoutError(null);
      },
      logout() {
        if (!session) return;
        setLogoutError(null);
        void deleteJson(session, "/auth/session").then(() => {
          clearStoredSession(); setSession(null); setIdentity(null);
        }).catch((error) => {
          if (error instanceof ApiError && error.status === 401) {
            clearStoredSession(); setSession(null); setIdentity(null);
          } else {
            setLogoutError(error instanceof Error ? error.message : "Logout failed");
          }
        });
      },
    }),
    [identity, isReady, session, logoutError],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }

  return context;
}
