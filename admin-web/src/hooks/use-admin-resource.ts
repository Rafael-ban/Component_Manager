import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { ApiError, requestJson } from "@/lib/api";
import { useAuth } from "@/hooks/use-auth";

export function useAdminResource<T>(path: string | null) {
  const { logout, session } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const requestGeneration = useRef(0);

  const load = useCallback(async () => {
    const generation = ++requestGeneration.current;
    if (!session || !path) {
      setData(null);
      setError(null);
      setLoading(false);
      return;
    }

    setData(null);
    setLoading(true);
    setError(null);
    try {
      const next = await requestJson<T>(session, path);
      if (generation === requestGeneration.current) {
        setData(next);
      }
    } catch (requestError) {
      if (generation !== requestGeneration.current) {
        return;
      }
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout();
        navigate("/login", {
          replace: true,
          state: {
            from: `${location.pathname}${location.search}`,
            reason: "登录已失效，请重新验证 API 令牌。",
          },
        });
        return;
      }

      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to load admin data.",
      );
    } finally {
      if (generation === requestGeneration.current) {
        setLoading(false);
      }
    }
  }, [location.pathname, location.search, logout, navigate, path, session]);

  useEffect(() => {
    void load();
    return () => {
      requestGeneration.current += 1;
    };
  }, [load]);

  return {
    data,
    error,
    loading,
    reload: load,
  };
}
