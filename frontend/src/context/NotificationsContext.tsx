import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { messageApi } from '../api/api';
import { streamMessageEvents } from '../api/messageEvents';
import { useAuth } from './AuthContext';

export interface MessageEventInfo {
  at: number;
  messageId: number | null;
}

interface NotificationsContextType {
  unreadCount: number;
  lastEvent: MessageEventInfo | null;
  markRead: (messageId: number) => void;
  refreshUnread: () => Promise<void>;
}

const NotificationsContext = createContext<NotificationsContextType | undefined>(undefined);

const CHANNEL_NAME = 'placement-notifications';
const FOCUS_STALE_MS = 30_000;

export function NotificationsProvider({ children }: { children: ReactNode }) {
  const { user, token, isAuthenticated } = useAuth();
  const enabled = isAuthenticated && !!token && !!user;

  const [unreadCount, setUnreadCount] = useState(0);
  const [lastEvent, setLastEvent] = useState<MessageEventInfo | null>(null);
  const channelRef = useRef<BroadcastChannel | null>(null);

  const refreshUnread = useCallback(async () => {
    if (!isAuthenticated || !token) return;
    try {
      const res = await messageApi.getUnreadCount();
      const next = res.data?.data?.unreadCount;
      if (typeof next === 'number') setUnreadCount(next);
    } catch {
      // Ignore transient failures; the next resync reconciles the badge.
    }
  }, [isAuthenticated, token]);

  // Connect (or reset) whenever the authenticated identity changes so that a
  // logout or an account switch never leaks another user's unread state.
  useEffect(() => {
    if (!enabled) {
      setUnreadCount(0);
      setLastEvent(null);
      return;
    }

    let cancelled = false;
    let reconnectTimer: number | undefined;
    let attempt = 0;
    const controller = new AbortController();

    const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(CHANNEL_NAME) : null;
    channelRef.current = channel;
    if (channel) {
      channel.onmessage = (event: MessageEvent) => {
        const type = (event.data as { type?: string } | null)?.type;
        if (type === 'UNREAD_REFRESH') void refreshUnread();
      };
    }

    void refreshUnread();

    const connect = () => {
      if (cancelled) return;
      streamMessageEvents({
        signal: controller.signal,
        onOpen: () => {
          attempt = 0;
          void refreshUnread();
        },
        onEvent: (name, data) => {
          if (name !== 'NEW_MESSAGE') return;
          // Never blindly increment: a replayed/duplicated event (or a reconnect
          // that redelivers) would inflate the badge. Guarantee the dot shows
          // immediately, then reconcile against the authoritative Mongo count.
          setUnreadCount((count) => (count > 0 ? count : 1));
          const payload = data as { messageId?: number } | null;
          setLastEvent({
            at: Date.now(),
            messageId: typeof payload?.messageId === 'number' ? payload.messageId : null,
          });
          void refreshUnread();
          channel?.postMessage({ type: 'UNREAD_REFRESH' });
        },
      })
        .catch(() => {
          // Stream closed or errored; reconnect below with backoff.
        })
        .finally(() => {
          if (cancelled) return;
          const delay = Math.min(30_000, 1_000 * 2 ** attempt) + Math.random() * 500;
          attempt = Math.min(attempt + 1, 5);
          reconnectTimer = window.setTimeout(connect, delay);
        });
    };

    connect();

    return () => {
      cancelled = true;
      controller.abort();
      if (reconnectTimer !== undefined) window.clearTimeout(reconnectTimer);
      if (channel) {
        channel.onmessage = null;
        channel.close();
      }
      if (channelRef.current === channel) channelRef.current = null;
    };
  }, [enabled, refreshUnread, user?.id]);

  // Re-sync the authoritative count when the tab regains focus after a while.
  useEffect(() => {
    if (!enabled) return;
    let lastCheck = Date.now();
    const onFocus = () => {
      const now = Date.now();
      if (now - lastCheck <= FOCUS_STALE_MS) return;
      lastCheck = now;
      void refreshUnread();
    };
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [enabled, refreshUnread]);

  const markRead = useCallback(
    (messageId: number) => {
      setUnreadCount((count) => Math.max(0, count - 1));
      messageApi
        .markAsRead(messageId)
        .catch(() => {
          // The authoritative resync below corrects an optimistic decrement.
        })
        .finally(() => {
          channelRef.current?.postMessage({ type: 'UNREAD_REFRESH' });
          void refreshUnread();
        });
    },
    [refreshUnread]
  );

  return (
    <NotificationsContext.Provider value={{ unreadCount, lastEvent, markRead, refreshUnread }}>
      {children}
    </NotificationsContext.Provider>
  );
}

export function useNotifications() {
  const context = useContext(NotificationsContext);
  if (!context) {
    throw new Error('useNotifications must be used within a NotificationsProvider');
  }
  return context;
}
