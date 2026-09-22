import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  messageApi,
  departmentApi,
  reportApi,
  clarificationApi,
  userApi,
} from '../../api/api';
import type { ClarificationThread, Department, Message, User } from '../../types';
import { useAuth } from '../../context/AuthContext';
import { useNotifications } from '../../context/NotificationsContext';
import {
  Avatar,
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  PageContainer,
  PageHeader,
  Pagination,
  SearchInput,
  Select,
  Skeleton,
  Textarea,
  notify,
} from '../../components/ui';
import { usePaginatedData } from '../../hooks/useApi';
import {
  AlertCircle,
  ArrowLeft,
  Check,
  Download,
  Inbox,
  MailX,
  MessageCircleQuestion,
  MessageSquare,
  Reply,
  Search,
  Send as SendIcon,
  ThumbsDown,
  ThumbsUp,
  X,
} from 'lucide-react';

type Tab = 'inbox' | 'sent' | 'incoming';
type Reaction = 'UPVOTE' | 'DOWNVOTE';
type InboxFilter = 'all' | 'unread' | 'ack' | 'needs-clarification';

interface ComposeAudience {
  value: string;
  label: string;
  hint: string;
  usesDepartment?: boolean;
  specific?: boolean;
}

const MESSAGE_TYPE_OPTIONS = [
  { label: 'Broadcast', value: 'BROADCAST' },
  { label: 'Department', value: 'DEPARTMENT' },
  { label: 'Direct', value: 'DIRECT' },
];

const SPECIFIC_AUDIENCE: ComposeAudience = {
  value: 'SPECIFIC',
  label: 'Specific recipients',
  hint: 'Search and choose the exact people to message.',
  specific: true,
};

const AUDIENCES: Record<User['role'], ComposeAudience[]> = {
  PO: [
    { value: 'ALL_STUDENTS', label: 'All Students', hint: 'Every active student across all departments.' },
    { value: 'ALL_PRS', label: 'All PR Representatives', hint: 'Every active placement representative.' },
    { value: 'ALL_PCS', label: 'All PC Coordinators', hint: 'Every active placement coordinator.' },
    { value: 'ALL_POS', label: 'All Placement Officers', hint: 'Every active placement officer.' },
    { value: 'DEPT_ALL', label: 'Everyone in a Department', hint: 'Students, PRs and PCs within one department.', usesDepartment: true },
    { value: 'DEPT_STUDENTS', label: 'Students in a Department', hint: 'Students only, within one department.', usesDepartment: true },
    { value: 'DEPT_PRS', label: 'PRs in a Department', hint: 'Placement representatives only, within one department.', usesDepartment: true },
    { value: 'DEPT_PCS', label: 'PCs in a Department', hint: 'Placement coordinators only, within one department.', usesDepartment: true },
  ],
  PC: [
    { value: 'STUDENTS', label: 'Students (my department)', hint: 'Active students in your department.' },
    { value: 'PRS', label: 'PR Representatives (my department)', hint: 'Active placement representatives in your department.' },
    { value: 'PCS', label: 'PC Coordinators (my department)', hint: 'Active placement coordinators in your department.' },
  ],
  PR: [{ value: 'STUDENTS', label: 'Students (my department)', hint: 'Active students in your department.' }],
  STUDENT: [],
};

const ROLE_ALLOWLIST: Record<User['role'], User['role'][]> = {
  PO: ['STUDENT', 'PR', 'PC', 'PO'],
  PC: ['STUDENT', 'PR', 'PC'],
  PR: [],
  STUDENT: [],
};

const ROLE_LABELS: Record<string, string> = {
  PO: 'Placement Officer',
  PC: 'Placement Coordinator',
  PR: 'Placement Representative',
  STUDENT: 'Student',
};

const TYPE_LABELS: Record<string, string> = {
  BROADCAST: 'Broadcast',
  DEPARTMENT: 'Department',
  DIRECT: 'Direct',
};

interface ComposeForm {
  title: string;
  content: string;
  messageType: string;
  audience: string;
  departmentId: string;
}

const emptyCompose = (audiences: ComposeAudience[]): ComposeForm => ({
  title: '',
  content: '',
  messageType: 'DIRECT',
  audience: audiences[0]?.value ?? '',
  departmentId: '',
});

function fmtTimestamp(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getDate()} ${d.toLocaleString(undefined, { month: 'short' })} · ${d.toLocaleString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  })}`;
}

function fmtDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function statusLabel(status: string | undefined): string {
  return status === 'ANSWERED' ? 'Answered' : 'Awaiting reply';
}

function statusVariant(status: string | undefined): 'success' | 'warning' {
  return status === 'ANSWERED' ? 'success' : 'warning';
}

function previewText(content: string): string {
  const lines = content.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  return lines.length > 0 ? lines[0] : content;
}

function scopeLine(m: Message): string {
  const label = TYPE_LABELS[m.messageType ?? ''] ?? 'Message';
  const n = m.totalRecipients.toLocaleString();
  return `${label} · ${n} recipient${m.totalRecipients === 1 ? '' : 's'}`;
}

function ProgressBar({ label, value, total }: { label: string; value: number; total: number }) {
  const pct = total > 0 ? Math.min(100, Math.round((value / total) * 100)) : 0;
  return (
    <div>
      <div className="flex items-center justify-between mb-1.5">
        <span className="text-[13px] font-medium text-neutral-700">{label}</span>
        <span className="text-[13px] text-neutral-500 tabular-nums">
          {value.toLocaleString()} / {total.toLocaleString()}
        </span>
      </div>
      <div className="h-2 rounded-full bg-neutral-100 overflow-hidden">
        <div
          className="h-full rounded-full bg-primary-500 transition-all duration-300"
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

export default function MessagesPage() {
  const { user } = useAuth();
  const { markRead, lastEvent } = useNotifications();
  const role = (user?.role || 'PO') as User['role'];
  const audiences = useMemo(
    () => (role === 'PO' || role === 'PC' ? [...AUDIENCES[role], SPECIFIC_AUDIENCE] : AUDIENCES[role] ?? []),
    [role]
  );

  const [tab, setTab] = useState<Tab>('inbox');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<InboxFilter>('all');

  const [readLocally, setReadLocally] = useState<Set<number>>(() => new Set());
  const [reactOverrides, setReactOverrides] = useState<Record<number, Reaction | null>>({});
  const [countOverrides, setCountOverrides] = useState<Record<number, { up: number; down: number }>>({});
  const [reacting, setReacting] = useState(false);

  const [departments, setDepartments] = useState<Department[]>([]);
  const [exporting, setExporting] = useState(false);

  const [composeOpen, setComposeOpen] = useState(false);
  const [composeStep, setComposeStep] = useState<'compose' | 'confirm'>('compose');
  const [form, setForm] = useState<ComposeForm>(() => emptyCompose(AUDIENCES[role] ?? []));
  const [saving, setSaving] = useState(false);
  const [recipientQuery, setRecipientQuery] = useState('');
  const [recipientResults, setRecipientResults] = useState<User[]>([]);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [recipientSearching, setRecipientSearching] = useState(false);

  const [sentThreads, setSentThreads] = useState<ClarificationThread[]>([]);
  const [sentThreadTotal, setSentThreadTotal] = useState(0);
  const [sentClarPage, setSentClarPage] = useState(0);
  const [sentClarLoading, setSentClarLoading] = useState(false);
  const [sentClarError, setSentClarError] = useState('');

  const [recipientThread, setRecipientThread] = useState<ClarificationThread | null>(null);
  const [rcLoading, setRcLoading] = useState(false);
  const [rcError, setRcError] = useState('');
  const [rcAskOpen, setRcAskOpen] = useState(false);
  const [rcText, setRcText] = useState('');
  const [rcBusy, setRcBusy] = useState(false);

  const url = tab === 'inbox' ? '/messages/received' : tab === 'sent' ? '/messages/sent' : '/clarifications/incoming';

  const {
    data: listData,
    loading,
    error,
    page,
    totalPages,
    totalElements,
    setPage,
    refresh,
  } = usePaginatedData<Message>({ url });

  // Refresh the open inbox when a new message notification arrives so the
  // badge and list stay consistent without polling every page.
  useEffect(() => {
    if (tab === 'inbox' && lastEvent) refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastEvent?.at]);

  const listItems = Array.isArray(listData) ? listData : [];
  const messages =
    tab === 'incoming'
      ? []
      : (listItems as Message[]).filter((m) => m && typeof m === 'object' && 'totalRecipients' in m);
  const threadPage =
    tab === 'incoming'
      ? (listItems as unknown as ClarificationThread[]).filter((t) => t && typeof t === 'object' && 'threadId' in t)
      : [];

  useEffect(() => {
    if (role !== 'PO') return;
    let cancelled = false;
    departmentApi
      .getActive()
      .then((res) => {
        const d = res.data?.data;
        if (!cancelled) setDepartments(Array.isArray(d) ? d : []);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [role]);

  const selected = tab === 'incoming' ? null : (messages.find((m) => m.id === selectedId) ?? null);
  const activeAudience = audiences.find((a) => a.value === form.audience);

  const countOf = useCallback(
    (m: Message) => {
      const o = countOverrides[m.id];
      return { up: o ? o.up : m.upvoteCount, down: o ? o.down : m.downvoteCount };
    },
    [countOverrides]
  );

  const myReactionOf = useCallback(
    (m: Message): Reaction | null => {
      if (m.id in reactOverrides) return reactOverrides[m.id];
      return m.myReaction ?? null;
    },
    [reactOverrides]
  );

  const isUnread = useCallback(
    (m: Message) => tab === 'inbox' && !m.readByRecipient && !readLocally.has(m.id),
    [tab, readLocally]
  );

  const visibleMessages = useMemo(() => {
    const q = search.trim().toLowerCase();
    let items = messages;
    if (tab === 'inbox' && filter !== 'all') {
      items = items.filter((m) => {
        if (filter === 'unread') return isUnread(m);
        if (filter === 'ack') return myReactionOf(m) === 'UPVOTE';
        return myReactionOf(m) === 'DOWNVOTE';
      });
    }
    if (!q) return items;
    return items.filter(
      (m) =>
        m.title.toLowerCase().includes(q) ||
        m.content.toLowerCase().includes(q) ||
        m.senderName.toLowerCase().includes(q)
    );
  }, [messages, search, filter, tab, isUnread, myReactionOf]);

  const visibleThreads = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return threadPage;
    return threadPage.filter(
      (t) =>
        (t.requesterName ?? '').toLowerCase().includes(q) ||
        (t.messageTitle ?? '').toLowerCase().includes(q)
    );
  }, [threadPage, search]);

  const handleTabChange = (next: Tab) => {
    setTab(next);
    setPage(0);
    setSelectedId(null);
    setSearch('');
    setFilter('all');
  };

  const handleSelect = (m: Message) => {
    setSelectedId(m.id);
    if (tab === 'inbox' && !m.readByRecipient && !readLocally.has(m.id)) {
      setReadLocally((prev) => new Set(prev).add(m.id));
      markRead(m.id);
    }
  };

  const handleReact = async (m: Message, type: Reaction) => {
    if (reacting) return;
    const prior = myReactionOf(m);
    if (prior === type) return;
    setReacting(true);
    const base = { up: m.upvoteCount, down: m.downvoteCount };
    let up = base.up;
    let down = base.down;
    if (prior === 'UPVOTE') up -= 1;
    else if (prior === 'DOWNVOTE') down -= 1;
    if (type === 'UPVOTE') up += 1;
    else down += 1;

    setCountOverrides((prev) => ({ ...prev, [m.id]: { up, down } }));
    setReactOverrides((prev) => ({ ...prev, [m.id]: type }));
    try {
      await messageApi.react(m.id, type);
      notify.success(type === 'UPVOTE' ? 'Acknowledgement recorded' : 'Clarification noted');
    } catch {
      setReactOverrides((prev) => {
        const next = { ...prev };
        next[m.id] = prior;
        return next;
      });
      setCountOverrides((prev) => {
        const next = { ...prev };
        delete next[m.id];
        return next;
      });
      notify.error('Failed to record acknowledgement');
    } finally {
      setReacting(false);
    }
  };

  const audiencePayload = () => {
    const value = form.audience;
    const departmentId = form.departmentId ? Number(form.departmentId) : undefined;
    if (value === 'ALL_STUDENTS') return { targetRole: 'STUDENT' };
    if (value === 'ALL_PRS') return { targetRole: 'PR' };
    if (value === 'ALL_PCS') return { targetRole: 'PC' };
    if (value === 'ALL_POS') return { targetRole: 'PO' };
    if (value === 'DEPT_ALL') return { departmentId };
    if (value === 'DEPT_STUDENTS') return { departmentId, targetRole: 'STUDENT' };
    if (value === 'DEPT_PRS') return { departmentId, targetRole: 'PR' };
    if (value === 'DEPT_PCS') return { departmentId, targetRole: 'PC' };
    if (value === 'STUDENTS') return { targetRole: 'STUDENT' };
    if (value === 'PRS') return { targetRole: 'PR' };
    if (value === 'PCS') return { targetRole: 'PC' };
    return { targetRole: 'STUDENT' };
  };

  const openCompose = () => {
    setForm(emptyCompose(audiences));
    setSelectedIds([]);
    setRecipientQuery('');
    setRecipientResults([]);
    setComposeStep('compose');
    setComposeOpen(true);
  };

  const startSend = () => {
    if (!form.title.trim() || !form.content.trim()) {
      notify.error('Title and content are required');
      return;
    }
    const audience = audiences.find((a) => a.value === form.audience);
    if (!audience) {
      notify.error('Choose who this message is for');
      return;
    }
    if (audience.usesDepartment && !form.departmentId) {
      notify.error('Select a department for this audience');
      return;
    }
    if (audience.specific && selectedIds.length === 0) {
      notify.error('Choose at least one recipient');
      return;
    }
    setComposeStep('confirm');
  };

  const handleSend = async () => {
    setSaving(true);
    try {
      const res = await messageApi.send({
        title: form.title.trim(),
        content: form.content.trim(),
        messageType: form.messageType,
        ...(activeAudience?.specific
          ? { recipientIds: selectedIds }
          : { ...audiencePayload() }),
      });
      const sentCount = (res.data?.data as { totalRecipients?: number } | undefined)?.totalRecipients;
      notify.success(
        sentCount != null
          ? `Message delivered to ${sentCount.toLocaleString()} recipient${sentCount === 1 ? '' : 's'}`
          : 'Message sent successfully'
      );
      setComposeOpen(false);
      setForm(emptyCompose(audiences));
      setSelectedIds([]);
      setSelectedId(null);
      setTab('sent');
      setPage(0);
      refresh();
    } catch {
      notify.error('Failed to send message. Check the selected audience has valid recipients.');
    } finally {
      setSaving(false);
    }
  };

  const handleExport = async () => {
    if (!selected) return;
    setExporting(true);
    try {
      const res = await reportApi.messages(selected.id);
      const blob = new Blob([res.data], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `message-${selected.id}-responses.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      notify.success('Acknowledgements CSV downloaded');
    } catch {
      notify.error('Failed to export acknowledgements');
    } finally {
      setExporting(false);
    }
  };

  useEffect(() => {
    if (tab !== 'inbox' || !selected || selected.senderRole === 'PO' || (selected.senderRole !== 'PC' && selected.senderRole !== 'PO')) {
      return;
    }
    if (selected.senderRole !== 'PO' && selected.senderRole !== 'PC') {
      setRecipientThread(null);
      return;
    }
    let cancelled = false;
    setRcLoading(true);
    setRcError('');
    setRecipientThread(null);
    setRcAskOpen(false);
    setRcText('');
    void (async () => {
      try {
        const list = await clarificationApi.listForMessage(selected.id, { page: 0, size: 5 });
        if (cancelled) return;
        const threads = list.data?.data?.content ?? [];
        if (threads.length > 0) {
          const detail = await clarificationApi.getThread(threads[0].threadId, { page: 0, size: 50 });
          if (!cancelled) setRecipientThread(detail.data?.data ?? null);
        }
      } catch {
        if (!cancelled) setRcError('Could not load your clarification.');
      } finally {
        if (!cancelled) setRcLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, selectedId]);

  useEffect(() => {
    if (tab !== 'sent' || !selected) return;
    let cancelled = false;
    setSentClarLoading(true);
    setSentClarError('');
    void (async () => {
      try {
        const res = await clarificationApi.listForMessage(selected.id, { page: sentClarPage, size: 5 });
        if (cancelled) return;
        setSentThreads(res.data?.data?.content ?? []);
        setSentThreadTotal(res.data?.data?.totalElements ?? 0);
      } catch {
        if (!cancelled) setSentClarError('Could not load clarifications for this message.');
      } finally {
        if (!cancelled) setSentClarLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, selectedId, sentClarPage]);

  const postRecipientClarification = async () => {
    const content = rcText.trim();
    if (!content || !selected) return;
    setRcBusy(true);
    setRcError('');
    try {
      const res = recipientThread
        ? await clarificationApi.reply(recipientThread.threadId, content)
        : await clarificationApi.create(selected.id, content);
      setRecipientThread(res.data?.data ?? null);
      setRcText('');
      setRcAskOpen(false);
      notify.success(recipientThread ? 'Follow-up sent' : 'Question sent to the sender');
    } catch {
      setRcError('Failed to post. Please check your permissions.');
    } finally {
      setRcBusy(false);
    }
  };

  const [threadModal, setThreadModal] = useState<{
    threadId: number;
    thread: ClarificationThread | null;
    loading: boolean;
    error: string;
    text: string;
    busy: boolean;
  } | null>(null);

  const openThreadModal = async (threadId: number) => {
    setThreadModal({ threadId, thread: null, loading: true, error: '', text: '', busy: false });
    try {
      const res = await clarificationApi.getThread(threadId, { page: 0, size: 50 });
      setThreadModal((prev) => (prev && prev.threadId === threadId ? { ...prev, thread: res.data?.data ?? null, loading: false } : prev));
    } catch {
      setThreadModal((prev) => (prev && prev.threadId === threadId ? { ...prev, loading: false, error: 'Could not load this clarification thread.' } : prev));
    }
  };

  const postThreadReply = async () => {
    if (!threadModal || threadModal.busy || !threadModal.text.trim()) return;
    const text = threadModal.text.trim();
    setThreadModal((prev) => (prev ? { ...prev, busy: true, error: '' } : prev));
    try {
      const res = await clarificationApi.reply(threadModal.threadId, text);
      setThreadModal((prev) => (prev ? { ...prev, thread: res.data?.data ?? null, text: '', busy: false } : prev));
      notify.success('Reply sent');
      if (tab === 'sent' && selected) {
        clarificationApi
          .listForMessage(selected.id, { page: sentClarPage, size: 5 })
          .then((r) => {
            setSentThreads(r.data?.data?.content ?? []);
            setSentThreadTotal(r.data?.data?.totalElements ?? 0);
          })
          .catch(() => {});
        refresh();
      }
    } catch {
      setThreadModal((prev) => (prev ? { ...prev, busy: false, error: 'Failed to reply. Please check your permissions.' } : prev));
    }
  };

  const toggleRecipient = (u: User) => {
    setSelectedIds((prev) => (prev.includes(u.id) ? prev.filter((id) => id !== u.id) : [...prev, u.id]));
  };

  const searchTimer = useRef<number | null>(null);
  useEffect(() => {
    if (!activeAudience?.specific) {
      setRecipientResults([]);
      return;
    }
    if (searchTimer.current) window.clearTimeout(searchTimer.current);
    searchTimer.current = window.setTimeout(() => {
      let cancelled = false;
      setRecipientSearching(true);
      void (async () => {
        try {
          const res = await userApi.search({ search: recipientQuery.trim() || undefined, page: 0, size: 8 });
          const items = (res.data?.data?.content ?? []) as User[];
          if (!cancelled) {
            setRecipientResults(items.filter((u) => ROLE_ALLOWLIST[role]?.includes(u.role)));
          }
        } catch {
          if (!cancelled) setRecipientResults([]);
        } finally {
          if (!cancelled) setRecipientSearching(false);
        }
      })();
    }, 320);
    return () => {
      if (searchTimer.current) window.clearTimeout(searchTimer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipientQuery, activeAudience?.specific, role]);

  const confirmLine = (): string => {
    if (activeAudience?.specific) {
      return `Send this message to ${selectedIds.length} recipient${selectedIds.length === 1 ? '' : 's'}?`;
    }
    const dept = activeAudience?.usesDepartment
      ? departments.find((d) => d.id === Number(form.departmentId))?.name
      : undefined;
    return `Send this message to ${activeAudience?.label ?? 'this audience'}${dept ? ` (${dept})` : ''}?`;
  };

  const renderRail = () => {
    const items: { key: Tab; label: string; icon: typeof Inbox; active: boolean }[] = [
      { key: 'inbox', label: 'Inbox', icon: Inbox, active: tab === 'inbox' },
      ...(role === 'STUDENT'
        ? []
        : [{ key: 'sent' as Tab, label: 'Sent', icon: SendIcon, active: tab === 'sent' }]),
      ...(role === 'PO' || role === 'PC'
        ? [{ key: 'incoming' as Tab, label: 'Clarifications', icon: MessageCircleQuestion, active: tab === 'incoming' }]
        : []),
    ];
    return (
      <nav aria-label="Messages" className="flex lg:flex-col w-full lg:w-auto gap-1">
        {items.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => handleTabChange(item.key)}
            aria-current={item.active ? 'page' : undefined}
            className={`flex items-center gap-2.5 rounded-[10px] transition-colors text-[14px] font-medium ${
              item.active
                ? 'bg-primary-50 text-primary-700'
                : 'text-neutral-600 hover:bg-neutral-100/80 hover:text-neutral-900'
            }`}
          >
            <item.icon size={17} className={item.active ? 'text-primary-500' : 'text-neutral-500'} />
            <span className="lg:inline">{item.label}</span>
          </button>
        ))}
      </nav>
    );
  };

  const renderListSkeleton = () => (
    <div className="space-y-1 p-3">
      {Array.from({ length: 7 }).map((_, i) => (
        <div key={i} className="flex gap-3 p-3">
          <Skeleton className="h-8 w-8 rounded-full shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-3.5 w-1/2" />
            <Skeleton className="h-3 w-3/4" />
            <Skeleton className="h-3 w-1/4" />
          </div>
        </div>
      ))}
    </div>
  );

  const renderList = () => {
    if (tab === 'incoming') {
      if (loading) return renderListSkeleton();
      if (error) return <ErrorState message={error} onRetry={refresh} />;
      if (visibleThreads.length === 0 && !search.trim())
        return (
          <EmptyState
            icon={<MessageCircleQuestion size={40} />}
            title="No clarification requests"
            description="Recipients asking about your messages will appear here."
          />
        );
      return (
        <div className="divide-y divide-neutral-100/70">
          {visibleThreads.length === 0 ? (
            <EmptyState
              icon={<Search size={40} />}
              title="No matching threads"
              description="Try a different search query."
            />
          ) : (
          visibleThreads.map((t) => (
            <button
              key={t.threadId}
              type="button"
              onClick={() => void openThreadModal(t.threadId)}
              aria-label={`View clarification from ${t.requesterName}`}
              className="w-full text-left px-4 py-3.5 hover:bg-primary-50/40 transition-colors"
            >
              <div className="flex items-center justify-between gap-2">
                <p className="text-[14px] font-semibold text-neutral-900 truncate min-w-0">{t.requesterName}</p>
                <Badge variant={statusVariant(t.status)} size="sm">
                  {statusLabel(t.status)}
                </Badge>
              </div>
              <p className="text-[13px] text-neutral-500 truncate mt-0.5">{t.messageTitle}</p>
              <p className="text-[12px] text-neutral-500 mt-1">
                {t.updatedAt ? fmtTimestamp(t.updatedAt) : ''}
              </p>
            </button>
          )))}
          {totalPages > 1 && (
            <div className="px-3 py-2">
              <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={setPage} />
            </div>
          )}
        </div>
      );
    }

    if (loading) return renderListSkeleton();
    if (error) return <ErrorState message={error} onRetry={refresh} />;
    if (messages.length === 0)
      return (
        <EmptyState
          icon={tab === 'inbox' ? <Inbox size={40} /> : <SendIcon size={40} />}
          title={tab === 'inbox' ? 'No messages yet' : 'No sent messages'}
          description={
            tab === 'inbox'
              ? 'Placement communication sent to you will appear here.'
              : 'Messages you send will appear here.'
          }
        />
      );
    if (visibleMessages.length === 0)
      return (
        <EmptyState
          icon={<MailX size={40} />}
          title="No messages match your search"
          description="Try a different name, sender or keyword."
        />
      );

    return (
      <div className="divide-y divide-neutral-100/70">
        {visibleMessages.map((m) => {
          const active = selectedId === m.id;
          const unread = isUnread(m);
          const counts = countOf(m);
          return (
            <button
              key={m.id}
              type="button"
              onClick={() => handleSelect(m)}
              aria-current={active ? 'true' : undefined}
              className={`w-full text-left px-4 py-3.5 transition-colors ${
                active
                  ? 'bg-primary-50/70 border-l-2 border-primary-500'
                  : unread
                    ? 'bg-primary-50/30 hover:bg-primary-50/50 border-l-2 border-transparent'
                    : 'bg-white hover:bg-neutral-50/80 border-l-2 border-transparent'
              }`}
            >
              <div className="flex items-start gap-2.5">
                <span
                  className={`mt-1.5 h-2 w-2 rounded-full shrink-0 ${
                    unread && tab === 'inbox' ? 'bg-primary-500' : 'bg-transparent'
                  }`}
                  aria-hidden="true"
                />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[13px] font-semibold text-neutral-800 truncate">
                      {tab === 'inbox' ? m.senderName : scopeLine(m)}
                    </span>
                    <span className="text-[12px] text-neutral-500 whitespace-nowrap shrink-0">
                      {fmtTimestamp(m.createdAt)}
                    </span>
                  </div>
                  <h3
                    className={`text-[14.5px] mt-0.5 truncate ${
                      unread ? 'font-bold text-neutral-900' : 'font-semibold text-neutral-800'
                    }`}
                  >
                    {m.title}
                  </h3>
                  <div className="flex items-center justify-between gap-2 mt-0.5">
                    <p className="text-[13px] text-neutral-500 truncate">{previewText(m.content)}</p>
                    {tab === 'inbox' ? (
                      <span className="flex items-center gap-2 shrink-0">
                        <span className="flex items-center gap-0.5 text-[12px] text-success-600 font-medium" title="Acknowledged">
                          <ThumbsUp size={11} /> {counts.up}
                        </span>
                        <span className="flex items-center gap-0.5 text-[12px] text-warning-600 font-medium" title="Need clarification">
                          <ThumbsDown size={11} /> {counts.down}
                        </span>
                      </span>
                    ) : (
                      <span className="flex items-center gap-2 shrink-0 text-[12px] text-neutral-500">
                        {m.clarificationCount ? (
                          <span className="flex items-center gap-1 font-medium text-primary-600">
                            <MessageCircleQuestion size={11} /> {m.clarificationCount}
                          </span>
                        ) : null}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </button>
          );
        })}
        {totalPages > 1 && (
          <div className="px-3 py-2">
            <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={setPage} />
          </div>
        )}
      </div>
    );
  };

  const renderRecipientDetail = (m: Message) => {
    const reacted = myReactionOf(m);
    const counts = countOf(m);
    const eligible = m.senderRole === 'PO' || m.senderRole === 'PC';
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center justify-between gap-2 px-5 py-3 border-b border-neutral-200/70 shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            <button
              type="button"
              onClick={() => setSelectedId(null)}
              className="md:hidden p-1.5 rounded-[10px] text-neutral-500 hover:text-neutral-700 hover:bg-neutral-100"
              aria-label="Back to messages"
            >
              <ArrowLeft size={17} />
            </button>
            <h2 className="text-[16px] font-semibold text-neutral-900 truncate">{m.title}</h2>
          </div>
          <button
            type="button"
            onClick={() => setSelectedId(null)}
            className="hidden md:inline-flex p-1.5 rounded-[10px] text-neutral-500 hover:text-neutral-700 hover:bg-neutral-100"
            aria-label="Close message"
          >
            <X size={16} />
          </button>
        </div>

        <div className="px-5 pt-4 shrink-0">
          <div className="flex items-center gap-3">
            <Avatar name={m.senderName} size="sm" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2 flex-wrap">
                <p className="text-[14px] font-semibold text-neutral-900 truncate">{m.senderName}</p>
                <Badge variant="neutral" size="sm">
                  {ROLE_LABELS[m.senderRole] ?? m.senderRole}
                </Badge>
              </div>
              <p className="text-[12.5px] text-neutral-500 mt-0.5">{fmtDateTime(m.createdAt)}</p>
            </div>
            {m.messageType && (
              <Badge variant={m.messageType === 'DEPARTMENT' ? 'teal' : 'info'} size="sm">
                {TYPE_LABELS[m.messageType] ?? m.messageType}
              </Badge>
            )}
          </div>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto px-5 py-4">
          <div className="max-w-[760px]">
            <p className="text-[15px] leading-[1.65] text-neutral-800 whitespace-pre-wrap break-words">
              {m.content}
            </p>
          </div>

          <div className="mt-6 border-t border-neutral-200/70 pt-4 max-w-[760px]">
            <h3 className="text-[15px] font-semibold text-neutral-900">Acknowledgement</h3>
            <p className="text-[13.5px] text-neutral-500 mt-0.5">
              {reacted === 'UPVOTE'
                ? 'You have acknowledged this message. The sender can see your response.'
                : reacted === 'DOWNVOTE'
                  ? 'You asked the sender to clarify this message.'
                  : 'Did you receive and understand this message?'}
            </p>
            <div className="flex flex-wrap gap-2 mt-3">
              <button
                type="button"
                onClick={() => void handleReact(m, 'UPVOTE')}
                disabled={reacting}
                aria-pressed={reacted === 'UPVOTE'}
                className={`inline-flex items-center gap-1.5 h-[38px] px-4 rounded-[10px] text-[13.5px] font-semibold transition-all disabled:opacity-60 ${
                  reacted === 'UPVOTE'
                    ? 'bg-success-600 text-white shadow-soft'
                    : 'bg-white border border-neutral-200 text-neutral-700 hover:bg-neutral-50'
                }`}
              >
                {reacted === 'UPVOTE' && <Check size={14} />}
                <ThumbsUp size={13} />
                Acknowledged
              </button>
              <button
                type="button"
                onClick={() => void handleReact(m, 'DOWNVOTE')}
                disabled={reacting}
                aria-pressed={reacted === 'DOWNVOTE'}
                className={`inline-flex items-center gap-1.5 h-[38px] px-4 rounded-[10px] text-[13.5px] font-semibold transition-all disabled:opacity-60 ${
                  reacted === 'DOWNVOTE'
                    ? 'bg-warning-600 text-white shadow-soft'
                    : 'bg-white border border-neutral-200 text-neutral-700 hover:bg-neutral-50'
                }`}
              >
                <ThumbsDown size={13} />
                Need clarification
              </button>
            </div>
            <p className="text-[12.5px] text-neutral-500 mt-2 flex items-center gap-3">
              <span className="flex items-center gap-1 text-success-600">
                <ThumbsUp size={11} /> {counts.up.toLocaleString()} acknowledged
              </span>
              <span className="flex items-center gap-1 text-warning-600">
                <ThumbsDown size={11} /> {counts.down.toLocaleString()} need clarification
              </span>
            </p>
          </div>

          {eligible && (
            <div className="mt-6 border-t border-neutral-200/70 pt-4 max-w-[760px]">
              <h3 className="text-[15px] font-semibold text-neutral-900">Clarification</h3>
              {rcLoading ? (
                <div className="mt-3 space-y-2">
                  <Skeleton className="h-12 w-full" />
                  <Skeleton className="h-12 w-full" />
                </div>
              ) : rcError ? (
                <p className="mt-2 text-[13px] text-danger-600">{rcError}</p>
              ) : recipientThread ? (
                <div className="mt-3">
                  <Badge variant={statusVariant(recipientThread.status)} size="sm">
                    {statusLabel(recipientThread.status)}
                  </Badge>
                  <div className="mt-3 space-y-2">
                    {(recipientThread.entries ?? []).map((e) => {
                      const mine = e.authorId === recipientThread.requesterId;
                      return (
                        <div
                          key={e.id}
                          className={`rounded-[12px] px-3.5 py-2.5 ${
                            mine ? 'bg-neutral-50 border border-neutral-200/70' : 'bg-primary-50/60 border border-primary-100'
                          }`}
                        >
                          <div className="flex items-center justify-between gap-2">
                            <span className={`text-[12.5px] font-semibold ${mine ? 'text-neutral-800' : 'text-primary-700'}`}>
                              {mine ? 'You' : recipientThread.senderName}
                            </span>
                            <span className="text-[11.5px] text-neutral-500">{fmtTimestamp(e.createdAt)}</span>
                          </div>
                          <p className="text-[14px] text-neutral-700 leading-relaxed mt-1 whitespace-pre-wrap break-words">
                            {e.content}
                          </p>
                        </div>
                      );
                    })}
                  </div>
                  {rcAskOpen ? (
                    <div className="mt-3">
                      <Textarea
                        label="Follow-up question"
                        rows={2}
                        placeholder="Ask another question or add more detail..."
                        value={rcText}
                        onChange={(e) => setRcText(e.target.value)}
                      />
                      <div className="flex justify-end gap-2 mt-2">
                        <Button variant="secondary" size="sm" onClick={() => setRcAskOpen(false)} disabled={rcBusy}>
                          Cancel
                        </Button>
                        <Button size="sm" onClick={() => void postRecipientClarification()} loading={rcBusy} disabled={!rcText.trim()}>
                          <Reply size={13} /> Ask sender
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <Button
                      variant="secondary"
                      size="sm"
                      className="mt-3"
                      onClick={() => {
                        setRcAskOpen(true);
                        setRcText('');
                      }}
                    >
                      <MessageCircleQuestion size={13} /> Write a follow-up
                    </Button>
                  )}
                </div>
              ) : (
                <div className="mt-3">
                  <p className="text-[13.5px] text-neutral-500">Have a question about this message?</p>
                  {rcAskOpen ? (
                    <div className="mt-2">
                      <Textarea
                        label="Your question"
                        rows={2}
                        placeholder="Ask about eligibility, rounds, schedules or anything unclear..."
                        value={rcText}
                        onChange={(e) => setRcText(e.target.value)}
                      />
                      <div className="flex justify-end gap-2 mt-2">
                        <Button variant="secondary" size="sm" onClick={() => setRcAskOpen(false)} disabled={rcBusy}>
                          Cancel
                        </Button>
                        <Button size="sm" onClick={() => void postRecipientClarification()} loading={rcBusy} disabled={!rcText.trim()}>
                          <Reply size={13} /> Ask sender
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <Button
                      variant="secondary"
                      size="sm"
                      className="mt-2"
                      onClick={() => {
                        setRcAskOpen(true);
                        setRcText('');
                      }}
                    >
                      <MessageCircleQuestion size={13} /> Ask for clarification
                    </Button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    );
  };

  const renderSenderDetail = (m: Message) => {
    const counts = countOf(m);
    const pending = m.totalRecipients - counts.up - counts.down;
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center justify-between gap-2 px-5 py-3 border-b border-neutral-200/70 shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            <button
              type="button"
              onClick={() => setSelectedId(null)}
              className="md:hidden p-1.5 rounded-[10px] text-neutral-500 hover:text-neutral-700 hover:bg-neutral-100"
              aria-label="Back to messages"
            >
              <ArrowLeft size={17} />
            </button>
            <h2 className="text-[16px] font-semibold text-neutral-900 truncate">{m.title}</h2>
          </div>
          <button
            type="button"
            onClick={() => setSelectedId(null)}
            className="hidden md:inline-flex p-1.5 rounded-[10px] text-neutral-500 hover:text-neutral-700 hover:bg-neutral-100"
            aria-label="Close message"
          >
            <X size={16} />
          </button>
        </div>

        <div className="px-5 pt-4 shrink-0">
          <div className="flex items-center gap-3 flex-wrap">
            <p className="text-[14px] font-semibold text-neutral-900">
              {ROLE_LABELS[m.senderRole] ?? m.senderRole}
            </p>
            <Badge variant="neutral" size="sm">{scopeLine(m)}</Badge>
            <span className="text-[12.5px] text-neutral-500">{fmtDateTime(m.createdAt)}</span>
          </div>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto px-5 py-4">
          <div className="max-w-[760px]">
            <p className="text-[15px] leading-[1.65] text-neutral-800 whitespace-pre-wrap break-words">
              {m.content}
            </p>
          </div>

          <div className="mt-6 border-t border-neutral-200/70 pt-4 max-w-[760px]">
            <h3 className="text-[15px] font-semibold text-neutral-900">Delivery &amp; Response</h3>
            <div className="grid grid-cols-3 sm:grid-cols-5 gap-3 mt-3">
              <div className="rounded-[12px] bg-neutral-50 border border-neutral-200/70 px-3 py-2.5">
                <p className="text-[18px] font-bold text-neutral-900 tabular-nums">{m.totalRecipients.toLocaleString()}</p>
                <p className="text-[12px] text-neutral-500 -mt-0.5">Recipients</p>
              </div>
              <div className="rounded-[12px] bg-neutral-50 border border-neutral-200/70 px-3 py-2.5">
                <p className="text-[18px] font-bold text-primary-600 tabular-nums">{m.readCount.toLocaleString()}</p>
                <p className="text-[12px] text-neutral-500 -mt-0.5">Read</p>
              </div>
              <div className="rounded-[12px] bg-neutral-50 border border-neutral-200/70 px-3 py-2.5">
                <p className="text-[18px] font-bold text-success-600 tabular-nums">{counts.up.toLocaleString()}</p>
                <p className="text-[12px] text-neutral-500 -mt-0.5">Acknowledged</p>
              </div>
              <div className="rounded-[12px] bg-neutral-50 border border-neutral-200/70 px-3 py-2.5">
                <p className="text-[18px] font-bold text-warning-600 tabular-nums">{counts.down.toLocaleString()}</p>
                <p className="text-[12px] text-neutral-500 -mt-0.5">Need clarification</p>
              </div>
              <div className="rounded-[12px] bg-neutral-50 border border-neutral-200/70 px-3 py-2.5 col-span-3 sm:col-span-1">
                <p className="text-[18px] font-bold text-neutral-600 tabular-nums">{pending.toLocaleString()}</p>
                <p className="text-[12px] text-neutral-500 -mt-0.5">Pending</p>
              </div>
            </div>
            <div className="space-y-3 mt-4 max-w-[420px]">
              <ProgressBar label="Read" value={m.readCount} total={m.totalRecipients} />
              <ProgressBar label="Responses" value={counts.up + counts.down} total={m.totalRecipients} />
            </div>
            {(role === 'PO' || role === 'PC') && (
              <Button variant="secondary" size="sm" className="mt-4" loading={exporting} onClick={handleExport}>
                <Download size={13} /> Export acknowledgements (CSV)
              </Button>
            )}
          </div>

          <div className="mt-6 border-t border-neutral-200/70 pt-4 max-w-[760px]">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-[15px] font-semibold text-neutral-900">Clarifications</h3>
              <span className="text-[13px] text-neutral-500">
                {m.clarificationCount ? `${m.openClarificationCount ?? 0} awaiting reply · ${m.answeredClarificationCount ?? 0} answered` : 'None yet'}
              </span>
            </div>
            {sentClarLoading ? (
              <div className="mt-3 space-y-2">
                <Skeleton className="h-14 w-full" />
                <Skeleton className="h-14 w-full" />
              </div>
            ) : sentClarError ? (
              <div className="mt-3">
                <p className="text-[13px] text-danger-600">{sentClarError}</p>
                <Button variant="secondary" size="sm" className="mt-2" onClick={() => setSentClarPage((p) => p)}>
                  Try Again
                </Button>
              </div>
            ) : sentThreads.length === 0 ? (
              <p className="mt-2 text-[13.5px] text-neutral-500">
                Recipients can ask questions about this message.
              </p>
            ) : (
              <div className="mt-3 divide-y divide-neutral-100/70 border rounded-[12px] border-neutral-200/70 overflow-hidden">
                {sentThreads.map((t) => (
                  <button
                    key={t.threadId}
                    type="button"
                    onClick={() => void openThreadModal(t.threadId)}
                    className="w-full flex items-center justify-between gap-3 px-3.5 py-3 text-left hover:bg-primary-50/40 transition-colors"
                    aria-label={`View clarification from ${t.requesterName}`}
                  >
                    <div className="min-w-0">
                      <p className="text-[14px] font-semibold text-neutral-900 truncate">{t.requesterName}</p>
                      <p className="text-[12.5px] text-neutral-500 mt-0.5">{t.updatedAt ? fmtTimestamp(t.updatedAt) : ''}</p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <Badge variant={statusVariant(t.status)} size="sm">
                        {statusLabel(t.status)}
                      </Badge>
                      <span className="text-[13px] font-medium text-primary-600">View →</span>
                    </div>
                  </button>
                ))}
              </div>
            )}
            {sentThreadTotal > 5 && (
              <div className="mt-3">
                <Pagination page={sentClarPage} totalPages={Math.ceil(sentThreadTotal / 5)} totalElements={sentThreadTotal} pageSize={5} onPageChange={setSentClarPage} />
              </div>
            )}
          </div>
        </div>
      </div>
    );
  };

  const renderDetail = () => {
    if (tab === 'incoming') {
      return (
        <div className="h-full flex items-center justify-center">
          <EmptyState
            icon={<MessageCircleQuestion size={40} />}
            title="Select a clarification"
            description="Choose a request from the list to read and reply to the thread."
          />
        </div>
      );
    }
    if (!selected) {
      return (
        <div className="h-full flex items-center justify-center">
          <EmptyState
            icon={<MessageSquare size={40} />}
            title="Select a message"
            description="Choose a message from the list to view its contents."
          />
        </div>
      );
    }
    return tab === 'inbox' ? renderRecipientDetail(selected) : renderSenderDetail(selected);
  };

  const renderCompose = () => (
    <Modal
      isOpen={composeOpen}
      onClose={() => setComposeOpen(false)}
      title={composeStep === 'compose' ? 'Compose Message' : 'Confirm send'}
      description={
        composeStep === 'compose'
          ? 'Send a new message to a supported audience.'
          : 'Review the destination before sending.'
      }
      size="lg"
      actions={
        composeStep === 'compose' ? (
          <>
            <Button variant="secondary" onClick={() => setComposeOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={startSend}>
              <SendIcon size={14} /> Continue
            </Button>
          </>
        ) : (
          <>
            <Button variant="secondary" onClick={() => setComposeStep('compose')} disabled={saving}>
              Edit
            </Button>
            <Button onClick={() => void handleSend()} loading={saving} variant={activeAudience?.specific && selectedIds.length > 50 ? 'danger' : 'primary'}>
              <SendIcon size={14} /> Send Message
            </Button>
          </>
        )
      }
    >
      {composeStep === 'compose' ? (
        <div className="space-y-4">
          {audiences.length > 0 && (
            <>
              <Select
                label="Audience"
                required
                options={audiences.map((a) => ({ label: a.label, value: a.value }))}
                value={form.audience}
                onChange={(e) => setForm({ ...form, audience: e.target.value, departmentId: '' })}
              />
              {activeAudience?.usesDepartment && (
                <Select
                  label="Department"
                  required
                  placeholder="Select a department"
                  options={departments.map((d) => ({ label: d.name, value: String(d.id) }))}
                  value={form.departmentId}
                  onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                />
              )}
            </>
          )}
          {activeAudience?.specific && (
            <div>
              <Input
                label="Search recipients"
                placeholder="Type a name or email to search..."
                value={recipientQuery}
                onChange={(e) => setRecipientQuery(e.target.value)}
                icon={recipientSearching ? <AlertCircle size={16} className="animate-pulse" /> : undefined}
              />
              {recipientQuery.trim() !== '' && (
                <div className="mt-2 border border-neutral-200/80 rounded-[10px] bg-white shadow-soft overflow-hidden">
                  {recipientResults.length === 0 ? (
                    <p className="px-3.5 py-2.5 text-[13px] text-neutral-500">
                      {recipientSearching ? 'Searching…' : 'No matching recipients.'}
                    </p>
                  ) : (
                    recipientResults.map((u) => {
                      const added = selectedIds.includes(u.id);
                      return (
                        <button
                          key={u.id}
                          type="button"
                          onClick={() => toggleRecipient(u)}
                          aria-pressed={added}
                          className={`w-full flex items-center justify-between gap-3 px-3.5 py-2.5 text-left hover:bg-primary-50/50 transition-colors ${
                            added ? 'bg-primary-50/60' : ''
                          }`}
                        >
                          <span className="min-w-0">
                            <span className="block text-[14px] font-medium text-neutral-900 truncate">{u.name}</span>
                            <span className="block text-[12.5px] text-neutral-500 truncate">
                              {ROLE_LABELS[u.role] ?? u.role}
                              {u.departmentName ? ` · ${u.departmentName}` : ''}
                            </span>
                          </span>
                          {added && <Check size={15} className="text-primary-600 shrink-0" />}
                        </button>
                      );
                    })
                  )}
                </div>
              )}
              {selectedIds.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {selectedIds.map((id) => {
                    const u = recipientResults.find((r) => r.id === id);
                    const label = u ? u.name : `#${id}`;
                    return (
                      <span key={id} className="inline-flex items-center gap-1.5 bg-primary-50 text-primary-700 border border-primary-100 rounded-full pl-2.5 pr-1.5 py-1 text-[12.5px] font-medium">
                        {label}
                        <button
                          type="button"
                          onClick={() => toggleRecipient({ id } as User)}
                          aria-label={`Remove ${label}`}
                          className="p-0.5 rounded-full hover:bg-primary-100 transition-colors"
                        >
                          <X size={12} />
                        </button>
                      </span>
                    );
                  })}
                  <span className="inline-flex items-center gap-1 text-[12.5px] text-neutral-500 px-1">
                    {selectedIds.length} selected
                  </span>
                </div>
              )}
            </div>
          )}
          <Input
            label="Title"
            required
            placeholder="Message subject..."
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
          <Textarea
            label="Content"
            required
            rows={6}
            placeholder="Write your message..."
            value={form.content}
            onChange={(e) => setForm({ ...form, content: e.target.value })}
          />
          <Select
            label="Message Type"
            options={MESSAGE_TYPE_OPTIONS}
            value={form.messageType}
            onChange={(e) => setForm({ ...form, messageType: e.target.value })}
          />
          {activeAudience && !activeAudience.specific && (
            <p className="text-[13.5px] text-neutral-500 leading-relaxed">{activeAudience.hint}</p>
          )}
        </div>
      ) : (
        <div className="space-y-4">
          <div className="rounded-[12px] bg-neutral-50 border border-neutral-200/70 p-4">
            <p className="text-[13px] text-neutral-500">Title</p>
            <p className="text-[15px] font-semibold text-neutral-900 mt-0.5">{form.title.trim()}</p>
          </div>
          <div className="rounded-[12px] border border-neutral-200/70 p-4 bg-white">
            <p className="text-[13px] text-neutral-500">Message</p>
            <p className="text-[14px] text-neutral-700 leading-relaxed mt-1 whitespace-pre-wrap break-words line-clamp-4">
              {form.content.trim()}
            </p>
          </div>
          <div className="flex items-center gap-2 text-[14px] text-warning-700 bg-warning-50 border border-warning-200/70 rounded-[10px] px-3.5 py-2.5">
            <AlertCircle size={15} className="shrink-0" />
            <span>{confirmLine()}</span>
          </div>
          {activeAudience?.specific && (
            <p className="text-[13px] text-neutral-500">
              Recipient count is exact: {selectedIds.length} person{selectedIds.length === 1 ? '' : 's'} will receive this message.
            </p>
          )}
        </div>
      )}
    </Modal>
  );

  const renderThreadModal = () =>
    threadModal && (
      <Modal
        isOpen={true}
        onClose={() => setThreadModal(null)}
        title={threadModal.thread ? `Clarification with ${threadModal.thread.requesterName}` : 'Clarification thread'}
        description={threadModal.thread?.messageTitle ?? undefined}
        size="lg"
        actions={
          <Button variant="secondary" onClick={() => setThreadModal(null)}>
            Close
          </Button>
        }
      >
        {threadModal.loading ? (
          <div className="space-y-3">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        ) : threadModal.error ? (
          <p className="text-[13px] text-danger-600">{threadModal.error}</p>
        ) : threadModal.thread ? (
          <div className="space-y-4">
            <div className="flex items-center gap-2 flex-wrap">
              <Badge variant={statusVariant(threadModal.thread.status)} size="sm">
                {statusLabel(threadModal.thread.status)}
              </Badge>
              <span className="text-[13px] text-neutral-500">
                {threadModal.thread.updatedAt ? fmtDateTime(threadModal.thread.updatedAt) : ''}
              </span>
            </div>
            <div className="space-y-2">
              {(threadModal.thread.entries ?? []).map((e) => {
                const mine = e.authorId === threadModal.thread?.requesterId;
                return (
                  <div
                    key={e.id}
                    className={`rounded-[12px] px-4 py-3 ${
                      mine ? 'bg-neutral-50 border border-neutral-200/70' : 'bg-primary-50/60 border border-primary-100'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className={`text-[13px] font-semibold ${mine ? 'text-neutral-800' : 'text-primary-700'}`}>
                        {mine ? threadModal.thread?.requesterName : threadModal.thread?.senderName}
                      </span>
                      <span className="text-[12px] text-neutral-500">{fmtTimestamp(e.createdAt)}</span>
                    </div>
                    <p className="text-[14px] text-neutral-700 leading-relaxed mt-1 whitespace-pre-wrap break-words">
                      {e.content}
                    </p>
                  </div>
                );
              })}
            </div>
            <div>
              <Textarea
                label="Reply to clarify"
                rows={3}
                placeholder="Write your official answer..."
                value={threadModal.text}
                onChange={(e) => setThreadModal({ ...threadModal, text: e.target.value })}
              />
              <div className="flex justify-end mt-2">
                <Button onClick={() => void postThreadReply()} loading={threadModal.busy} disabled={!threadModal.text.trim()}>
                  <Reply size={14} /> Send reply
                </Button>
              </div>
            </div>
          </div>
        ) : null}
      </Modal>
    );

  const showCompose = role !== 'STUDENT';

  return (
    <PageContainer>
      <PageHeader
        title="Messages"
        description="Placement communication and announcements."
        actions={
          showCompose && (
            <Button onClick={openCompose} size="md">
              <SendIcon size={15} />
              Compose Message
            </Button>
          )
        }
      />

      <div className="lg:hidden flex mb-3">
        {renderRail()}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-[minmax(320px,400px)_minmax(0,1fr)] lg:grid-cols-[176px_minmax(340px,420px)_minmax(0,1fr)] h-[calc(100dvh-240px)] min-h-[460px] overflow-hidden lg:border lg:border-neutral-200/80 lg:rounded-[14px] lg:bg-white lg:shadow-soft gap-3 lg:gap-0">
        <aside className="hidden lg:flex flex-col gap-0.5 p-3 border-r border-neutral-200/70 bg-neutral-50/40">
          {renderRail()}
        </aside>

        <section
          className={`flex-col min-w-0 md:flex ${selectedId && tab !== 'incoming' ? 'hidden md:flex' : 'flex'} border border-neutral-200/80 rounded-[14px] bg-white shadow-soft lg:border-0 lg:rounded-none lg:shadow-none lg:border-r lg:border-neutral-200/70 overflow-hidden`}
        >
          <div className="p-3 border-b border-neutral-200/70 shrink-0">
            <div className="flex items-center justify-between gap-2 mb-2">
              <p className="text-[13px] font-semibold text-neutral-700">
                {tab === 'inbox' ? 'Inbox' : tab === 'sent' ? 'Sent' : 'Clarifications'}
                <span className="text-neutral-500 font-medium ml-1.5">
                  {totalElements.toLocaleString()}
                </span>
              </p>
            </div>
            {tab !== 'incoming' && (
              <div className="flex items-center gap-2">
                <SearchInput
                  placeholder={tab === 'sent' ? 'Search sent messages…' : 'Search messages…'}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  aria-label="Search messages"
                />
              </div>
            )}
            {tab === 'inbox' && (
              <div className="flex items-center gap-1 mt-2.5 flex-wrap" role="group" aria-label="Filter inbox">
                {(
                  [
                    { key: 'all', label: 'All' },
                    { key: 'unread', label: 'Unread' },
                    { key: 'ack', label: 'Acknowledged' },
                    { key: 'needs-clarification', label: 'Needs clarification' },
                  ] as { key: InboxFilter; label: string }[]
                ).map((f) => (
                  <button
                    key={f.key}
                    type="button"
                    aria-pressed={filter === f.key}
                    onClick={() => setFilter(f.key)}
                    className={`px-2.5 py-1 rounded-full text-[12.5px] font-medium transition-colors ${
                      filter === f.key
                        ? 'bg-primary-50 text-primary-700 border border-primary-200'
                        : 'text-neutral-500 hover:bg-neutral-100 border border-transparent'
                    }`}
                  >
                    {f.label}
                  </button>
                ))}
              </div>
            )}
          </div>
          <div className="flex-1 min-h-0 overflow-y-auto">
            {tab === 'incoming' ? renderList() : renderList()}
          </div>
        </section>

        <section className={`flex-col min-w-0 ${selectedId === null || tab === 'incoming' ? 'hidden md:flex' : 'flex'} border border-neutral-200/80 rounded-[14px] bg-white shadow-soft lg:border-0 lg:rounded-none lg:shadow-none overflow-hidden`}>
          {renderDetail()}
        </section>
      </div>

      {renderCompose()}
      {renderThreadModal()}
    </PageContainer>
  );
}