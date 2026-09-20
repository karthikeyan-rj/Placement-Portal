import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { userApi, departmentApi } from '../../api/api';
import { getErrorMessage } from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import { usePaginatedData } from '../../hooks/useApi';
import type { Department, PlacementDrive, UserStats } from '../../types';
import { Users, ShieldCheck, GraduationCap, Building2, CalendarClock } from 'lucide-react';
import { Badge, PageContainer } from '../../components/ui';
import {
  DashboardEmpty,
  DashboardError,
  DashboardRowsSkeleton,
  DashboardSection,
  DriveRow,
  MetricCard,
  MetricsSkeleton,
  SectionAction,
  WelcomeHeader,
} from '../../components/dashboard';

function DepartmentRow({ department }: { department: Department }) {
  return (
    <Link
      to="/departments"
      className="flex items-center gap-3 px-5 py-4 border-b border-neutral-100/60 last:border-0 hover:bg-neutral-50/60 transition-colors block"
    >
      <span className="shrink-0 flex items-center justify-center w-10 h-10 rounded-[10px] bg-brand-navy-50 text-brand-navy-700">
        <Building2 size={18} />
      </span>
      <div className="flex-1 min-w-0">
        <p className="text-[14px] font-semibold text-neutral-900 truncate">{department.name}</p>
        <p className="text-[13px] text-text-secondary mt-0.5 truncate">
          {department.prLimit != null
            ? `PR limit ${department.prLimit}`
            : 'No PR limit configured'}
        </p>
      </div>
      <Badge variant={department.active ? 'success' : 'neutral'} size="sm">
        {department.active ? 'Active' : 'Inactive'}
      </Badge>
    </Link>
  );
}

export default function PODashboard() {
  const { user } = useAuth();
  const [stats, setStats] = useState<UserStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState<string | null>(null);

  const [departments, setDepartments] = useState<Department[]>([]);
  const [deptLoading, setDeptLoading] = useState(true);
  const [deptError, setDeptError] = useState<string | null>(null);

  const [retryKey, setRetryKey] = useState(0);

  const drives = usePaginatedData<PlacementDrive>({
    url: '/placement-drives',
    params: { status: 'UPCOMING', size: 5 },
    cacheTtl: 10_000,
  });

  useEffect(() => {
    let cancelled = false;
    setStatsLoading(true);
    setStatsError(null);
    userApi
      .getStats()
      .then((res) => {
        if (!cancelled) setStats(res.data.data);
      })
      .catch((err) => {
        if (!cancelled) setStatsError(getErrorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setStatsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [retryKey]);

  useEffect(() => {
    let cancelled = false;
    setDeptLoading(true);
    setDeptError(null);
    departmentApi
      .getAll()
      .then((res) => {
        if (!cancelled) setDepartments(res.data.data);
      })
      .catch((err) => {
        if (!cancelled) setDeptError(getErrorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setDeptLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [retryKey]);

  const activeDepartments = departments.filter((d) => d.active).length;
  const displayName = user?.name || 'User';
  const metricsReady = !statsLoading && !deptLoading;
  const metricsError = statsError || deptError;

  return (
    <PageContainer className="space-y-6">
      <WelcomeHeader
        name={displayName}
        roleLabel="Placement Officer"
        subtitle="Oversee students, coordinators and placement drives across every department."
      />

      {!metricsReady ? (
        <MetricsSkeleton />
      ) : metricsError ? (
        <DashboardError
          title="Unable to load dashboard data"
          description="We couldn't retrieve your overview right now."
          onRetry={() => setRetryKey((k) => k + 1)}
        />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
          <MetricCard
            label="Total Students"
            value={stats?.totalStudents ?? 0}
            icon={Users}
            sub={`${stats?.totalPrs ?? 0} PRs included`}
            href="/students"
          />
          <MetricCard
            label="Placement Coordinators"
            value={stats?.totalPcs ?? 0}
            icon={ShieldCheck}
            href="/pc-management"
          />
          <MetricCard
            label="Placement Reps"
            value={stats?.totalPrs ?? 0}
            icon={GraduationCap}
            href="/pr-management"
          />
          <MetricCard
            label="Active Departments"
            value={activeDepartments}
            icon={Building2}
            href="/departments"
          />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
        <DashboardSection
          title="Upcoming Drives"
          subtitle="Placement drives scheduled ahead"
          action={<SectionAction label="View all" to="/placement-drives" />}
          className="lg:col-span-2"
        >
          {drives.loading ? (
            <DashboardRowsSkeleton />
          ) : drives.error ? (
            <DashboardError
              title="Unable to load drives"
              description="We couldn't retrieve placement drives right now."
              onRetry={drives.refresh}
            />
          ) : drives.data.length === 0 ? (
            <DashboardEmpty
              icon={CalendarClock}
              title="No upcoming drive announcements"
              description="When placement drives are scheduled ahead, they will appear here."
            />
          ) : (
            drives.data.map((drive) => <DriveRow key={drive.id} drive={drive} to="/placement-drives" />)
          )}
        </DashboardSection>

        <DashboardSection title="Department Overview" subtitle="Departments in the system">
          {deptLoading ? (
            <DashboardRowsSkeleton />
          ) : deptError ? (
            <DashboardError
              title="Unable to load departments"
              description="We couldn't retrieve the department list right now."
            />
          ) : departments.length === 0 ? (
            <DashboardEmpty
              icon={Building2}
              title="No departments found"
              description="Departments will appear here once they are created."
            />
          ) : (
            departments
              .slice(0, 5)
              .map((department) => <DepartmentRow key={department.id} department={department} />)
          )}
        </DashboardSection>
      </div>
    </PageContainer>
  );
}