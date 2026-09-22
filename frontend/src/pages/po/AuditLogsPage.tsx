import { useState, useCallback, useEffect, useMemo } from 'react';
import { auditLogApi } from '../../api/api';
import {
  DataTable,
  Pagination,
  Badge,
  Button,
  Select,
  SearchInput,
  Avatar,
  Modal,
  PageHeader,
  PageContainer,
  EmptyState,
  ErrorState,
  FilterToolbar,
} from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { Activity, Eye, FilterX } from 'lucide-react';
import type { AuditLog } from '../../types';

const PAGE_SIZE = 25;
const FILTER_SCOPE = 1000;

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const SENSITIVE_KEY = /password|secret|token|access[i_-]?code|api[i_-]?key/i;
const SENSITIVE_TOKEN = /password|secret|token|jwt|accessCode|access_code|api[i_-]?key/i;

const WORD_OVERRIDES: Record<string, string> = {
  PO: 'PO',
  PC: 'PC',
  PR: 'PR',
  CGPA: 'CGPA',
};

function humanizeAction(action: string): string {
  if (!action) return '—';
  return action
    .split(/[_ ]+/)
    .filter(Boolean)
    .map((w) => {
      const up = w.toUpperCase();
      if (WORD_OVERRIDES[up]) return WORD_OVERRIDES[up];
      if (w === up && w.length > 1) return w.charAt(0) + w.slice(1).toLowerCase();
      return w.charAt(0).toUpperCase() + w.slice(1).toLowerCase();
    })
    .join(' ');
}

function actionVariant(action: string): 'success' | 'danger' | 'warning' | 'info' | 'neutral' {
  if (!action) return 'neutral';
  if (/^CREATE|^PROMOTE/.test(action)) return 'success';
  if (/^DEMOTE|^DEACTIVATE/.test(action)) return 'danger';
  if (/^UPDATE|^RENAME|^SET|^ASSIGN/i.test(action)) return 'info';
  return 'neutral';
}

function formatTimestamp(iso: string): string {
  if (!iso) return '—';
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso);
  if (m) {
    const year = Number(m[1]);
    const month = Number(m[2]);
    const day = Number(m[3]);
    const hour = Number(m[4]);
    const minute = m[5];
    const apm = hour >= 12 ? 'PM' : 'AM';
    const h12 = hour % 12 || 12;
    return `${String(day).padStart(2, '0')} ${MONTHS[month - 1]} ${year} · ${String(h12).padStart(2, '0')}:${minute} ${apm}`;
  }
  const dt = new Date(iso);
  if (Number.isNaN(dt.getTime())) return iso;
  const h = dt.getHours();
  const apm = h >= 12 ? 'PM' : 'AM';
  const h12 = h % 12 || 12;
  return `${String(dt.getDate()).padStart(2, '0')} ${MONTHS[dt.getMonth()]} ${dt.getFullYear()} · ${String(h12).padStart(2, '0')}:${String(dt.getMinutes()).padStart(2, '0')} ${apm}`;
}

interface DetailRow {
  label: string;
  value: string;
}

function parseDetails(details: string | null): DetailRow[] | null {
  if (!details) return null;
  const trimmed = details.trim();
  if (trimmed.startsWith('{')) {
    try {
      const parsed = JSON.parse(trimmed) as Record<string, unknown>;
      return Object.entries(parsed)
        .filter(([key]) => !SENSITIVE_KEY.test(key))
        .map(([key, value]) => ({ label: key, value: String(value ?? '') }));
    } catch {
      return null;
    }
  }
  const parts = trimmed.split('\n').filter(Boolean);
  if (parts.length > 1) {
    return parts.map((line) => {
      const idx = line.indexOf(':');
      if (idx > 0) return { label: line.slice(0, idx).trim(), value: line.slice(idx + 1).trim() };
      return { label: 'Detail', value: line };
    });
  }
  return null;
}

function sanitizeText(text: string | null): string {
  if (!text) return text ?? '';
  return text.replace(new RegExp(SENSITIVE_TOKEN.source, 'gi'), '[Redacted]');
}

export default function AuditLogsPage() {
  const [page, setPage] = useState(0);
  const [rawLogs, setRawLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [retryKey, setRetryKey] = useState(0);

  const [search, setSearch] = useState('');
  const [actionFilter, setActionFilter] = useState('');
  const [userFilter, setUserFilter] = useState('');
  const [selected, setSelected] = useState<AuditLog | null>(null);

  const hasFilters = search.trim() !== '' || actionFilter !== '' || userFilter !== '';

  const load = useCallback(async () => {
    try {
      const requestedPage = hasFilters ? 0 : page;
      const size = hasFilters ? FILTER_SCOPE : PAGE_SIZE;
      const res = await auditLogApi.getAll({ page: requestedPage, size });
      const payload = res.data?.data;
      if (Array.isArray(payload)) {
        setRawLogs(payload);
        setTotalElements(payload.length);
        setTotalPages(1);
      } else if (payload) {
        setRawLogs(payload.content ?? []);
        setTotalElements(payload.totalElements ?? 0);
        setTotalPages(payload.totalPages ?? 0);
      }
    } catch (err) {
      setError(getErrorMessage(err));
      setRawLogs([]);
    } finally {
      setLoading(false);
    }
  }, [page, hasFilters]);

  useEffect(() => {
    load();
  }, [load, retryKey]);

  const handleRetry = () => {
    setError(null);
    setLoading(true);
    setRetryKey((k) => k + 1);
  };

  const filteredLogs = useMemo(() => {
    const query = search.trim().toLowerCase();
    return rawLogs.filter((log) => {
      if (actionFilter && log.action !== actionFilter) return false;
      if (userFilter && log.userEmail !== userFilter) return false;
      if (query) {
        const haystack = [
          log.userEmail,
          log.action,
          humanizeAction(log.action),
          log.entityType,
          String(log.entityId ?? ''),
          log.details ?? '',
        ]
          .join(' ')
          .toLowerCase();
        if (!haystack.includes(query)) return false;
      }
      return true;
    });
  }, [rawLogs, search, actionFilter, userFilter]);

  const actionOptions = useMemo(() => {
    const seen = new Set<string>();
    const opts: { label: string; value: string }[] = [];
    for (const log of rawLogs) {
      if (log.action && !seen.has(log.action)) {
        seen.add(log.action);
        opts.push({ label: humanizeAction(log.action), value: log.action });
      }
    }
    return opts;
  }, [rawLogs]);

  const userOptions = useMemo(() => {
    const seen = new Set<string>();
    const opts: { label: string; value: string }[] = [];
    for (const log of rawLogs) {
      if (log.userEmail && !seen.has(log.userEmail)) {
        seen.add(log.userEmail);
        opts.push({ label: log.userEmail, value: log.userEmail });
      }
    }
    return opts;
  }, [rawLogs]);

  const clearFilters = () => {
    setSearch('');
    setActionFilter('');
    setUserFilter('');
  };

  const countLabel = hasFilters
    ? `${filteredLogs.length} matching event${filteredLogs.length === 1 ? '' : 's'}`
    : `${totalElements} audit event${totalElements === 1 ? '' : 's'}`;

  const modalDetails = selected ? parseDetails(selected.details) : null;

  return (
    <PageContainer>
        <PageHeader
          title="Audit Logs"
          description="Review important administrative and placement activity."
        />

        <FilterToolbar
          search={
            <SearchInput
              placeholder="Search audit activity..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          }
          filters={
            <>
              <Select
                label="Action"
                placeholder="All actions"
                value={actionFilter}
                onChange={(e) => setActionFilter(e.target.value)}
                options={actionOptions}
                className="w-52"
              />
              <Select
                label="User"
                placeholder="All users"
                value={userFilter}
                onChange={(e) => setUserFilter(e.target.value)}
                options={userOptions}
                className="w-56"
              />
              {hasFilters && (
                <Button variant="ghost" size="sm" onClick={clearFilters}>
                  <FilterX size={14} />
                  Clear filters
                </Button>
              )}
            </>
          }
        >
          <span className="text-[14px] text-neutral-500">{countLabel}</span>
        </FilterToolbar>

        {hasFilters && (
          <p className="mb-4 text-[12.5px] text-neutral-500">
            Filters are applied client-side to the most recent {FILTER_SCOPE.toLocaleString()} audit
            events retrieved from the server.
          </p>
        )}

        {error ? (
          <ErrorState
            title="Unable to load audit logs"
            message={`Could not load audit activity. ${error}`}
            onRetry={handleRetry}
          />
        ) : hasFilters && filteredLogs.length === 0 && !loading ? (
          <EmptyState
            icon={<Activity size={40} />}
            title="No audit events match your filters"
            description="Try adjusting the search term or clearing the filters."
            action={
              <Button variant="secondary" onClick={clearFilters}>
                <FilterX size={14} />
                Clear filters
              </Button>
            }
          />
        ) : (
          <>
            <DataTable<AuditLog>
              columns={[
                {
                  key: 'createdAt',
                  label: 'Timestamp',
                  render: (log) => (
                    <span className="text-[13px] text-neutral-500 whitespace-nowrap">
                      {formatTimestamp(log.createdAt)}
                    </span>
                  ),
                },
                {
                  key: 'userEmail',
                  label: 'User',
                  render: (log) =>
                    log.userEmail ? (
                      <span className="flex items-center gap-2 min-w-0">
                        <Avatar name={log.userEmail} size="xs" className="shrink-0" />
                        <span className="text-[13px] font-medium text-neutral-900 truncate max-w-[220px]">
                          {log.userEmail}
                        </span>
                      </span>
                    ) : (
                      <span className="text-[13px] text-neutral-500">—</span>
                    ),
                },
                {
                  key: 'action',
                  label: 'Action',
                  render: (log) => (
                    <Badge variant={actionVariant(log.action)}>
                      {humanizeAction(log.action)}
                    </Badge>
                  ),
                },
                {
                  key: 'entityType',
                  label: 'Entity',
                  render: (log) => (
                    <div className="min-w-0">
                      <p className="text-[13px] font-medium text-neutral-700 truncate">
                        {log.entityType || '—'}
                      </p>
                      {log.entityId != null && (
                        <p className="text-[12px] text-neutral-500">#{log.entityId}</p>
                      )}
                    </div>
                  ),
                },
                {
                  key: 'details',
                  label: 'Details',
                  render: (log) => (
                    <span
                      className="text-[13px] text-neutral-500 max-w-[280px] truncate block"
                      title={sanitizeText(log.details)}
                    >
                      {sanitizeText(log.details) || '—'}
                    </span>
                  ),
                },
                {
                  key: 'view',
                  label: '',
                  className: 'w-1',
                  render: (log) => (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelected(log);
                      }}
                      aria-label="View details"
                    >
                      <Eye size={14} />
                      View Details
                    </Button>
                  ),
                },
              ]}
              data={filteredLogs}
              rowKey={(log) => log.id}
              onRowClick={(log) => setSelected(log)}
              density="compact"
              loading={loading}
              emptyMessage="No audit activity found"
              emptyIcon={<Activity size={40} />}
            />

            {!hasFilters && (
              <div className="mt-6">
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  totalElements={totalElements}
                  pageSize={PAGE_SIZE}
                  onPageChange={setPage}
                />
              </div>
            )}
          </>
        )}

        <Modal
          isOpen={selected !== null}
          onClose={() => setSelected(null)}
          title="Audit Event"
          description={selected ? `Event #${selected.id}` : undefined}
        >
          {selected && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Field label="Action">
                  <Badge variant={actionVariant(selected.action)}>
                    {humanizeAction(selected.action)}
                  </Badge>
                </Field>
                <Field label="User">
                  <span className="flex items-center gap-2 min-w-0">
                    <Avatar name={selected.userEmail} size="xs" className="shrink-0" />
                    <span className="text-[13px] font-medium text-neutral-900 truncate">
                      {selected.userEmail || '—'}
                    </span>
                  </span>
                </Field>
                <Field label="Timestamp">
                  <span className="text-[13px] text-neutral-700">
                    {formatTimestamp(selected.createdAt)}
                  </span>
                </Field>
                <Field label="Entity">
                  <span className="text-[13px] text-neutral-700">
                    {selected.entityType || '—'}
                    {selected.entityId != null ? `  #${selected.entityId}` : ''}
                  </span>
                </Field>
              </div>
              <Field label="Details">
                {modalDetails ? (
                  <div className="divide-y divide-neutral-100 rounded-[10px] border border-neutral-200/60 overflow-hidden">
                    {modalDetails.map((row) => (
                      <div key={row.label} className="flex items-start gap-3 px-3.5 py-2.5 text-[13px]">
                        <span className="w-32 shrink-0 font-medium text-neutral-500">{row.label}</span>
                        <span className="text-neutral-800 break-all">{row.value}</span>
                      </div>
                    ))}
                  </div>
                ) : (
<p className="text-[13px] text-neutral-800 whitespace-pre-wrap break-words leading-relaxed">
                      {sanitizeText(selected.details) || 'No additional details.'}
                    </p>
                )}
              </Field>
            </div>
          )}
        </Modal>
      </PageContainer>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-[12px] font-semibold uppercase tracking-[0.05em] text-neutral-500 mb-1.5">
        {label}
      </p>
      {children}
    </div>
  );
}