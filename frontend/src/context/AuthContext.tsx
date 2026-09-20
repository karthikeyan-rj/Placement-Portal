import { createContext, useContext, useState, useEffect, useRef, useCallback, type ReactNode } from 'react';
import type { User, LoginResponse, MeResponse } from '../types';
import api from '../api/axios';
import { cacheClear } from '../api/cache';

interface AuthContextType {
  user: User | null;
  token: string | null;
  refresh: () => Promise<boolean>;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function toUser(m: {
  id: number;
  name: string;
  email: string;
  role: string;
  departmentId: number | null;
  departmentName: string | null;
  active: boolean;
}): User {
  return {
    id: m.id,
    name: m.name,
    email: m.email,
    role: m.role as User['role'],
    departmentId: m.departmentId,
    departmentName: m.departmentName,
    active: m.active,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const userRef = useRef<User | null>(null);

  const commitUser = useCallback((next: User | null) => {
    userRef.current = next;
    setUser(next);
  }, []);

  // Refetch the authoritative profile from the server. Any role/department
  // change clears the role-scoped in-memory cache so stale data is not served.
  const refresh = useCallback(async (): Promise<boolean> => {
    const storedToken = localStorage.getItem('token');
    if (!storedToken) return false;
    try {
      const res = await api.get<MeResponse>('/auth/me');
      const nextUser = toUser(res.data);
      const previous = userRef.current;
      const changed =
        !previous ||
        previous.id !== nextUser.id ||
        previous.role !== nextUser.role ||
        previous.departmentId !== nextUser.departmentId;
      localStorage.setItem('user', JSON.stringify(nextUser));
      commitUser(nextUser);
      if (changed) cacheClear();
      return true;
    } catch {
      return false;
    }
  }, [commitUser]);

  useEffect(() => {
    const storedToken = localStorage.getItem('token');
    const storedUser = localStorage.getItem('user');
    if (storedToken && storedUser) {
      try {
        setToken(storedToken);
        commitUser(toUser(JSON.parse(storedUser)));
      } catch {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
      }
    }
    setLoading(false);
    if (storedToken) refresh();
  }, [commitUser, refresh]);

  // Re-check the server when the window regains focus, but only if a
  // meaningful amount of time has passed (no constant polling).
  useEffect(() => {
    let lastCheck = Date.now();
    const STALE_MS = 30_000;
    const reconfirm = () => {
      const now = Date.now();
      if (now - lastCheck <= STALE_MS) return;
      lastCheck = now;
      refresh();
    };
    const onFocus = () => reconfirm();
    const onShow = (e: PageTransitionEvent) => {
      if (e.persisted) reconfirm();
    };
    window.addEventListener('focus', onFocus);
    window.addEventListener('pageshow', onShow);
    return () => {
      window.removeEventListener('focus', onFocus);
      window.removeEventListener('pageshow', onShow);
    };
  }, [refresh]);

  const login = async (email: string, password: string) => {
    const response = await api.post<LoginResponse>('/auth/login', { email, password });
    const data = response.data;

    const userData = toUser({
      id: 0,
      name: data.name,
      email: data.email,
      role: data.role,
      departmentId: data.departmentId,
      departmentName: data.departmentName,
      active: true,
    });

    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(userData));
    setToken(data.token);
    commitUser(userData);
    cacheClear();
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    commitUser(null);
    cacheClear();
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        refresh,
        login,
        logout,
        isAuthenticated: !!token,
        loading,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}