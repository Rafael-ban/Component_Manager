import { useCallback, useEffect, useState } from "react";

import { ApiError, requestJson } from "@/lib/api";
import { useAuth } from "@/hooks/use-auth";

export function useAdminResource<T>(path: string) {
  const { logout, session } = useAuth();
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (!session) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const next = await requestJson<T>(session, path);
      setData(next);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout();
        return;
      }

      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to load admin data.",
      );
    } finally {
      setLoading(false);
    }
  }, [logout, path, session]);

  useEffect(() => {
    void load();
  }, [load]);

  return {
    data,
    error,
    loading,
    reload: load,
  };
}
