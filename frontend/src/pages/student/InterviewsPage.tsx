import { useState, useEffect } from 'react';
import api from '../../api/axios';
import type { StudentInterview } from '../../types';
import {
  Badge,
  DataTable,
  EmptyState,
  PageHeader,
  PageContainer,
  notify,
} from '../../components/ui';
import { Calendar, MessageSquare, CheckCircle2, Clock, XCircle, ListChecks } from 'lucide-react';

const statusVariant = (s: string): 'warning' | 'success' | 'danger' | 'info' | 'neutral' => {
  switch (s) {
    case 'SELECTED': return 'success';
    case 'REJECTED': return 'danger';
    case 'PENDING': return 'warning';
    case 'IN_PROGRESS': return 'info';
    case 'SHORTLISTED': return 'info';
    case 'WAITLISTED': return 'warning';
    default: return 'neutral';
  }
};

const statusLabel = (s: string): string => {
  return s.replace('_', ' ');
};

export default function InterviewsPage() {
  const [interviews, setInterviews] = useState<StudentInterview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchInterviews = async () => {
      try {
        setLoading(true);
        setError(null);
        const res = await api.get('/interviews');
        const data = res.data?.data;
        setInterviews(data?.content ?? (Array.isArray(data) ? data : []));
      } catch {
        setError('Failed to load interviews');
        notify.error('Failed to load interviews');
      } finally {
        setLoading(false);
      }
    };
    fetchInterviews();
  }, []);

  const columns = [
    {
      key: 'companyName',
      label: 'Company',
      render: (i: StudentInterview) => (
        <span className="text-[15px] font-medium text-neutral-900">{i.companyName}</span>
      ),
    },
    {
      key: 'driveJobRole',
      label: 'Drive Role',
      render: (i: StudentInterview) => (
        <span className="text-[15px] text-neutral-700">{i.driveJobRole}</span>
      ),
    },
    {
      key: 'roundName',
      label: 'Round',
      render: (i: StudentInterview) => (
        <Badge variant="neutral" size="sm">{i.roundName}</Badge>
      ),
    },
    {
      key: 'status',
      label: 'Status',
      render: (i: StudentInterview) => (
        <Badge variant={statusVariant(i.status)} dot>{statusLabel(i.status)}</Badge>
      ),
    },
    {
      key: 'interviewDate',
      label: 'Date',
      render: (i: StudentInterview) =>
        i.interviewDate ? (
          <div className="flex items-center gap-1.5">
            <Calendar size={12} className="text-neutral-400" />
            <span className="text-[15px] text-neutral-700">{new Date(i.interviewDate).toLocaleDateString()}</span>
          </div>
        ) : (
          <span className="text-[15px] text-neutral-400">-</span>
        ),
    },
    {
      key: 'attended',
      label: 'Attended',
      render: (i: StudentInterview) => {
        if (i.attended === null || i.attended === undefined) {
          return <span className="text-[15px] text-neutral-400">-</span>;
        }
        return (
          <Badge variant={i.attended ? 'success' : 'danger'} dot size="sm">
            {i.attended ? 'Yes' : 'No'}
          </Badge>
        );
      },
    },
    {
      key: 'remarks',
      label: 'Remarks',
      render: (i: StudentInterview) => (
        <span className="text-[15px] text-neutral-600 max-w-[200px] block truncate" title={i.remarks || ''}>
          {i.remarks || '-'}
        </span>
      ),
    },
  ];

  const stats = {
    total: interviews.length,
    selected: interviews.filter((i) => i.status === 'SELECTED').length,
    pending: interviews.filter((i) => i.status === 'PENDING' || i.status === 'IN_PROGRESS').length,
    rejected: interviews.filter((i) => i.status === 'REJECTED').length,
  };

  return (
    <PageContainer>
      <PageHeader
        title="Interviews"
        description="Track your interview progress."
      />

      {!loading && interviews.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-4 flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-brand-navy flex items-center justify-center text-white">
              <ListChecks size={16} />
            </div>
            <div>
              <p className="text-[13px] text-neutral-500 uppercase tracking-wide">Total</p>
              <p className="text-[20px] font-bold text-neutral-900">{stats.total}</p>
            </div>
          </div>
          <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-4 flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-primary-50 flex items-center justify-center text-primary-600">
              <CheckCircle2 size={16} />
            </div>
            <div>
              <p className="text-[13px] text-neutral-500 uppercase tracking-wide">Selected</p>
              <p className="text-[20px] font-bold text-neutral-900">{stats.selected}</p>
            </div>
          </div>
          <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-4 flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-info-50 flex items-center justify-center text-info-500">
              <Clock size={16} />
            </div>
            <div>
              <p className="text-[13px] text-neutral-500 uppercase tracking-wide">In Progress</p>
              <p className="text-[20px] font-bold text-neutral-900">{stats.pending}</p>
            </div>
          </div>
          <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-4 flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-danger-50 flex items-center justify-center text-danger-500">
              <XCircle size={16} />
            </div>
            <div>
              <p className="text-[13px] text-neutral-500 uppercase tracking-wide">Not Selected</p>
              <p className="text-[20px] font-bold text-neutral-900">{stats.rejected}</p>
            </div>
          </div>
        </div>
      )}

      {error && (
        <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-12">
          <EmptyState
            icon={<MessageSquare size={48} />}
            title="Error loading interviews"
            description={error}
          />
        </div>
      )}

      {!error && (
        <DataTable<StudentInterview>
          columns={columns}
          data={interviews}
          rowKey={(i) => i.id}
          loading={loading}
          emptyMessage="No interviews scheduled yet. Interviews will appear here once you are shortlisted for a placement drive."
          emptyIcon={<MessageSquare size={40} />}
        />
      )}
    </PageContainer>
  );
}
