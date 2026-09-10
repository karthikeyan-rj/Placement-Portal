import { useEffect, useMemo, useState } from 'react';
import { messageApi, departmentApi, reportApi } from '../../api/api';
import type { Department, Message, User } from '../../types';
import { useAuth } from '../../context/AuthContext';
import {
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
  Tabs,
  Textarea,
  formatStatus,
  notify,
} from '../../components/ui';
import { usePaginatedData } from '../../hooks/useApi';
import {
  ArrowLeft,
  CheckCircle2,
  Download,
  Inbox,
  MessageSquare,
  Send as SendIcon,
  ThumbsDown,
  ThumbsUp,
  X,
} from 'lucide-react';

type Tab = 'inbox' | 'sent';
type Reaction = 'UPVOTE' | 'DOWNVOTE';

interface ComposeAudience {
  value: string;
  label: string;
  hint: string;
  usesDepartment?: boolean;
}

const MESSAGE_TYPE_OPTIONS = [
  { label: 'Broadcast', value: 'BROADCAST' },
  { label: 'Department', value: 'DEPARTMENT' },
  { label: 'Direct', value: 'DIRECT' },
];

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
  PR: [
    { value: 'STUDENTS', label: 'Students (my department)', hint: 'Active students in your department.' },
  ],
  STUDENT: [],
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

const readKey = (email: string) => `pp:msgs:read:${email}`;
const reactKey = (email: string) => `pp:msgs:react:${email}`;

const loadReadIds = (email: string): Set<number> => {
  try {
    const raw = localStorage.getItem(readKey(email));
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? new Set(parsed) : new Set();
  } catch {
    return new Set();
  }
};

const loadReactions = (email: string): Record<string, Reaction> => {
  try {
    const raw = localStorage.getItem(reactKey(email));
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};

const messageTypeVariant = (t: string | null): 'warning' | 'teal' | 'neutral' => {
  if (t === 'BROADCAST') return 'warning';
  if (t === 'DEPARTMENT') return 'teal';
  return 'neutral';
};

export default function MessagesPage() {
  const { user } = useAuth();
  const role = (user?.role || 'PO') as User['role'];
  const email = user?.email || 'anon';

  const [tab, setTab] = useState<Tab>('inbox');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [composeOpen, setComposeOpen] = useState(false);
  const [form, setForm] = useState<ComposeForm>(() => emptyCompose(AUDIENCES[role] ?? []));
  const [saving, setSaving] = useState(false);
  const [readIds, setReadIds] = useState<Set<number>>(() => loadReadIds(email));
  const [myReactions, setMyReactions] = useState<Record<string, Reaction>>(() => loadReactions(email));
  const [countOverrides, setCountOverrides] = useState<Record<string, { up: number; down: number }>>({});
  const [reacting, setReacting] = useState(false);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [exporting, setExporting] = useState(false);

  const url = tab === 'inbox' ? '/messages/received' : '/messages/sent';

  const {
    data: messages,
    loading,
    error,
    page,
    totalPages,
    totalElements,
    setPage,
    refresh,
  } = usePaginatedData<Message>({ url });

  const audiences = useMemo(() => AUDIENCES[role] ?? [], [role]);

  useEffect(() => {
    if (role !== 'PO') return;
    let cancelled = false;
    departmentApi
      .getActive()
      .then((res) => {
        if (!cancelled) {
          const d = res.data?.data;
          setDepartments(Array.isArray(d) ? d : []);
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [role]);

  const selected = messages.find((m) => m.id === selectedId) ?? null;

  const countOf = (m: Message) => {
    const o = countOverrides[m.id];
    return { up: o ? o.up : m.upvoteCount, down: o ? o.down : m.downvoteCount };
  };

  const isUnread = (m: Message) => tab === 'inbox' && !readIds.has(m.id);

  const activeAudience = audiences.find((a) => a.value === form.audience);

  const visibleMessages = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return messages;
    return messages.filter(
      (m) =>
        m.title.toLowerCase().includes(q) ||
        m.content.toLowerCase().includes(q) ||
        m.senderName.toLowerCase().includes(q)
    );
  }, [messages, search]);

  const handleSelect = (m: Message) => {
    setSelectedId(m.id);
    if (tab === 'inbox' && !readIds.has(m.id)) {
      const next = new Set(readIds);
      next.add(m.id);
      setReadIds(next);
      try {
        localStorage.setItem(readKey(email), JSON.stringify([...next]));
      } catch {}
      messageApi.markAsRead(m.id).catch(() => {});
    }
  };

  const handleReact = async (m: Message, type: Reaction) => {
    if (reacting) return;
    setReacting(true);
    const prior = myReactions[m.id];
    const base = { up: m.upvoteCount, down: m.downvoteCount };
    let up = base.up;
    let down = base.down;
    if (prior === 'UPVOTE') up -= 1;
    else if (prior === 'DOWNVOTE') down -= 1;
    if (type === 'UPVOTE') up += 1;
    else down += 1;

    setCountOverrides((prev) => ({ ...prev, [m.id]: { up, down } }));
    const nextReactions = { ...myReactions, [m.id]: type };
    setMyReactions(nextReactions);
    try {
      localStorage.setItem(reactKey(email), JSON.stringify(nextReactions));
    } catch {}

    try {
      await messageApi.react(m.id, type);
      notify.success(type === 'UPVOTE' ? 'Acknowledgement recorded' : 'Clarification noted');
    } catch {
      const prevReactions = { ...myReactions };
      delete prevReactions[m.id];
      setMyReactions(prevReactions);
      try {
        localStorage.setItem(reactKey(email), JSON.stringify(prevReactions));
      } catch {}
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

  const handleSend = async () => {
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
    setSaving(true);
    try {
      await messageApi.send({
        title: form.title.trim(),
        content: form.content.trim(),
        messageType: form.messageType,
        ...audiencePayload(),
      });
      notify.success('Message sent successfully');
      setComposeOpen(false);
      setForm(emptyCompose(audiences));
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

  const renderList = () => {
    if (loading) {
      return (
        <div className="space-y-2 p-4">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="glass rounded-[12px] p-4 animate-pulse">
              <Skeleton className="h-4 w-3/4 mb-2" />
              <Skeleton className="h-3 w-1/2 mb-2" />
              <Skeleton className="h-3 w-1/4" />
            </div>
          ))}
        </div>
      );
    }

    if (error) {
      return (
        <div className="p-4">
          <ErrorState message={error} onRetry={refresh} />
        </div>
      );
    }

    if (messages.length === 0) {
      return (
        <div className="py-16">
          <EmptyState
            icon={tab === 'inbox' ? <Inbox size={40} /> : <SendIcon size={40} />}
            title={tab === 'inbox' ? 'No messages in inbox' : 'No messages sent'}
            description={
              tab === 'inbox'
                ? 'No one has sent you a message yet.'
                : "You haven't sent any messages yet."
            }
          />
        </div>
      );
    }

    if (visibleMessages.length === 0) {
      return (
        <div className="py-16">
          <EmptyState
            icon={<MessageSquare size={40} />}
            title="No matches"
            description="No messages match your search."
          />
        </div>
      );
    }

    return (
      <div className="divide-y divide-white/40">
        {visibleMessages.map((msg) => {
          const active = selectedId === msg.id;
          const unread = isUnread(msg);
          const counts = countOf(msg);
          return (
            <button
              key={msg.id}
              type="button"
              onClick={() => handleSelect(msg)}
              className={`w-full text-left px-5 py-4 transition-all duration-150 ${
                active ? 'bg-primary-50/60 border-l-2 border-primary-500' : 'hover:bg-white/60'
              }`}
            >
              <div className="flex items-start justify-between mb-1">
                <div className="flex items-center gap-2 min-w-0">
                  {unread && <span className="w-2 h-2 rounded-full bg-primary-500 shrink-0" />}
                  <span
                    className={`text-[15px] truncate ${
                      unread ? 'font-semibold text-neutral-900' : 'font-medium text-neutral-800'
                    }`}
                  >
                    {msg.title}
                  </span>
                </div>
                <div className="flex items-center gap-2 shrink-0 ml-2">
                  <span className="flex items-center gap-0.5 text-[15px] text-neutral-400">
                    <ThumbsUp size={10} /> {counts.up}
                  </span>
                  <span className="flex items-center gap-0.5 text-[15px] text-neutral-400">
                    <ThumbsDown size={10} /> {counts.down}
                  </span>
                </div>
              </div>
              <div className="flex items-center gap-2 ml-4 min-w-0">
                <span className="text-[15px] text-neutral-500 truncate">
                  {tab === 'inbox' ? `From: ${msg.senderName}` : `To: ${msg.totalRecipients} recipients`}
                </span>
                <span className="text-neutral-300 shrink-0">·</span>
                <span className="text-[15px] text-neutral-400 whitespace-nowrap">
                  {new Date(msg.createdAt).toLocaleDateString()}
                </span>
              </div>
            </button>
          );
        })}
        {totalPages > 1 && (
          <div className="px-5 py-3">
            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={setPage}
            />
          </div>
        )}
      </div>
    );
  };

  const renderViewer = () => {
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

    const recipient = tab === 'inbox';
    const counts = countOf(selected);
    const reacted = myReactions[selected.id];

    return (
      <div className="h-full flex flex-col">
        <div className="px-6 py-4 border-b border-white/40">
          <div className="flex items-start justify-between gap-2 mb-2">
            <div className="flex items-center gap-2 min-w-0">
              <button
                type="button"
                onClick={() => setSelectedId(null)}
                className="lg:hidden p-1.5 rounded-[10px] text-neutral-400 hover:text-neutral-600 hover:bg-neutral-100 transition-colors shrink-0"
                aria-label="Back to messages"
              >
                <ArrowLeft size={16} />
              </button>
              <div className="min-w-0 flex-1">
                <h2 className="text-[15px] font-semibold text-neutral-900 truncate">{selected.title}</h2>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setSelectedId(null)}
              className="p-1.5 rounded-[10px] text-neutral-400 hover:text-neutral-600 hover:bg-neutral-100 transition-colors shrink-0"
              aria-label="Close message"
            >
              <X size={16} />
            </button>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <Badge variant="info" size="sm">
              {recipient ? `From ${selected.senderName}` : `To ${selected.totalRecipients} recipients`}
            </Badge>
            <Badge
              variant={recipient ? 'neutral' : 'teal'}
              size="sm"
            >
              {formatStatus(selected.senderRole)}
            </Badge>
            {selected.messageType && (
              <Badge variant={messageTypeVariant(selected.messageType)} size="sm">
                {formatStatus(selected.messageType)}
              </Badge>
            )}
            <span className="text-[13px] text-neutral-400">
              {new Date(selected.createdAt).toLocaleString()}
            </span>
          </div>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto px-6 py-5">
          <p className="text-[15px] text-neutral-700 whitespace-pre-wrap leading-relaxed">
            {selected.content}
          </p>
        </div>

        <div className="border-t border-white/40 px-6 py-4">
          {recipient ? (
            <div>
              <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-400 mb-3">
                Your acknowledgement
              </p>
              <div className="flex flex-wrap gap-2">
                <Button
                  variant={reacted === 'UPVOTE' ? 'primary' : 'secondary'}
                  size="sm"
                  disabled={reacting}
                  onClick={() => handleReact(selected, 'UPVOTE')}
                >
                  <ThumbsUp size={13} /> Received ({counts.up})
                </Button>
                <Button
                  variant={reacted === 'DOWNVOTE' ? 'primary' : 'secondary'}
                  size="sm"
                  disabled={reacting}
                  onClick={() => handleReact(selected, 'DOWNVOTE')}
                >
                  <ThumbsDown size={13} /> Need clarification ({counts.down})
                </Button>
              </div>
              <p className="text-[13px] text-neutral-400 mt-2">
                {reacted
                  ? reacted === 'UPVOTE'
                    ? 'You have acknowledged this message.'
                    : 'You have requested clarification on this message.'
                  : 'Acknowledge this message so the sender can confirm you received it.'}
              </p>
            </div>
          ) : (
            <div>
              <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-400 mb-3">
                Delivery &amp; Acknowledgement Summary
              </p>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div className="glass rounded-[10px] p-3 text-center">
                  <div className="text-[17px] font-semibold text-neutral-900">{selected.totalRecipients}</div>
                  <div className="text-[13px] text-neutral-500">Recipients</div>
                </div>
                <div className="glass rounded-[10px] p-3 text-center">
                  <div className="text-[17px] font-semibold text-green-600">{selected.deliveredCount}</div>
                  <div className="text-[13px] text-neutral-500">Delivered</div>
                </div>
                <div className="glass rounded-[10px] p-3 text-center">
                  <div className="text-[17px] font-semibold text-primary-600">{selected.readCount}</div>
                  <div className="text-[13px] text-neutral-500">Read</div>
                </div>
                <div className="glass rounded-[10px] p-3 text-center">
                  <div className="text-[17px] font-semibold text-accent-600">
                    {selected.readCount}/{selected.totalRecipients || 1}
                  </div>
                  <div className="text-[13px] text-neutral-500">Read %</div>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-3 mt-3">
                <span className="flex items-center gap-1.5 text-[14px] text-neutral-600">
                  <ThumbsUp size={14} className="text-green-600" /> {counts.up} acknowledged
                </span>
                <span className="flex items-center gap-1.5 text-[14px] text-neutral-600">
                  <ThumbsDown size={14} className="text-danger-500" /> {counts.down} clarification
                </span>
                {role === 'PO' || role === 'PC' ? (
                  <Button
                    variant="secondary"
                    size="sm"
                    className="ml-auto"
                    loading={exporting}
                    onClick={handleExport}
                  >
                    <Download size={13} /> Export acknowledgements (CSV)
                  </Button>
                ) : (
                  <span className="ml-auto hidden sm:flex items-center gap-1.5 text-[13px] text-neutral-400">
                    <CheckCircle2 size={13} /> Detailed export is available to administrators
                  </span>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <PageContainer>
      <PageHeader
        title="Messages"
        description="Send announcements and track acknowledgements from students and faculty."
        actions={
          <Button
            onClick={() => {
              setForm(emptyCompose(audiences));
              setComposeOpen(true);
            }}
            size="md"
          >
            <SendIcon size={15} />
            Compose
          </Button>
        }
      />

      <div className="flex flex-col sm:flex-row sm:items-start gap-3 sm:gap-0">
        <div className="sm:flex-1">
          <Tabs<Tab>
            tabs={[
              { key: 'inbox', label: 'Inbox' },
              { key: 'sent', label: 'Sent' },
            ]}
            active={tab}
            onChange={(t) => {
              setTab(t);
              setPage(0);
              setSelectedId(null);
              setSearch('');
            }}
          />
        </div>
        <div className="w-full sm:w-72 shrink-0">
          <SearchInput
            placeholder="Search messages..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="glass rounded-[20px] shadow-glass">
        <div className="grid grid-cols-1 lg:grid-cols-5 min-h-[600px]">
          <div
            className={`lg:col-span-2 border-r border-white/40 ${
              selected ? 'hidden lg:block' : 'lg:col-span-5'
            }`}
          >
            <div className="overflow-y-auto h-full max-h-[680px]">{renderList()}</div>
          </div>

          <div className={`lg:col-span-3 ${!selected ? 'hidden lg:block' : ''}`}>
            {renderViewer()}
          </div>
        </div>
      </div>

      <Modal
        isOpen={composeOpen}
        onClose={() => setComposeOpen(false)}
        title="Compose Message"
        description="Send a new message to a supported audience."
        size="lg"
        actions={
          <>
            <Button variant="secondary" onClick={() => setComposeOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={handleSend} loading={saving}>
              <SendIcon size={14} /> Send
            </Button>
          </>
        }
      >
        <div className="space-y-4">
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
          {activeAudience && (
            <p className="text-[14px] text-neutral-500 leading-relaxed">{activeAudience.hint}</p>
          )}
        </div>
      </Modal>
    </PageContainer>
  );
}