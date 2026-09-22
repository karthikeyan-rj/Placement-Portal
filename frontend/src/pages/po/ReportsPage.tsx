import { useState, useEffect, useMemo, type ReactNode } from 'react';
import { reportApi, departmentApi } from '../../api/api';
import type { Department, ReportSummary } from '../../types';
import { getErrorMessage } from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import {
  Button,
  Select,
  Dropdown,
  Card,
  PageHeader,
  PageContainer,
  EmptyState,
  ErrorState,
  FilterToolbar,
  Skeleton,
  notify,
} from '../../components/ui';
import { MetricCard, MetricsSkeleton } from '../../components/dashboard';
import {
  Users,
  GraduationCap,
  Briefcase,
  Building2,
  Download,
  BarChart3,
  FilterX,
  FileDown,
  FileSpreadsheet,
} from 'lucide-react';

interface DepartmentStat {
  id: number;
  name: string;
  students: number;
  interested: number;
  placed: number;
  rate: number;
}

interface BatchStat {
  batch: string;
  students: number;
  interested: number;
  placed: number;
  rate: number;
}

interface StatusSegment {
  key: string;
  label: string;
  color: string;
  count: number;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function slugify(name: string): string {
  return (
    name
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)+/g, '') || 'report'
  );
}

function prettyNumber(n: number): string {
  return n.toLocaleString('en-IN');
}

function fileDateStamp(): string {
  const d = new Date();
  return `${String(d.getDate()).padStart(2, '0')}-${MONTHS[d.getMonth()]}-${d.getFullYear()}`;
}

export default function ReportsPage() {
  const { user } = useAuth();
  const isPo = user?.role === 'PO';

  const [departments, setDepartments] = useState<Department[]>([]);
  const [summary, setSummary] = useState<ReportSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);

  const [deptFilter, setDeptFilter] = useState('');
  const [exporting, setExporting] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    (async () => {
      try {
        const [deptRes, summaryRes] = await Promise.all([
          departmentApi.getAll(),
          reportApi.summary(deptFilter === '' ? undefined : Number(deptFilter)),
        ]);
        if (cancelled) return;
        setDepartments(deptRes.data?.data ?? []);
        setSummary(summaryRes.data?.data ?? null);
      } catch (err) {
        if (!cancelled) setError(getErrorMessage(err));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [deptFilter, retryKey]);

  const handleRetry = () => {
    setError(null);
    setLoading(true);
    setRetryKey((k) => k + 1);
  };

  const stats = useMemo(() => {
    const s = summary;
    const totalStudents = s?.totalStudentPopulation ?? 0;
    const placed = s?.placed ?? 0;
    const interested = s?.placementInterested ?? 0;
    const notPlaced = s?.notPlaced ?? 0;
    const blocked = s?.blocked ?? 0;
    const rate = s?.placementRate ?? 0;

    const byDept: DepartmentStat[] = (s?.byDepartment ?? []).map((d) => ({
      id: d.departmentId ?? -1,
      name: d.departmentName,
      students: d.studentCount,
      interested: d.interestedCount,
      placed: d.placedCount,
      rate: d.placementRate,
    }));

    const byBatch: BatchStat[] = (s?.byBatch ?? []).map((b) => ({
      batch: b.batch,
      students: b.studentCount,
      interested: b.interestedCount,
      placed: b.placedCount,
      rate: b.placementRate,
    }));

    const statusSegments: StatusSegment[] = [
      { key: 'PLACED', label: 'Placed', color: '#059669', count: placed },
      { key: 'NOT_PLACED', label: 'Not placed', color: '#cbd5e1', count: notPlaced },
      { key: 'BLOCKED', label: 'Blocked', color: '#f59e0b', count: blocked },
    ].filter((seg) => seg.count > 0);

    return {
      totalStudents,
      placed,
      interested,
      rate,
      blocked,
      statusSegments,
      activeDrives: s?.activeDrives ?? 0,
      completedDrives: s?.completedDrives ?? 0,
      activeCompanies: s?.activeCompanies ?? 0,
      byDept,
      byBatch,
    };
  }, [summary]);

  const deptOptions = useMemo(() => {
    return departments
      .filter((d) => d.active || String(d.id) === deptFilter)
      .map((d) => ({
        label: d.name,
        value: String(d.id),
      }));
  }, [departments, deptFilter]);

  const selectedDeptName = useMemo(
    () => departments.find((d) => String(d.id) === deptFilter)?.name,
    [departments, deptFilter]
  );

  const handleExport = async (type: 'students' | 'placement') => {
    setExporting(type);
    try {
      const res =
        type === 'students'
          ? await reportApi.students(deptFilter === '' ? undefined : Number(deptFilter))
          : await reportApi.placements();
      const blob = new Blob([res.data], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const stamp = fileDateStamp();
      a.download =
        type === 'students'
          ? selectedDeptName
            ? `students-${slugify(selectedDeptName)}-${stamp}.csv`
            : `students-all-departments-${stamp}.csv`
          : `placement-report-${stamp}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      notify.success('Report downloaded successfully');
    } catch {
      notify.error('Failed to download report');
    } finally {
      setExporting(null);
    }
  };

  const exportItems = [
    {
      label: 'Students CSV',
      onClick: () => handleExport('students'),
      icon: <FileSpreadsheet size={14} />,
    },
    ...(isPo
      ? [
          {
            label: 'Placement Report CSV',
            onClick: () => handleExport('placement'),
            icon: <FileDown size={14} />,
          },
        ]
      : []),
  ];

  return (
    <PageContainer>
        <PageHeader
          title="Reports"
          description="Placement performance and recruitment insights."
        />

        <FilterToolbar
          search={
            <Select
              label="Department"
              placeholder="All departments"
              value={deptFilter}
              onChange={(e) => setDeptFilter(e.target.value)}
              options={deptOptions}
              className="w-64"
            />
          }
          filters={
            deptFilter !== '' ? (
              <Button variant="ghost" size="sm" onClick={() => setDeptFilter('')}>
                <FilterX size={14} />
                Clear filter
              </Button>
            ) : undefined
          }
        >
          <Dropdown
            trigger={
              <Button loading={exporting !== null}>
                <Download size={14} />
                {exporting === 'students'
                  ? 'Exporting students...'
                  : exporting === 'placement'
                    ? 'Exporting report...'
                    : 'Export CSV'}
              </Button>
            }
            items={exportItems}
          />
        </FilterToolbar>

        {error ? (
          <ErrorState
            title="Unable to load reports"
            message={`Could not load report data. ${error}`}
            onRetry={handleRetry}
          />
        ) : loading ? (
          <div className="space-y-6">
            <MetricsSkeleton />
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <Skeleton className="h-[340px] rounded-[14px]" />
              <Skeleton className="h-[340px] lg:col-span-2 rounded-[14px]" />
            </div>
          </div>
        ) : stats.totalStudents === 0 ? (
          <EmptyState
            icon={<BarChart3 size={40} />}
            title="No placement report data available"
            description="Reports will appear here once student profiles and placement data exist."
          />
        ) : (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              <MetricCard
                label="Total Students"
                value={prettyNumber(stats.totalStudents)}
                icon={Users}
                sub={`${prettyNumber(stats.interested)} interested in placement`}
                href="/students"
              />
              <MetricCard
                label="Placement Rate"
                value={`${stats.rate}%`}
                icon={GraduationCap}
                sub={`${prettyNumber(stats.placed)} of ${prettyNumber(stats.totalStudents)} placed`}
                href="/students"
              />
              <MetricCard
                label="Active Drives"
                value={prettyNumber(stats.activeDrives)}
                icon={Briefcase}
                sub={`${prettyNumber(stats.completedDrives)} completed`}
                href="/placement-drives"
              />
              <MetricCard
                label="Companies"
                value={prettyNumber(stats.activeCompanies)}
                icon={Building2}
                sub="active companies"
                href="/companies"
              />
            </div>

            <div className="mt-6 grid grid-cols-1 lg:grid-cols-3 gap-6">
              <Card title="Placement Overview" subtitle="Placed vs not placed across students" padding="none">
                <div className="p-6 min-h-[280px]">
                  <PlacementDonut
                    segments={stats.statusSegments}
                    total={stats.totalStudents}
                    placed={stats.placed}
                    rate={stats.rate}
                  />
                </div>
              </Card>

              <Card
                title="Department Performance"
                subtitle="Placement outcomes by department"
                padding="none"
                className="lg:col-span-2"
              >
                <div className="overflow-x-auto">
                  <table className="w-full text-[14px]">
                    <thead>
                      <tr className="border-b border-neutral-200/80 bg-primary-50/30">
                        <Th>Department</Th>
                        <Th className="text-right">Students</Th>
                        <Th className="text-right">Interested</Th>
                        <Th className="text-right">Placed</Th>
                        <Th className="w-56">Placement %</Th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-neutral-100/60">
                      {stats.byDept.map((d) => (
                        <tr key={d.id} className="hover:bg-neutral-50/60 transition-colors">
                          <td className="px-5 py-3.5 font-medium text-neutral-900 whitespace-nowrap">
                            {d.name}
                          </td>
                          <Td className="text-right">{prettyNumber(d.students)}</Td>
                          <Td className="text-right">{prettyNumber(d.interested)}</Td>
                          <Td className="text-right font-medium text-success-700">
                            {prettyNumber(d.placed)}
                          </Td>
                          <td className="px-5 py-3.5">
                            <RateBar rate={d.rate} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {stats.byDept.length === 0 && (
                    <div className="px-5 py-8 text-center text-[14px] text-neutral-500">
                      No student profiles in {selectedDeptName || 'the selected scope'}.
                    </div>
                  )}
                </div>
              </Card>
            </div>

            <div className="mt-6">
              <Card title="Placement Summary by Batch" subtitle="Year-wise placement outcomes" padding="none">
                <div className="overflow-x-auto">
                  <table className="w-full text-[14px]">
                    <thead>
                      <tr className="border-b border-neutral-200/80 bg-primary-50/30">
                        <Th>Batch</Th>
                        <Th className="text-right">Students</Th>
                        <Th className="text-right">Interested</Th>
                        <Th className="text-right">Placed</Th>
                        <Th className="w-56">Placement %</Th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-neutral-100/60">
                      {stats.byBatch.map((b) => (
                        <tr key={b.batch} className="hover:bg-neutral-50/60 transition-colors">
                          <td className="px-5 py-3.5 font-medium text-neutral-900 whitespace-nowrap">
                            {b.batch}
                          </td>
                          <Td className="text-right">{prettyNumber(b.students)}</Td>
                          <Td className="text-right">{prettyNumber(b.interested)}</Td>
                          <Td className="text-right font-medium text-success-700">
                            {prettyNumber(b.placed)}
                          </Td>
                          <td className="px-5 py-3.5">
                            <RateBar rate={b.rate} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {stats.byBatch.length === 0 && (
                    <div className="px-5 py-8 text-center text-[14px] text-neutral-500">
                      No student profiles in {selectedDeptName || 'the selected scope'}.
                    </div>
                  )}
                </div>
              </Card>
            </div>
          </>
        )}
      </PageContainer>
  );
}

function PlacementDonut({
  segments,
  total,
  placed,
  rate,
}: {
  segments: StatusSegment[];
  total: number;
  placed: number;
  rate: number;
}) {
  const radius = 40;
  const stroke = 12;
  const circumference = 2 * Math.PI * radius;

  const arcs = segments.reduce<{
    key: string;
    color: string;
    label: string;
    count: number;
    dash: number;
    offset: number;
  }[]>((acc, seg) => {
    const fraction = seg.count / total;
    const dash = fraction * circumference;
    const offset = acc.length ? acc[acc.length - 1].offset - acc[acc.length - 1].dash : 0;
    acc.push({ key: seg.key, color: seg.color, label: seg.label, count: seg.count, dash, offset });
    return acc;
  }, []);

  return (
    <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-4 h-full w-full">
      <svg width="180" height="180" viewBox="0 0 100 100" className="shrink-0">
        <circle cx="50" cy="50" r={radius} fill="none" stroke="#f4f4f5" strokeWidth={stroke} />
        <g transform="rotate(-90 50 50)">
          {arcs.map((a) => (
            <circle
              key={a.key}
              cx="50"
              cy="50"
              r={radius}
              fill="none"
              stroke={a.color}
              strokeWidth={stroke}
              strokeDasharray={`${a.dash} ${circumference - a.dash}`}
              strokeDashoffset={a.offset}
            />
          ))}
        </g>
        <text
          x="50"
          y="47"
          textAnchor="middle"
          className="fill-neutral-900"
          fontSize="16"
          fontWeight="700"
        >
          {prettyNumber(placed)}
        </text>
        <text x="50" y="60" textAnchor="middle" className="fill-neutral-400" fontSize="5.5">
          placed
        </text>
      </svg>
      <div className="space-y-3">
        {segments.map((seg) => (
          <div key={seg.key} className="flex items-center gap-2.5">
            <span
              className="w-2.5 h-2.5 rounded-[4px] shrink-0"
              style={{ backgroundColor: seg.color }}
            />
            <span className="text-[13px] text-neutral-600">{seg.label}</span>
            <span className="text-[13px] font-semibold text-neutral-900 tabular-nums">
              {prettyNumber(seg.count)}
            </span>
            <span className="text-[12px] text-neutral-500 tabular-nums">
              {total ? Math.round((seg.count / total) * 1000) / 10 : 0}%
            </span>
          </div>
        ))}
        <div className="pt-2 border-t border-neutral-100">
          <span className="text-[13px] font-medium text-neutral-600">
            Placement rate:{' '}
            <span className="font-bold text-success-700 tabular-nums">{rate}%</span>
          </span>
        </div>
      </div>
    </div>
  );
}

function RateBar({ rate }: { rate: number }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="h-1.5 flex-1 rounded-full bg-neutral-100 overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{
            width: `${rate}%`,
            backgroundColor: rate > 0 ? '#059669' : '#d4d4d8',
          }}
        />
      </div>
      <span className="text-[12.5px] font-semibold text-neutral-600 w-12 text-right tabular-nums">
        {rate}%
      </span>
    </div>
  );
}

function Th({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <th
      className={`px-5 py-3.5 text-left text-[12px] font-semibold uppercase tracking-[0.05em] text-neutral-500 whitespace-nowrap ${className}`}
    >
      {children}
    </th>
  );
}

function Td({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <td className={`px-5 py-3.5 text-neutral-700 whitespace-nowrap ${className}`}>{children}</td>
  );
}