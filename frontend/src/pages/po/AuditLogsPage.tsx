import { useState, useCallback, useEffect } from 'react';
import { auditLogApi } from '../../api/api';
import { DataTable, Pagination, Badge, PageHeader, PageContainer, ErrorState } from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { Activity } from 'lucide-react';

interface AuditLog {
  id: number;
  userEmail: string;
  action: string;
  entityType: string;
  entityId: number;
  details: string;
  createdAt: string;
  timestamp: string;
}

const actionVariantMap: Record<string, 'success' | 'danger' | 'warning' | 'info' | 'neutral'> = {
  CREATE: 'success',
  UPDATE: 'info',
  DELETE: 'danger',
  LOGIN: 'neutral',
  LOGOUT: 'neutral',
  PROMOTE: 'success',
  DEMOTE: 'warning',
  ASSIGN: 'info',
};

export default function AuditLogsPage() {
  const [page, setPage] = useState(0);
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await auditLogApi.getAll({ page, size: 20 });
      const payload = res.data.data;
      if (Array.isArray(payload)) {
        setLogs(payload);
        setTotalPages(1);
        setTotalElements(payload.length);
      } else if (payload && typeof payload === 'object' && 'content' in payload) {
        setLogs(payload.content);
        setTotalPages(payload.totalPages);
        setTotalElements(payload.totalElements);
      }
    } catch (err) {
      setError(getErrorMessage(err));
      setLogs([]);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  const formatTimestamp = (log: AuditLog) => {
    const ts = log.createdAt || log.timestamp;
    if (!ts) return '—';
    return new Date(ts).toLocaleString();
  };

  const getActionBadge = (action: string) => {
    const upperAction = action?.toUpperCase() || '';
    const variant = actionVariantMap[upperAction] || 'neutral';
    return <Badge variant={variant} dot>{action}</Badge>;
  };

  return (
    <PageContainer>
      <PageHeader title="Audit Logs" description="Track system activity and changes." />

      {error ? (
        <ErrorState title="Unable to load audit logs" message={error} onRetry={fetchLogs} />
      ) : (
        <>
          <DataTable<AuditLog>
            columns={[
              {
                key: 'createdAt',
                label: 'Timestamp',
                render: (l) => (
                  <span className="text-[15px] text-neutral-500 whitespace-nowrap">{formatTimestamp(l)}</span>
                ),
              },
              {
                key: 'userEmail',
                label: 'User',
                render: (l) => (
                  <span className="text-[15px] font-medium text-neutral-900">{l.userEmail || '—'}</span>
                ),
              },
              {
                key: 'action',
                label: 'Action',
                render: (l) => getActionBadge(l.action),
              },
              {
                key: 'entityType',
                label: 'Entity Type',
                render: (l) => (
                  <span className="text-[15px] text-neutral-600">{l.entityType || '—'}</span>
                ),
              },
              {
                key: 'details',
                label: 'Details',
                render: (l) => (
                  <span className="text-[15px] text-neutral-500 max-w-xs truncate block" title={l.details}>
                    {l.details || l.entityId ? `${l.entityType || ''} #${l.entityId || ''}` : '—'}
                  </span>
                ),
              },
            ]}
            data={logs}
            rowKey={(l) => l.id}
            loading={loading}
            emptyMessage="No audit logs found"
            emptyIcon={<Activity size={40} />}
          />
          <div className="mt-6">
            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={20}
              onPageChange={setPage}
            />
          </div>
        </>
      )}
    </PageContainer>
  );
}
