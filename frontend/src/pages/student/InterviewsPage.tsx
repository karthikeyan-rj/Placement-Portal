import { useEffect, useMemo, useState } from 'react';
import api from '../../api/axios';
import type { StudentInterview } from '../../types';
import {
  Badge,
  EmptyState,
  ErrorState,
  Modal,
  PageContainer,
  PageHeader,
  Skeleton,
  Tabs,
  formatInterviewStatus,
  interviewStatusVariant,
} from '../../components/ui';
import {
  Briefcase,
  Building2,
  Calendar,
  CheckCircle2,
  ChevronRight,
  ClipboardList,
  Clock,
  ListChecks,
  MapPin,
  StickyNote,
  Wallet,
  XCircle,
  type LucideIcon,
} from 'lucide-react';

type InterviewTab = 'upcoming' | 'history';

const isUpcoming = (i: StudentInterview) => i.status === 'SCHEDULED';

function fmtDate(d: string): string {
  const dt = new Date(d);
  if (Number.isNaN(dt.getTime())) return d;
  return dt.toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function DetailItem({
  icon: Icon,
  label,
  value,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
}) {
  const missing = value === '—';
  return (
    <div className="rounded-[12px] border border-neutral-200/60 bg-neutral-50/50 px-4 py-3">
      <div className="flex items-center gap-1.5 text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
        <Icon size={13} />
        {label}
      </div>
      <p
        className={`mt-1 text-[15px] font-medium break-words ${
          missing ? 'text-neutral-500' : 'text-neutral-800'
        }`}
      >
        {value}
      </p>
    </div>
  );
}

function InterviewCard({
  interview,
  onClick,
}: {
  interview: StudentInterview;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full text-left flex items-center gap-3 sm:gap-4 px-4 sm:px-5 py-4 hover:bg-neutral-50/70 transition-colors animate-fadeIn cursor-pointer"
    >
      <span className="shrink-0 flex items-center justify-center w-10 h-10 sm:w-11 sm:h-11 rounded-[12px] bg-brand-navy text-white">
        <Building2 size={18} />
      </span>
      <div className="flex-1 min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <p className="text-[15px] font-semibold text-neutral-900 truncate">
            {interview.companyName}
          </p>
          <span className="text-[13px] text-neutral-300">·</span>
          <p className="text-[13px] text-neutral-500 truncate">
            {interview.driveJobRole}
          </p>
        </div>
        <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1.5">
          <Badge variant="neutral" size="sm">
            {interview.roundName}
          </Badge>
          {interview.interviewDate && (
            <span className="inline-flex items-center gap-1 text-[12.5px] text-neutral-500">
              <Calendar size={12} className="text-neutral-500" />
              {fmtDate(interview.interviewDate)}
            </span>
          )}
        </div>
      </div>
      <div className="shrink-0 flex items-center gap-2">
        <Badge variant={interviewStatusVariant(interview.status)} dot size="sm">
          {formatInterviewStatus(interview.status)}
        </Badge>
        <ChevronRight size={16} className="text-neutral-300 shrink-0" />
      </div>
    </button>
  );
}

function CardsSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="divide-y divide-neutral-100/60">
      {Array.from({ length: rows }).map((_, idx) => (
        <div key={idx} className="flex items-center gap-4 px-5 py-4">
          <Skeleton className="w-10 h-10 rounded-[12px] shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-3.5 w-2/5" />
            <Skeleton className="h-3 w-1/3" />
          </div>
          <Skeleton className="h-5 w-20 rounded-full shrink-0" />
        </div>
      ))}
    </div>
  );
}

export default function InterviewsPage() {
  const [interviews, setInterviews] = useState<StudentInterview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<InterviewTab>('upcoming');
  const [selected, setSelected] = useState<StudentInterview | null>(null);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const fetchInterviews = async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await api.get('/interviews');
        const data = res.data?.data;
        const arr = Array.isArray(data) ? data : (data?.content ?? []);
        if (!cancelled) setInterviews(arr);
      } catch {
        if (!cancelled) setError('We could not load your interviews right now.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    fetchInterviews();
    return () => {
      cancelled = true;
    };
  }, [retryKey]);

  const upcoming = useMemo(() => interviews.filter(isUpcoming), [interviews]);
  const history = useMemo(() => interviews.filter((i) => !isUpcoming(i)), [interviews]);
  const stats = useMemo(
    () => ({
      total: interviews.length,
      selected: interviews.filter((i) => i.status === 'SELECTED').length,
      upcoming: upcoming.length,
      rejected: interviews.filter((i) => i.status === 'REJECTED').length,
    }),
    [interviews, upcoming]
  );

  const visible = tab === 'upcoming' ? upcoming : history;
  const statCards = [
    { label: 'Total', value: stats.total, icon: ListChecks, iconBg: 'bg-brand-navy text-white' },
    { label: 'Selected', value: stats.selected, icon: CheckCircle2, iconBg: 'bg-success-50 text-success-500' },
    { label: 'Upcoming', value: stats.upcoming, icon: Clock, iconBg: 'bg-info-50 text-info-500' },
    { label: 'Not Selected', value: stats.rejected, icon: XCircle, iconBg: 'bg-danger-50 text-danger-500' },
  ];

  return (
    <PageContainer>
      <PageHeader
        title="My Interviews"
        description="Upcoming rounds and your past interview progress."
      />

      {!loading && !error && interviews.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6 animate-slideUp">
          {statCards.map(({ label, value, icon: Icon, iconBg }) => (
            <div
              key={label}
              className="bg-white rounded-[14px] border border-neutral-200/80 shadow-soft p-4 flex items-center gap-3"
            >
              <div
                className={`w-9 h-9 rounded-[9px] flex items-center justify-center shrink-0 ${iconBg}`}
              >
                <Icon size={16} />
              </div>
              <div className="min-w-0">
                <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
                  {label}
                </p>
                <p className="text-[20px] font-bold text-neutral-900 leading-tight">
                  {value}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}

      {error && !loading ? (
        <ErrorState
          title="Unable to load interviews"
          message={error}
          onRetry={() => setRetryKey((k) => k + 1)}
        />
      ) : (
        <>
          <Tabs<InterviewTab>
            tabs={[
              { key: 'upcoming', label: `Upcoming (${upcoming.length})` },
              { key: 'history', label: `History (${history.length})` },
            ]}
            active={tab}
            onChange={setTab}
          />

          <div className="bg-white rounded-[14px] border border-neutral-200/60 overflow-hidden">
            {loading ? (
              <CardsSkeleton />
            ) : visible.length === 0 ? (
              tab === 'upcoming' ? (
                <EmptyState
                  icon={<Briefcase size={40} />}
                  title="No upcoming interviews"
                  description="Interviews scheduled for you will appear here. Keep an eye on your placement drives."
                />
              ) : (
                <EmptyState
                  icon={<ClipboardList size={40} />}
                  title="No interview history yet"
                  description="Rounds you have attended or completed will show up here as your drives progress."
                />
              )
            ) : (
              <div className="divide-y divide-neutral-100/60">
                {visible.map((interview) => (
                  <InterviewCard
                    key={interview.id}
                    interview={interview}
                    onClick={() => setSelected(interview)}
                  />
                ))}
              </div>
            )}
          </div>
        </>
      )}

      <Modal
        isOpen={!!selected}
        onClose={() => setSelected(null)}
        title={
          selected ? `${selected.companyName} · ${selected.driveJobRole}` : ''
        }
        description={selected ? `Round: ${selected.roundName}` : ''}
        size="md"
      >
        {selected && (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <Badge
                variant={interviewStatusVariant(selected.status)}
                dot
                size="sm"
              >
                {formatInterviewStatus(selected.status)}
              </Badge>
              <Badge variant="neutral" size="sm">
                {selected.roundName}
              </Badge>
              {selected.attended != null && (
                <Badge
                  variant={selected.attended ? 'success' : 'danger'}
                  size="sm"
                >
                  {selected.attended ? 'Attended' : 'Not attended'}
                </Badge>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <DetailItem
                icon={Calendar}
                label="Interview Date"
                value={selected.interviewDate ? fmtDate(selected.interviewDate) : '—'}
              />
              <DetailItem
                icon={Calendar}
                label="Drive Date"
                value={selected.driveDate ? fmtDate(selected.driveDate) : '—'}
              />
              <DetailItem
                icon={MapPin}
                label="Location"
                value={selected.driveLocation || '—'}
              />
              <DetailItem
                icon={Wallet}
                label="Package"
                value={
                  selected.packageLpa != null ? `${selected.packageLpa} LPA` : '—'
                }
              />
            </div>

            {selected.remarks ? (
              <div className="rounded-[12px] border border-neutral-200/60 bg-neutral-50/50 px-4 py-3">
                <div className="flex items-center gap-1.5 text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
                  <StickyNote size={13} />
                  Remarks
                </div>
                <p className="mt-1.5 text-[14px] text-neutral-700 leading-relaxed">
                  {selected.remarks}
                </p>
              </div>
            ) : null}
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}