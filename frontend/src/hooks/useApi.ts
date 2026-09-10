import { useState, useEffect, useCallback, useMemo } from 'react';
import api, { getErrorMessage } from '../api/axios';
import { cacheGet, cacheSet } from '../api/cache';
import type { ApiResponse, PaginatedResponse } from '../types';

interface UseApiOptions {
  params?: Record<string, unknown>;
  immediate?: boolean;
  /** Cache the GET result in memory for this many ms (role/user-scoped). Default: no caching. */
  cacheTtl?: number;
}

interface UseApiReturn<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  page: number;
  totalPages: number;
  totalElements: number;
  setPage: (page: number) => void;
  refresh: () => void;
}

export function useApi<T>(
  url: string,
  options: UseApiOptions = {}
): UseApiReturn<T> {
  const { params = {}, immediate = true, cacheTtl } = options;
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(immediate);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  const stableParams = useMemo(
    () => JSON.stringify(params),
    [params]
  );

  useEffect(() => {
    let cancelled = false;

    const fetchData = async () => {
      setLoading(true);
      setError(null);

      const requestParams = { ...params, page, size: params.size ?? 20 };

      // Serve from cache without a network round trip when available.
      if (cacheTtl && !refreshKey && page === 0) {
        const cached = cacheGet<T | PaginatedResponse<T>>(url, requestParams, { ttl: cacheTtl });
        if (cached !== undefined) {
          const payload = (cached as unknown) as T | PaginatedResponse<T>;
          if (Array.isArray(payload)) {
            setData(payload as T);
            setTotalPages(1);
            setTotalElements((payload as unknown[]).length);
          } else if (payload && typeof payload === 'object' && 'content' in payload) {
            const paginated = payload as PaginatedResponse<unknown>;
            setData(paginated.content as T);
            setTotalPages(paginated.totalPages);
            setTotalElements(paginated.totalElements);
          } else {
            setData(payload as T);
            setTotalPages(1);
            setTotalElements(1);
          }
          setLoading(false);
          return;
        }
      }

      try {
        const response = await api.get<ApiResponse<T | PaginatedResponse<T>>>(url, {
          params: requestParams,
        });

        if (cancelled) return;

        const wrapper = response.data;
        const payload = wrapper.data;

        if (Array.isArray(payload)) {
          setData(payload as T);
          setTotalPages(1);
          setTotalElements(payload.length);
        } else if (payload && typeof payload === 'object' && 'content' in payload) {
          const paginated = payload as PaginatedResponse<unknown>;
          setData(paginated.content as T);
          setTotalPages(paginated.totalPages);
          setTotalElements(paginated.totalElements);
        } else {
          setData(payload as T);
          setTotalPages(1);
          setTotalElements(1);
        }

        if (cacheTtl && page === 0) {
          cacheSet<T | PaginatedResponse<T>>(url, requestParams, payload, cacheTtl);
        }
      } catch (err) {
        if (!cancelled) {
          setError(getErrorMessage(err));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    fetchData();

    return () => {
      cancelled = true;
    };
  }, [url, page, refreshKey, stableParams]);

  return { data, loading, error, page, totalPages, totalElements, setPage, refresh };
}

// ─── usePaginatedData ────────────────────────────────────────────────────────
// Convenience wrapper for paginated list pages.

interface UsePaginatedDataOptions {
  url: string;
  params?: Record<string, unknown>;
  cacheTtl?: number;
}

interface UsePaginatedDataReturn<T> {
  data: T[];
  loading: boolean;
  error: string | null;
  page: number;
  totalPages: number;
  totalElements: number;
  setPage: (page: number) => void;
  refresh: () => void;
}

export function usePaginatedData<T>(
  options: UsePaginatedDataOptions
): UsePaginatedDataReturn<T> {
  const { url, params = {}, cacheTtl } = options;
  const result = useApi<T[]>(url, { params, cacheTtl });

  return {
    data: result.data ?? [],
    loading: result.loading,
    error: result.error,
    page: result.page,
    totalPages: result.totalPages,
    totalElements: result.totalElements,
    setPage: result.setPage,
    refresh: result.refresh,
  };
}
