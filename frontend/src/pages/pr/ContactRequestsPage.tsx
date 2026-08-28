import { useState } from 'react';
import { contactRequestApi } from '../../api/api';
import type { ContactRequest } from '../../types';
import {
  Badge,
  Button,
  DataTable,
  Pagination,
  PageHeader,
  PageContainer,
  Tabs,
  ConfirmDialog,
  notify,
} from '../../components/ui';
import { usePaginatedData } from '../../hooks/useApi';
import { MessageSquare } from 'lucide-react';

type Tab = 'incoming' | 'mine';

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
  const [confirm, setConfirm] = useState<ConfirmState | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const url = tab === 'incoming' ? '/contact-requests/incoming' : '/contact-requests/mine';

  const {
    data: requests,
    loading,
    page,
    totalPages,
    totalElements,
    setPage,
    refresh,
  } = usePaginatedData<ContactRequest>({ url });

  const handleStatusUpdate = async () => {
    if (!confirm) return;
    setActionLoading(true);
    try {
      await contactRequestApi.updateStatus(confirm.request.id, confirm.action);
      notify.success(`Request ${confirm.action.toLowerCase()}`);
      setConfirm(null);
      refresh();
    } catch {
      notify.error('Failed to update request');
    } finally {
      setActionLoading(false);
    }
  };

  const columns = [
    {
      key: 'studentName',
      label: 'Student',
      render: (r: ContactRequest) => (
        <div>
          <p className="text-[15px] font-medium text-neutral-900">{r.studentName}</p>
          <p className="text-[14px] text-neutral-500">{r.registerNumber}</p>
        </div>
      ),
    },
    {
      key: 'departmentName',
      label: 'Department',
      render: (r: ContactRequest) => (
        r.departmentName ? (
          <Badge variant="department">{r.departmentName}</Badge>
        ) : (
          <span className="text-[14px] text-neutral-400">—</span>
        )
      ),
    },
    {
      key: 'subject',
      label: 'Subject',
      render: (r: ContactRequest) => (
        <span className="text-[14px] font-medium text-neutral-900">{r.subject}</span>
      ),
    },
    {
      key: 'status',
      label: 'Status',
      render: (r: ContactRequest) => (
        <Badge variant={statusVariant(r.status)} dot={true}>
          {r.status.charAt(0) + r.status.slice(1).toLowerCase()}
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
            className: 'w-44',
            render: (r: ContactRequest) => (
              <div className="flex gap-2">
                {r.status === 'PENDING' && (
                  <>
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={(e) => {
                        e.stopPropagation();
                        setConfirm({ request: r, action: 'ACCEPTED' });
                      }}
                    >
                      Accept
                    </Button>
                    <Button
                      size="sm"
                      variant="outline-danger"
                      onClick={(e) => {
                        e.stopPropagation();
                        setConfirm({ request: r, action: 'REJECTED' });
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
                      setConfirm({ request: r, action: 'RESOLVED' });
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

  return (
    <PageContainer>
      <PageHeader
        title="Contact Requests"
        description="Manage student contact requests."
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
        }}
      />

      <DataTable<ContactRequest>
        columns={columns}
        data={requests}
        rowKey={(r) => r.id}
        loading={loading}
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
