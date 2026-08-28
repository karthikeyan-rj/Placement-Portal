import { useState, useEffect } from 'react';
import { messageApi, userApi } from '../../api/api';
import type { Message, User } from '../../types';
import {
  Button,
  Input,
  Select,
  Textarea,
  Badge,
  Modal,
  Pagination,
  Skeleton,
  EmptyState,
  PageHeader,
  PageContainer,
  notify,
} from '../../components/ui';
import { usePaginatedData } from '../../hooks/useApi';
import {
  ThumbsUp,
  ThumbsDown,
  Inbox,
  Send as SendIcon,
  Mail,
  MailOpen,
  X,
  MessageSquare,
} from 'lucide-react';

type Tab = 'inbox' | 'sent';

const MESSAGE_TYPES = [
  { label: 'General', value: 'GENERAL' },
  { label: 'Direct', value: 'DIRECT' },
  { label: 'Announcement', value: 'ANNOUNCEMENT' },
];

interface ComposeForm {
  title: string;
  content: string;
  messageType: string;
  recipientIds: number[];
}

const emptyCompose: ComposeForm = {
  title: '',
  content: '',
  messageType: 'GENERAL',
  recipientIds: [],
};

export default function MessagesPage() {
  const [tab, setTab] = useState<Tab>('inbox');
  const [selected, setSelected] = useState<Message | null>(null);
  const [composeOpen, setComposeOpen] = useState(false);
  const [form, setForm] = useState<ComposeForm>(emptyCompose);
  const [saving, setSaving] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);

  const url = tab === 'inbox' ? '/messages/received' : '/messages/sent';

  const {
    data: messages,
    loading,
    page,
    totalPages,
    totalElements,
    setPage,
    refresh,
  } = usePaginatedData<Message>({ url });

  useEffect(() => {
    setUsersLoading(true);
    userApi
      .getAll({ size: 500 })
      .then((res) => {
        const d = res.data?.data;
        setUsers(d?.content ?? (Array.isArray(d) ? d : []));
      })
      .catch(() => {})
      .finally(() => setUsersLoading(false));
  }, []);

  const handleSend = async () => {
    if (!form.title.trim() || !form.content.trim()) {
      notify.error('Title and content are required');
      return;
    }
    if (form.recipientIds.length === 0) {
      notify.error('Select at least one recipient');
      return;
    }
    setSaving(true);
    try {
      await messageApi.send({
        title: form.title,
        content: form.content,
        messageType: form.messageType,
        recipientIds: form.recipientIds,
      });
      notify.success('Message sent successfully');
      setComposeOpen(false);
      setForm(emptyCompose);
      refresh();
    } catch {
      notify.error('Failed to send message');
    } finally {
      setSaving(false);
    }
  };

  const handleVote = async (msg: Message, type: 'upvote' | 'downvote') => {
    try {
      if (type === 'upvote') await messageApi.upvote(msg.id);
      else await messageApi.downvote(msg.id);
      notify.success(type === 'upvote' ? 'Upvoted' : 'Downvoted');
      refresh();
      if (selected?.id === msg.id) {
        setSelected((prev) =>
          prev
            ? {
                ...prev,
                upvoteCount: type === 'upvote' ? prev.upvoteCount + 1 : prev.upvoteCount,
                downvoteCount: type === 'downvote' ? prev.downvoteCount + 1 : prev.downvoteCount,
              }
            : null
        );
      }
    } catch {
      notify.error('Failed to record vote');
    }
  };

  const handleMarkRead = async (msg: Message) => {
    if (tab === 'inbox') {
      try {
        await messageApi.markAsRead(msg.id);
      } catch {
        // silent
      }
    }
  };

  const toggleRecipient = (userId: number) => {
    setForm((prev) => ({
      ...prev,
      recipientIds: prev.recipientIds.includes(userId)
        ? prev.recipientIds.filter((id) => id !== userId)
        : [...prev.recipientIds, userId],
    }));
  };

  const renderMessageList = () => {
    if (loading) {
      return (
        <div className="space-y-2 p-4">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="rounded-xl border border-neutral-200/80 p-4">
              <Skeleton className="h-4 w-3/4 mb-2" />
              <Skeleton className="h-3 w-1/2 mb-2" />
              <Skeleton className="h-3 w-1/4" />
            </div>
          ))}
        </div>
      );
    }

    if (messages.length === 0) {
      return (
        <div className="py-16">
          <EmptyState
            icon={tab === 'inbox' ? <Inbox size={40} /> : <SendIcon size={40} />}
            title={tab === 'inbox' ? 'No messages in inbox' : 'No sent messages'}
            description={
              tab === 'inbox'
                ? 'You have no received messages yet.'
                : "You haven't sent any messages yet."
            }
          />
        </div>
      );
    }

    return (
      <div className="divide-y divide-neutral-100">
        {messages.map((msg) => {
          const isActive = selected?.id === msg.id;
          const isUnread = tab === 'inbox' && msg.readCount === 0;
          return (
            <button
              key={msg.id}
              onClick={() => {
                setSelected(msg);
                handleMarkRead(msg);
              }}
              className={`w-full text-left px-5 py-4 transition-colors ${
                isActive
                  ? 'bg-primary-50/60'
                  : 'hover:bg-neutral-50'
              }`}
            >
              <div className="flex items-start justify-between mb-1">
                <div className="flex items-center gap-2 min-w-0">
                  {isUnread && (
                    <span className="w-2 h-2 rounded-full bg-primary-500 shrink-0" />
                  )}
                  {!isUnread && (
                    tab === 'inbox'
                      ? <Mail size={14} className="text-neutral-300 shrink-0" />
                      : <MailOpen size={14} className="text-neutral-300 shrink-0" />
                  )}
                  <span className={`text-[15px] truncate ${isUnread ? 'font-semibold text-neutral-900' : 'font-medium text-neutral-800'}`}>
                    {msg.title}
                  </span>
                </div>
                <div className="flex items-center gap-2 shrink-0 ml-2">
                  <span className="flex items-center gap-0.5 text-[15px] text-neutral-400">
                    <ThumbsUp size={10} /> {msg.upvoteCount}
                  </span>
                  <span className="flex items-center gap-0.5 text-[15px] text-neutral-400">
                    <ThumbsDown size={10} /> {msg.downvoteCount}
                  </span>
                </div>
              </div>
              <p className="text-[15px] text-neutral-500 ml-4">
                {tab === 'inbox' ? `From: ${msg.senderName}` : `To: ${msg.totalRecipients} recipients`}
              </p>
              <span className="text-[15px] text-neutral-400 ml-4">
                {new Date(msg.createdAt).toLocaleDateString()}
              </span>
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

  const renderMessageViewer = () => {
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

    return (
      <div className="h-full flex flex-col">
        <div className="px-6 py-4 border-b border-neutral-100">
          <div className="flex items-start justify-between mb-2">
            <div className="min-w-0 flex-1">
              <h2 className="text-[15px] font-semibold text-neutral-900 truncate">
                {selected.title}
              </h2>
              <p className="text-[15px] text-neutral-500 mt-1">
                {tab === 'inbox' ? `From ${selected.senderName}` : `By you`} ({selected.senderRole}) &middot;{' '}
                {new Date(selected.createdAt).toLocaleString()}
              </p>
            </div>
            <button
              onClick={() => setSelected(null)}
              className="p-1.5 rounded-lg text-neutral-400 hover:text-neutral-600 hover:bg-neutral-100 transition-colors shrink-0"
            >
              <X size={16} />
            </button>
          </div>

          <div className="flex items-center gap-2">
            {selected.messageType && (
              <Badge variant="info" size="sm">
                {selected.messageType}
              </Badge>
            )}
          </div>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto px-6 py-5">
          <p className="text-[15px] text-neutral-700 whitespace-pre-wrap leading-relaxed">
            {selected.content}
          </p>
        </div>

        <div className="border-t border-neutral-100 px-6 py-4">
          <p className="text-[15px] font-medium text-neutral-400 uppercase tracking-wider mb-3">
            Delivery Stats
          </p>
          <div className="grid grid-cols-3 gap-3">
            <div className="bg-neutral-50 rounded-lg p-3 text-center">
              <div className="text-[17px] font-semibold text-neutral-900">
                {selected.totalRecipients}
              </div>
              <div className="text-[15px] text-neutral-500">Recipients</div>
            </div>
            <div className="bg-neutral-50 rounded-lg p-3 text-center">
              <div className="text-[17px] font-semibold text-green-600">
                {selected.deliveredCount}
              </div>
              <div className="text-[15px] text-neutral-500">Delivered</div>
            </div>
            <div className="bg-neutral-50 rounded-lg p-3 text-center">
              <div className="text-[17px] font-semibold text-primary-600">
                {selected.readCount}
              </div>
              <div className="text-[15px] text-neutral-500">Read</div>
            </div>
          </div>

          <div className="flex gap-2 mt-4">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => handleVote(selected, 'upvote')}
            >
              <ThumbsUp size={13} /> Upvote ({selected.upvoteCount})
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => handleVote(selected, 'downvote')}
            >
              <ThumbsDown size={13} /> Downvote ({selected.downvoteCount})
            </Button>
          </div>
        </div>
      </div>
    );
  };

  return (
    <PageContainer>
      <PageHeader
        title="Messages"
        description="Send and receive messages to students and faculty."
        actions={
          <Button
            onClick={() => {
              setForm(emptyCompose);
              setComposeOpen(true);
            }}
            size="md"
          >
            <SendIcon size={15} />
            New Message
          </Button>
        }
      />

      <div className="flex gap-1.5 mb-4">
        {(['inbox', 'sent'] as Tab[]).map((t) => (
          <button
            key={t}
            onClick={() => {
              setTab(t);
              setPage(0);
              setSelected(null);
            }}
            className={`px-4 py-2 -mb-px border-b-2 text-[14px] font-medium transition-colors ${
              tab === t
                ? 'border-primary-500 text-primary-700'
                : 'border-transparent text-neutral-500 hover:text-neutral-700'
            }`}
          >
            {t === 'inbox' ? 'Inbox' : 'Sent'}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card">
        <div className="grid grid-cols-1 lg:grid-cols-5 min-h-[600px]">
          <div className={`lg:col-span-2 border-r border-neutral-100 ${selected ? 'hidden lg:block' : 'lg:col-span-5'}`}>
            <div className="overflow-y-auto h-full max-h-[680px]">
              {renderMessageList()}
            </div>
          </div>

          <div className={`lg:col-span-3 ${!selected ? 'hidden lg:block' : ''}`}>
            {renderMessageViewer()}
          </div>
        </div>
      </div>

      <Modal
        isOpen={composeOpen}
        onClose={() => setComposeOpen(false)}
        title="Compose Message"
        description="Send a new message to selected recipients."
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
            options={MESSAGE_TYPES}
            value={form.messageType}
            onChange={(e) => setForm({ ...form, messageType: e.target.value })}
          />
          <div>
            <label className="text-[15px] font-medium text-neutral-700 mb-2 block">
              Recipients <span className="text-danger-500 ml-0.5">*</span>
            </label>
            {usersLoading ? (
              <Skeleton className="h-20 w-full" />
            ) : (
              <div className="border border-neutral-200/80 rounded-lg max-h-48 overflow-y-auto">
                {users.length === 0 ? (
                  <p className="p-4 text-[15px] text-neutral-500 text-center">No users available</p>
                ) : (
                  users.map((user) => (
                    <label
                      key={user.id}
                      className={`flex items-center gap-3 px-4 py-2.5 text-[15px] cursor-pointer transition-colors ${
                        form.recipientIds.includes(user.id)
                          ? 'bg-primary-50/60'
                          : 'hover:bg-neutral-50'
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={form.recipientIds.includes(user.id)}
                        onChange={() => toggleRecipient(user.id)}
                        className="rounded border-neutral-300 text-primary-600 focus:ring-primary-500"
                      />
                      <div className="min-w-0 flex-1">
                        <span className="font-medium text-neutral-900">{user.name}</span>
                        <span className="text-neutral-400 mx-1.5">&middot;</span>
                        <span className="text-neutral-500">{user.email}</span>
                      </div>
                      {user.role !== 'STUDENT' && (
                        <Badge variant="info" size="sm">
                          {user.role}
                        </Badge>
                      )}
                    </label>
                  ))
                )}
              </div>
            )}
            {form.recipientIds.length > 0 && (
              <p className="text-[15px] text-neutral-500 mt-1.5">
                {form.recipientIds.length} recipient{form.recipientIds.length !== 1 ? 's' : ''} selected
              </p>
            )}
          </div>
        </div>
      </Modal>
    </PageContainer>
  );
}
