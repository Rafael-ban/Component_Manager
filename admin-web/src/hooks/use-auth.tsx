import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";

import { normalizeBaseUrl, pingAuth } from "@/lib/api";
import { clearStoredSession, loadStoredSession, saveStoredSession } from "@/lib/storage";
import type { AdminSession } from "@/lib/types";

interface AuthContextValue {
  isReady: boolean;
  session: AdminSession | null;
  login: (apiBaseUrl: string, token: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: PropsWithChildren) {
  const [isReady, setIsReady] = useState(false);
  const [session, setSession] = useState<AdminSession | null>(null);

  useEffect(() => {
    setSession(loadStoredSession());
    setIsReady(true);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      isReady,
      session,
      async login(apiBaseUrl: string, token: string) {
        const normalizedSession = {
          apiBaseUrl: normalizeBaseUrl(apiBaseUrl),
          token: token.trim(),
        };
        await pingAuth(normalizedSession.apiBaseUrl, normalizedSession.token);
        saveStoredSession(normalizedSession);
        setSession(normalizedSession);
      },
      logout() {
        clearStoredSession();
        setSession(null);
      },
    }),
    [isReady, session],
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
