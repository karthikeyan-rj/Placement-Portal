// Lightweight in-memory GET cache with TTL + optional stale-while-revalidate.
// Keys are scoped by role + userId to prevent cross-account data leakage.
// Not used for POST/PUT/DELETE. Mutation endpoints must call invalidate().

interface CacheEntry<T> {
  data: T;
  expiry: number;
}

const CACHE = new Map<string, CacheEntry<unknown>>();

const DEFAULT_TTL_MS = 30_000;

export interface CacheGetOptions {
  ttl?: number;
}

function cacheKey(url: string, params: Record<string, unknown>): string {
  let query = '';
  try {
    query = Object.keys(params)
      .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
      .sort()
      .map((k) => `${k}=${encodeURIComponent(String(params[k]))}`)
      .join('&');
  } catch {
    query = '';
  }
  return `${url}${query ? `?${query}` : ''}`;
}

// The currently authenticated principal, used to scope cache keys so one
// user's cached data is never served to another user or role.
function scopedKey(url: string, params: Record<string, unknown>): string {
  const rawKey = cacheKey(url, params);
  let role = '';
  let id = '';
  try {
    const raw = localStorage.getItem('user');
    if (raw) {
      const user = JSON.parse(raw);
      role = user?.role ?? '';
      id = user?.id != null ? String(user.id) : '';
    }
  } catch {
    /* ignore */
  }
  // Also include any explicit auth-independent scope passed via params.
  const extra = params?.__scope ? String(params.__scope) : '';
  return `${role}:${id}:${extra}:${rawKey}`;
}

export function cacheGet<T>(
  url: string,
  params: Record<string, unknown> = {},
  _options: CacheGetOptions = {}
): T | undefined {
  const key = scopedKey(url, params);
  const entry = CACHE.get(key) as CacheEntry<T> | undefined;
  if (!entry) return undefined;
  if (Date.now() > entry.expiry) {
    CACHE.delete(key);
    return undefined;
  }
  return entry.data;
}

export function cacheHas(url: string, params: Record<string, unknown> = {}): boolean {
  return cacheGet(url, params) !== undefined;
}

export function cacheSet<T>(
  url: string,
  params: Record<string, unknown> = {},
  data: T,
  ttl: number = DEFAULT_TTL_MS
): void {
  CACHE.set(scopedKey(url, params), { data, expiry: Date.now() + ttl });
  // Opportunistic pruning of expired entries to bound memory growth.
  if (CACHE.size > 200) {
    const now = Date.now();
    for (const [k, v] of CACHE.entries()) {
      if (now > v.expiry) CACHE.delete(k);
    }
  }
}

// Invalidate cache entries whose key starts with any of the given path prefixes
// (e.g. invalidate('/departments') or invalidate('/placement-drives')).
export function invalidate(...pathPrefixes: string[]): void {
  if (pathPrefixes.length === 0) return;
  for (const key of Array.from(CACHE.keys())) {
    if (pathPrefixes.some((p) => key.includes(p))) CACHE.delete(key);
  }
}

export function cacheClear(): void {
  CACHE.clear();
}
