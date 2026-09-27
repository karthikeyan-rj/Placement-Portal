import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { contactRequestApi } from '../../api/api';
import type { ContactRequest, ContactRequestCounts, ContactRequestTarget } from '../../types';
import {
  Badge,
  Button,
  DataTable,
  FilterToolbar,
  Input,
  Modal,
  PageContainer,
  PageHeader,
  Pagination,
  SearchInput,
  Select,
  Tabs,
  Avatar,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Textarea,
  notify,
} from '../../components/ui';
import { usePaginatedData } from '../../hooks/useApi';
import { useAuth } from '../../context/AuthContext';
import { useNotifications } from '../../context/NotificationsContext';
import { Mail, MessageSquare, Send } from 'lucide-react';

type Tab = 'incoming' | 'mine';
type StatusFilter = 'ALL' | 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'RESOLVED';
type Action = 'ACCEPTED' | 'REJECTED' | 'RESOLVED';

/** Statuses that establish the relationship and therefore allow messaging. */
const MESSAGING_STATUSES = new Set(['ACCEPTED', 'RESOLVED']);

/**
 * REJECTED is stored under its own name in the database and API; it is only
 * ever presented to users as "Declined".
 */
const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  REJECTED: 'Declined',
  RESOLVED: 'Resolved',
};

const STATUS_OPTIONS = [
  { label: 'All Statuses', value: 'ALL' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Accepted', value: 'ACCEPTED' },
  { label: 'Declined', value: 'REJECTED' },
  { label: 'Resolved', value: 'RESOLVED' },
];

const statusLabel = (status: string): string => STATUS_LABELS[status] || status;

const statusVariant = (s: string): 'warning' | 'success' | 'danger' | 'info' | 'neutral' => {
  switch (s) {
    case 'PENDING': return 'warning';
    case 'ACCEPTED': return 'success';
    case 'REJECTED': return 'danger';
    case 'RESOLVED': return 'info';
    default: return 'neutral';
  }
};

function relativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';
  const minutes = Math.round((Date.now() - then) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} hr${hours === 1 ? '' : 's'} ago`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

interface ConfirmState {
  request: ContactRequest;
  action: Action;
}

export default function ContactRequestsPage() {
  const { user } = useAuth();
  const { contactRequestSignal } = useNotifications();
  const navigate = useNavigate();

  const role = user?.role;
  // Coordinators receive requests; students and representatives send them.
  const isRequester = role === 'STUDENT' || role === 'PR';

  const [tab, setTab] = useState<Tab>(isRequester ? 'mine' : 'incoming');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [detail, setDetail] = useState<ContactRequest | null>(null);
  const [confirm, setConfirm] = useState<ConfirmState | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const [createOpen, setCreateOpen] = useState(false);
  const [createTarget, setCreateTarget] = useState('');
  const [createSubject, setCreateSubject] = useState('');
  const [createMessage, setCreateMessage] = useState('');
  const [createTargets, setCreateTargets] = useState<ContactRequestTarget[]>([]);
  const [createTargetsLoading, setCreateTargetsLoading] = useState(false);
  const [createSaving, setCreateSaving] = useState(false);
  const [createError, setCreateError] = useState('');

  const [counts, setCounts] = useState<ContactRequestCounts | null>(null);

  const url = tab === 'incoming' ? '/contact-requests/incoming' : '/contact-requests/mine';

  const {
    data: requests,
    loading,
    error,
    page,
    totalPages,
    totalElements,
    setPage,
    refresh,
  } = usePaginatedData<ContactRequest>({ url });

  // A coordinator has no outgoing requests and a requester has no incoming
  // ones, so only the tab that can actually return rows is shown.
  const tabs = useMemo(() => {
    const list: { key: Tab; label: string }[] = [];
    if (!isRequester) list.push({ key: 'incoming', label: 'Incoming Requests' });
    if (isRequester) list.push({ key: 'mine', label: 'Sent Requests' });
    return list;
  }, [isRequester]);

  const loadCounts = useCallback(() => {
    if (!user) return;
    contactRequestApi
      .getCounts(tab)
      .then((res) => setCounts((res.data?.data as ContactRequestCounts) ?? null))
      .catch(() => setCounts(null));
  }, [tab, user]);

  useEffect(() => {
    loadCounts();
  }, [loadCounts]);

  // Live updates: a CONTACT_REQUEST_* event only tells us something changed, so
  // the authorized list and counters are simply re-read. No unread state here.
  useEffect(() => {
    if (!contactRequestSignal) return;
    refresh();
    loadCounts();
  }, [contactRequestSignal, refresh, loadCounts]);

  const openCreate = async () => {
    setCreateOpen(true);
    setCreateError('');
    setCreateTarget('');
    setCreateSubject('');
    setCreateMessage('');
    setCreateTargetsLoading(true);
    try {
      const res = await contactRequestApi.getTargets();
      setCreateTargets((res.data?.data as ContactRequestTarget[]) ?? []);
    } catch {
      setCreateTargets([]);
      setCreateError('Could not load the coordinator list. Please try again.');
    } finally {
      setCreateTargetsLoading(false);
    }
  };

  const submitCreate = async () => {
    if (!createTarget) {
      setCreateError('Choose a placement coordinator.');
      return;
    }
    if (!createSubject.trim() || !createMessage.trim()) {
      setCreateError('Subject and message are required.');
      return;
    }
    setCreateSaving(true);
    setCreateError('');
    try {
      await contactRequestApi.create({
        targetUserId: Number(createTarget),
        subject: createSubject.trim(),
        message: createMessage.trim(),
      });
      notify.success('Contact request sent');
      setCreateOpen(false);
      setPage(0);
      refresh();
      loadCounts();
    } catch (err) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Could not send the request.';
      setCreateError(message);
    } finally {
      setCreateSaving(false);
    }
  };

  const handleStatusUpdate = async () => {
    if (!confirm) return;
    setActionLoading(true);
    try {
      await contactRequestApi.updateStatus(confirm.request.id, confirm.action);
      notify.success(`Request ${statusLabel(confirm.action).toLowerCase()}`);
      if (detail && detail.id === confirm.request.id) {
        setDetail((prev) => (prev ? { ...prev, status: confirm.action } : prev));
      }
      setConfirm(null);
      refresh();
      loadCounts();
    } catch (err) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to update request';
      notify.error(message);
      refresh();
    } finally {
      setActionLoading(false);
    }
  };

  const openConfirm = (request: ContactRequest, action: Action) => {
    setDetail(null);
    setConfirm({ request, action });
  };

  // Hands the participant to the composer with only an id and a display name;
  // the recipient is re-authorized by the backend on send.
  const goToMessage = useCallback((counterpartId: number, counterpartName: string) => {
    if (!counterpartId) return;
    navigate('/messages', { state: { contactRecipient: { id: counterpartId, name: counterpartName } } });
  }, [navigate]);

  const canMessage = (r: ContactRequest): boolean => MESSAGING_STATUSES.has(r.status);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return requests.filter((r) => {
      if (statusFilter !== 'ALL' && r.status !== statusFilter) return false;
      if (!q) return true;
      return (
        r.studentName.toLowerCase().includes(q) ||
        (r.registerNumber || '').toLowerCase().includes(q) ||
        r.subject.toLowerCase().includes(q) ||
        (r.targetUserName || '').toLowerCase().includes(q)
      );
    });
  }, [requests, search, statusFilter]);

  const columns = useMemo(() => {
    const base = [
      {
        key: 'studentName',
        label: tab === 'incoming' ? 'Requester' : 'Coordinator',
        render: (r: ContactRequest) => {
          const name = tab === 'incoming' ? r.studentName : r.targetUserName;
          return (
            <div className="flex items-center gap-3">
              <Avatar name={name || 'U'} size="sm" />
              <div className="min-w-0">
                <p className="text-[15px] font-medium text-neutral-900 truncate">{name}</p>
                <p className="text-[13px] text-neutral-500 truncate">
                  {tab === 'incoming' ? r.registerNumber || '' : 'Placement Coordinator'}
                </p>
              </div>
            </div>
          );
        },
      },
      {
        key: 'subject',
        label: 'Subject',
        render: (r: ContactRequest) => (
          <div className="max-w-[280px]">
            <p className="text-[14px] font-medium text-neutral-900 truncate">{r.subject}</p>
            <p className="text-[13px] text-neutral-500 truncate mt-0.5">{r.message}</p>
          </div>
        ),
      },
      {
        key: 'status',
        label: 'Status',
        render: (r: ContactRequest) => (
          <Badge variant={statusVariant(r.status)} size="sm" dot={true}>
            {statusLabel(r.status)}
          </Badge>
        ),
      },
      {
        key: 'createdAt',
        label: 'Requested',
        render: (r: ContactRequest) => (
          <span className="text-[13px] text-neutral-500 whitespace-nowrap">{relativeTime(r.createdAt)}</span>
        ),
      },
    ];

    if (tab !== 'incoming') return base;

    return [
      ...base,
      {
        key: 'actions',
        label: '',
        className: 'w-56',
        render: (r: ContactRequest) => (
          <div className="flex gap-1.5 justify-end flex-wrap">
            <Button
              size="sm"
              variant="secondary"
              aria-label={`View request from ${r.studentName}`}
              onClick={(e) => {
                e.stopPropagation();
                setDetail(r);
              }}
            >
              View
            </Button>
            {r.status === 'PENDING' && (
              <>
                <Button
                  size="sm"
                  aria-label={`Accept request from ${r.studentName}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    openConfirm(r, 'ACCEPTED');
                  }}
                >
                  Accept
                </Button>
                <Button
                  size="sm"
                  variant="outline-danger"
                  aria-label={`Decline request from ${r.studentName}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    openConfirm(r, 'REJECTED');
                  }}
                >
                  Decline
                </Button>
              </>
            )}
            {r.status === 'ACCEPTED' && (
              <Button
                size="sm"
                variant="secondary"
                aria-label={`Mark request from ${r.studentName} resolved`}
                onClick={(e) => {
                  e.stopPropagation();
                  openConfirm(r, 'RESOLVED');
                }}
              >
                Resolve
              </Button>
            )}
            {canMessage(r) && (
              <Button
                size="sm"
                variant="ghost"
                aria-label={`Message ${r.studentName}`}
                onClick={(e) => {
                  e.stopPropagation();
                  goToMessage(r.requesterUserId, r.studentName);
                }}
              >
                <MessageSquare size={13} /> Message
              </Button>
            )}
          </div>
        ),
      },
    ];
  }, [tab, goToMessage]);

  const renderDetailActions = () => {
    if (!detail || tab !== 'incoming') return null;
    return (
      <div className="flex flex-wrap gap-2">
        {detail.status === 'PENDING' && (
          <>
            <Button variant="secondary" onClick={() => openConfirm(detail, 'ACCEPTED')}>
              Accept
            </Button>
            <Button variant="outline-danger" onClick={() => openConfirm(detail, 'REJECTED')}>
              Decline
            </Button>
          </>
        )}
        {detail.status === 'ACCEPTED' && (
          <>
            <Button variant="secondary" onClick={() => openConfirm(detail, 'RESOLVED')}>
              Mark Resolved
            </Button>
            <Button
              variant="primary"
              onClick={() => goToMessage(detail.requesterUserId, detail.studentName)}
            >
              <MessageSquare size={14} /> Message {detail.studentName}
            </Button>
          </>
        )}
        {detail.status === 'RESOLVED' && (
          <Button
            variant="primary"
            onClick={() => goToMessage(detail.requesterUserId, detail.studentName)}
          >
            <MessageSquare size={14} /> Message {detail.studentName}
          </Button>
        )}
      </div>
    );
  };

  const getConfirmMessage = () => {
    if (!confirm) return '';
    const name = confirm.request.studentName;
    switch (confirm.action) {
      case 'ACCEPTED':
        return `Accept the request from ${name}? You will both be able to message each other directly.`;
      case 'REJECTED':
        return `Decline the request from ${name}? This cannot be undone and no messaging access is granted.`;
      case 'RESOLVED':
        return `Mark the request from ${name} as resolved? Direct messaging stays available.`;
    }
  };

  const renderEmpty = () => {
    if (isRequester) {
      return (
        <EmptyState
          icon={<Mail size={40} />}
          title="No contact requests yet"
          description="Need to speak with a placement coordinator? Send a request explaining what you need help with."
          action={
            <Button onClick={() => void openCreate()} size="md">
              <Send size={14} /> Request Contact
            </Button>
          }
        />
      );
    }
    return (
      <EmptyState
        icon={<MessageSquare size={40} />}
        title="No pending requests"
        description="New requests from eligible students or representatives will appear here."
      />
    );
  };

  const renderTableBody = () => {
    if (error) {
      return <ErrorState message={error} onRetry={refresh} />;
    }
    if (!loading && requests.length === 0) {
      return renderEmpty();
    }
    if (!loading && requests.length > 0 && filtered.length === 0) {
      return (
        <EmptyState
          icon={<MessageSquare size={40} />}
          title="No matches"
          description="No requests match your search or status filter."
        />
      );
    }
    return (
      <>
        <DataTable<ContactRequest>
          columns={columns}
          data={filtered}
          rowKey={(r) => r.id}
          loading={loading}
          density="compact"
          onRowClick={(r) => setDetail(r)}
          emptyMessage="No requests"
          emptyIcon={<MessageSquare size={40} />}
        />
        {totalPages > 1 && (
          <div className="mt-6">
            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={setPage}
            />
          </div>
        )}
      </>
    );
  };

  return (
    <PageContainer>
      <PageHeader
        title="Contact Requests"
        description="Request communication access and manage your requests."
        actions={
          isRequester ? (
            <Button onClick={() => void openCreate()} size="md">
              <Send size={14} /> Request Contact
            </Button>
          ) : undefined
        }
      />

      {counts && (
        <div className="flex flex-wrap gap-2 mb-5" role="status" aria-label="Contact request summary">
          {tab === 'incoming' && counts.pending > 0 && (
            <Badge variant="warning" size="sm" dot>
              {counts.pending} pending
            </Badge>
          )}
          <Badge variant="neutral" size="sm">
            {counts.accepted} accepted
          </Badge>
          <Badge variant="neutral" size="sm">
            {counts.rejected} declined
          </Badge>
          <Badge variant="neutral" size="sm">
            {counts.resolved} resolved
          </Badge>
        </div>
      )}

      {tabs.length > 1 && (
        <Tabs<Tab>
          tabs={tabs}
          active={tab}
          onChange={(t) => {
            setTab(t);
            setPage(0);
            setSearch('');
            setStatusFilter('ALL');
          }}
        />
      )}

      {!error && requests.length > 0 && (
        <FilterToolbar
          search={
            <SearchInput
              placeholder="Search by name, register number, subject..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          }
          filters={
            <Select
              options={STATUS_OPTIONS}
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
              className="w-44"
              aria-label="Filter by status"
            />
          }
        />
      )}

      {renderTableBody()}

      <Modal
        isOpen={!!detail}
        onClose={() => setDetail(null)}
        title={detail?.subject || 'Contact Request'}
        description={detail ? `Requested ${relativeTime(detail.createdAt)}` : undefined}
        size="md"
        actions={
          <>
            {renderDetailActions()}
            <Button variant="secondary" onClick={() => setDetail(null)}>
              Close
            </Button>
          </>
        }
      >
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2 flex-wrap">
              <Badge variant={statusVariant(detail.status)} size="sm" dot={true}>
                {statusLabel(detail.status)}
              </Badge>
              {detail.resolvedAt && (
                <span className="text-[13px] text-neutral-500">
                  {detail.status === 'REJECTED' ? 'Declined' : 'Resolved'}{' '}
                  {new Date(detail.resolvedAt).toLocaleString()}
                </span>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="bg-neutral-50 border border-neutral-100 rounded-[10px] p-3.5">
                <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">
                  From
                </p>
                <p className="text-[14px] font-medium text-neutral-900">{detail.studentName}</p>
                {detail.registerNumber && (
                  <p className="text-[13px] text-neutral-500">{detail.registerNumber}</p>
                )}
                {detail.departmentName && (
                  <p className="text-[13px] text-neutral-500">{detail.departmentName}</p>
                )}
              </div>
              <div className="bg-neutral-50 border border-neutral-100 rounded-[10px] p-3.5">
                <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">
                  Requested Recipient
                </p>
                <p className="text-[14px] font-medium text-neutral-900">{detail.targetUserName}</p>
                {tab === 'mine' && MESSAGING_STATUSES.has(detail.status) && (
                  <Button
                    size="sm"
                    variant="ghost"
                    className="mt-1.5 px-0"
                    onClick={() => goToMessage(detail.targetUserId, detail.targetUserName)}
                  >
                    <MessageSquare size={13} /> Send Message
                  </Button>
                )}
              </div>
            </div>

            <div>
              <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500 mb-1.5">
                Message
              </p>
              <p className="text-[15px] text-neutral-700 whitespace-pre-wrap leading-relaxed">
                {detail.message}
              </p>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        isOpen={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Request Contact"
        description="Send a placement coordinator a request explaining what you need."
        size="md"
        actions={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)} disabled={createSaving}>
              Cancel
            </Button>
            <Button onClick={() => void submitCreate()} loading={createSaving}>
              Send Request
            </Button>
          </>
        }
      >
        <div className="space-y-3.5">
          {createError && (
            <p role="alert" className="text-[13px] text-danger-600">
              {createError}
            </p>
          )}
          <Select
            label="Coordinator"
            required
            options={createTargets.map((t) => ({
              label: `${t.name} — Placement Coordinator${t.departmentName ? ` · ${t.departmentName}` : ''}`,
              value: String(t.id),
            }))}
            value={createTarget}
            onChange={(e) => setCreateTarget(e.target.value)}
            disabled={createTargetsLoading}
            placeholder={
              createTargetsLoading ? 'Loading coordinators...' : createTargets.length ? 'Select a coordinator' : 'No coordinators available'
            }
          />
          <Input
            label="Subject"
            required
            placeholder="Drive eligibility clarification"
            value={createSubject}
            onChange={(e) => setCreateSubject(e.target.value)}
          />
          <Textarea
            label="Message"
            required
            rows={5}
            placeholder="I need clarification regarding..."
            value={createMessage}
            onChange={(e) => setCreateMessage(e.target.value)}
          />
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!confirm}
        onClose={() => setConfirm(null)}
        onConfirm={handleStatusUpdate}
        title={`${statusLabel(confirm?.action ?? '')} Request`}
        message={getConfirmMessage()}
        confirmLabel={statusLabel(confirm?.action ?? '')}
        variant={confirm?.action === 'REJECTED' ? 'danger' : 'primary'}
        loading={actionLoading}
      />
    </PageContainer>
  );
}
