import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";

import { ApiError, fetchIdentity, normalizeBaseUrl } from "@/lib/api";
import { clearStoredSession, loadStoredSession, saveStoredSession } from "@/lib/storage";
import type { AccountIdentity, AdminSession } from "@/lib/types";

interface AuthContextValue {
  isReady: boolean;
  session: AdminSession | null;
  identity: AccountIdentity | null;
  login: (apiBaseUrl: string, token: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: PropsWithChildren) {
  const [isReady, setIsReady] = useState(false);
  const [session, setSession] = useState<AdminSession | null>(null);
  const [identity, setIdentity] = useState<AccountIdentity | null>(null);

  useEffect(() => {
    let active = true;
    const stored = loadStoredSession();
    if (!stored) { setIsReady(true); return; }
    void fetchIdentity(stored).then((nextIdentity) => {
      if (!active) return;
      setSession(stored);
      setIdentity(nextIdentity);
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
      async login(apiBaseUrl: string, token: string) {
        const normalizedSession = {
          apiBaseUrl: normalizeBaseUrl(apiBaseUrl),
          token: token.trim(),
        };
        const nextIdentity = await fetchIdentity(normalizedSession);
        saveStoredSession(normalizedSession);
        setSession(normalizedSession);
        setIdentity(nextIdentity);
      },
      logout() {
        clearStoredSession();
        setSession(null);
        setIdentity(null);
      },
    }),
    [identity, isReady, session],
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
