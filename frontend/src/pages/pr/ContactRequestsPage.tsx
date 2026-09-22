import { useMemo, useState } from 'react';
import { contactRequestApi } from '../../api/api';
import type { ContactRequest } from '../../types';
import {
  Badge,
  Button,
  DataTable,
  FilterToolbar,
  Modal,
  PageContainer,
  PageHeader,
  Pagination,
  SearchInput,
  Select,
  Tabs,
  Avatar,
  formatStatus,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  notify,
} from '../../components/ui';
import { usePaginatedData } from '../../hooks/useApi';
import { Eye, MessageSquare } from 'lucide-react';

type Tab = 'incoming' | 'mine';
type StatusFilter = 'ALL' | 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'RESOLVED';

const STATUS_OPTIONS = [
  { label: 'All Statuses', value: 'ALL' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Accepted', value: 'ACCEPTED' },
  { label: 'Rejected', value: 'REJECTED' },
  { label: 'Resolved', value: 'RESOLVED' },
];

const statusVariant = (s: string): 'warning' | 'success' | 'danger' | 'info' | 'neutral' => {
  switch (s) {
    case 'PENDING': return 'warning';
    case 'ACCEPTED': return 'success';
    case 'REJECTED': return 'danger';
    case 'RESOLVED': return 'info';
    default: return 'neutral';
  }
};

interface ConfirmState {
  request: ContactRequest;
  action: 'ACCEPTED' | 'REJECTED' | 'RESOLVED';
}

export default function ContactRequestsPage() {
  const [tab, setTab] = useState<Tab>('incoming');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [detail, setDetail] = useState<ContactRequest | null>(null);
  const [confirm, setConfirm] = useState<ConfirmState | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

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

  const isNoProfile = !!error && /student profile/i.test(error);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return requests.filter((r) => {
      if (statusFilter !== 'ALL' && r.status !== statusFilter) return false;
      if (!q) return true;
      return (
        r.studentName.toLowerCase().includes(q) ||
        r.registerNumber.toLowerCase().includes(q) ||
        r.subject.toLowerCase().includes(q) ||
        r.message.toLowerCase().includes(q) ||
        (r.targetUserName || '').toLowerCase().includes(q)
      );
    });
  }, [requests, search, statusFilter]);

  const handleStatusUpdate = async () => {
    if (!confirm) return;
    setActionLoading(true);
    try {
      await contactRequestApi.updateStatus(confirm.request.id, confirm.action);
      notify.success(`Request ${confirm.action.toLowerCase()}`);
      if (detail && detail.id === confirm.request.id) {
        setDetail((prev) => (prev ? { ...prev, status: confirm.action } : prev));
      }
      setConfirm(null);
      refresh();
    } catch {
      notify.error('Failed to update request');
    } finally {
      setActionLoading(false);
    }
  };

  const openConfirm = (request: ContactRequest, action: 'ACCEPTED' | 'REJECTED' | 'RESOLVED') => {
    setDetail(null);
    setConfirm({ request, action });
  };

  const columns = [
    {
      key: 'studentName',
      label: 'Student',
      render: (r: ContactRequest) => (
        <div className="flex items-center gap-3">
          <Avatar name={r.studentName || 'U'} size="sm" />
          <div>
            <p className="text-[15px] font-medium text-neutral-900">{r.studentName}</p>
            <p className="text-[14px] text-neutral-500">{r.registerNumber}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'departmentName',
      label: 'Department',
      render: (r: ContactRequest) =>
        r.departmentName ? (
          <Badge variant="department">{r.departmentName}</Badge>
        ) : (
          <span className="text-[14px] text-neutral-500">—</span>
        ),
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
        <Badge variant={statusVariant(r.status)} dot={true}>
          {formatStatus(r.status)}
        </Badge>
      ),
    },
    {
      key: 'createdAt',
      label: 'Created',
      render: (r: ContactRequest) => (
        <span className="text-[14px] text-neutral-500">
          {new Date(r.createdAt).toLocaleDateString()}
        </span>
      ),
    },
    ...(tab === 'incoming'
      ? [
          {
            key: 'actions',
            label: '',
            className: 'w-48',
            render: (r: ContactRequest) => (
              <div className="flex gap-2 justify-end">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={(e) => {
                    e.stopPropagation();
                    setDetail(r);
                  }}
                >
                  <Eye size={13} /> View
                </Button>
                {r.status === 'PENDING' && (
                  <>
                    <Button
                      size="sm"
                      variant="secondary"
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
                      onClick={(e) => {
                        e.stopPropagation();
                        openConfirm(r, 'REJECTED');
                      }}
                    >
                      Reject
                    </Button>
                  </>
                )}
                {r.status === 'ACCEPTED' && (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={(e) => {
                      e.stopPropagation();
                      openConfirm(r, 'RESOLVED');
                    }}
                  >
                    Resolve
                  </Button>
                )}
              </div>
            ),
          },
        ]
      : []),
  ];

  const renderDetailActions = () => {
    if (!detail || tab !== 'incoming') return null;
    if (detail.status === 'PENDING') {
      return (
        <>
          <Button
            variant="secondary"
            onClick={() => openConfirm(detail, 'ACCEPTED')}
          >
            Accept
          </Button>
          <Button
            variant="outline-danger"
            onClick={() => openConfirm(detail, 'REJECTED')}
          >
            Reject
          </Button>
        </>
      );
    }
    if (detail.status === 'ACCEPTED') {
      return (
        <Button variant="secondary" onClick={() => openConfirm(detail, 'RESOLVED')}>
          Resolve
        </Button>
      );
    }
    return null;
  };

  const getConfirmMessage = () => {
    if (!confirm) return '';
    const name = confirm.request.studentName;
    switch (confirm.action) {
      case 'ACCEPTED':
        return `Accept the contact request from "${name}"? You will be expected to follow up.`;
      case 'REJECTED':
        return `Reject the contact request from "${name}"? This cannot be undone.`;
      case 'RESOLVED':
        return `Mark the contact request from "${name}" as resolved?`;
    }
  };

  const renderTableBody = () => {
    if (error && !isNoProfile) {
      return <ErrorState message={error} onRetry={refresh} />;
    }
    if (isNoProfile) {
      return (
        <EmptyState
          icon={<MessageSquare size={40} />}
          title="No requests to show"
          description="This account has no student profile, so it cannot create contact requests. Incoming requests still appear in the Incoming tab."
        />
      );
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
          onRowClick={(r) => setDetail(r)}
          emptyMessage={tab === 'incoming' ? 'No incoming requests' : 'No requests sent yet'}
          emptyIcon={<MessageSquare size={40} />}
        />
        {!loading && totalPages > 1 && (
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
        description="Review and respond to student contact requests."
      />

      <Tabs<Tab>
        tabs={[
          { key: 'incoming', label: 'Incoming Requests' },
          { key: 'mine', label: 'My Requests' },
        ]}
        active={tab}
        onChange={(t) => {
          setTab(t);
          setPage(0);
          setSearch('');
          setStatusFilter('ALL');
        }}
      />

      {!error && (
        <FilterToolbar
          search={
            <SearchInput
              placeholder="Search by student, register number, subject..."
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
            />
          }
        />
      )}

      {renderTableBody()}

      <Modal
        isOpen={!!detail}
        onClose={() => setDetail(null)}
        title={detail?.subject || 'Contact Request'}
        description={
          detail ? `Requested ${new Date(detail.createdAt).toLocaleString()}` : undefined
        }
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
            <div className="flex items-center gap-2">
              <Badge variant={statusVariant(detail.status)} dot={true}>
                {formatStatus(detail.status)}
              </Badge>
              {detail.resolvedAt && (
                <span className="text-[13px] text-neutral-500">
                  Resolved {new Date(detail.resolvedAt).toLocaleString()}
                </span>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="bg-neutral-50 border border-neutral-100 rounded-[10px] p-3.5">
                <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">
                  From
                </p>
                <p className="text-[14px] font-medium text-neutral-900">{detail.studentName}</p>
                <p className="text-[13px] text-neutral-500">{detail.registerNumber}</p>
                {detail.departmentName && (
                  <p className="text-[13px] text-neutral-500">{detail.departmentName}</p>
                )}
              </div>
              <div className="bg-neutral-50 border border-neutral-100 rounded-[10px] p-3.5">
                <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">
                  Requested Recipient
                </p>
                <p className="text-[14px] font-medium text-neutral-900">{detail.targetUserName}</p>
              </div>
            </div>

            <div>
              <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500 mb-1.5">
                Subject
              </p>
              <p className="text-[15px] font-medium text-neutral-800">{detail.subject}</p>
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

      <ConfirmDialog
        isOpen={!!confirm}
        onClose={() => setConfirm(null)}
        onConfirm={handleStatusUpdate}
        title={`${confirm?.action === 'ACCEPTED' ? 'Accept' : confirm?.action === 'REJECTED' ? 'Reject' : 'Resolve'} Request`}
        message={getConfirmMessage()}
        confirmLabel={
          confirm?.action === 'ACCEPTED'
            ? 'Accept'
            : confirm?.action === 'REJECTED'
            ? 'Reject'
            : 'Resolve'
        }
        variant={confirm?.action === 'REJECTED' ? 'danger' : 'primary'}
        loading={actionLoading}
      />
    </PageContainer>
  );
}