import { useEffect, useState } from 'react';
import { userApi, departmentApi } from '../../api/api';
import type { Department } from '../../types';
import {
  Badge,
  DataTable,
  ErrorState,
  PageHeader,
  PageContainer,
  StatCard,
  Skeleton,
  EmptyState,
} from '../../components/ui';
import { Users, Briefcase, Shield, Building2 } from 'lucide-react';
import { getErrorMessage } from '../../api/axios';
import type { LucideIcon } from 'lucide-react';

interface StatsData {
  totalStudents: number;
  activeStudents: number;
  totalPcs: number;
  totalPrs: number;
}

interface StatItem {
  label: string;
  value: number;
  icon: LucideIcon;
  accent: 'teal' | 'navy';
}

const columns = [
  {
    key: 'name',
    label: 'Department',
    render: (d: Department) => (
      <span className="font-medium text-neutral-900">{d.name}</span>
    ),
  },
  {
    key: 'prLimit',
    label: 'PR Limit',
    render: (d: Department) => (
      <span className="text-neutral-600">{d.prLimit ?? '—'}</span>
    ),
  },
  {
    key: 'active',
    label: 'Status',
    render: (d: Department) => (
      <Badge variant={d.active ? 'success' : 'neutral'} dot={true}>
        {d.active ? 'Active' : 'Inactive'}
      </Badge>
    ),
  },
];

export default function PODashboard() {
  const [stats, setStats] = useState<StatsData | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState<string | null>(null);

  const [departments, setDepartments] = useState<Department[]>([]);
  const [deptLoading, setDeptLoading] = useState(true);
  const [deptError, setDeptError] = useState<string | null>(null);

  useEffect(() => {
    setStatsLoading(true);
    userApi.getStats()
      .then((res) => {
        setStats(res.data.data);
      })
      .catch((err) => {
        setStatsError(getErrorMessage(err));
      })
      .finally(() => {
        setStatsLoading(false);
      });
  }, []);

  useEffect(() => {
    setDeptLoading(true);
    departmentApi.getAll()
      .then((res) => {
        setDepartments(res.data.data);
      })
      .catch((err) => {
        setDeptError(getErrorMessage(err));
      })
      .finally(() => {
        setDeptLoading(false);
      });
  }, []);

  const statItems: StatItem[] = [
    {
      label: 'Total Students',
      value: stats?.totalStudents ?? 0,
      icon: Users,
      accent: 'navy',
    },
    {
      label: 'Placement Coordinators',
      value: stats?.totalPcs ?? 0,
      icon: Briefcase,
      accent: 'teal',
    },
    {
      label: 'Placement Reps',
      value: stats?.totalPrs ?? 0,
      icon: Shield,
      accent: 'navy',
    },
    {
      label: 'Departments',
      value: departments.length,
      icon: Building2,
      accent: 'teal',
    },
  ];

  return (
    <PageContainer className="space-y-6">
      <PageHeader title="Dashboard" description="Overview of the placement system" />

      {statsLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-5">
              <Skeleton className="h-3 w-24 mb-4" />
              <Skeleton className="h-8 w-14" />
            </div>
          ))}
        </div>
      ) : statsError ? (
        <ErrorState title="Failed to load statistics" message={statsError} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {statItems.map((item) => (
            <StatCard
              key={item.label}
              label={item.label}
              value={item.value}
              accent={item.accent}
              icon={<item.icon size={20} />}
            />
          ))}
        </div>
      )}

      <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card">
        <div className="px-5 py-4 border-b border-neutral-100">
          <h2 className="text-[15px] font-semibold text-neutral-900">Department Overview</h2>
          <p className="text-[13px] text-neutral-500 mt-0.5">All academic departments in the system</p>
        </div>
        {deptLoading ? (
          <div className="p-5">
            <Skeleton className="h-72 w-full rounded-lg" />
          </div>
        ) : deptError ? (
          <div className="p-6">
            <ErrorState title="Failed to load departments" message={deptError} />
          </div>
        ) : departments.length === 0 ? (
          <div className="p-6">
            <EmptyState
              title="No departments found"
              description="Departments will appear here once created."
            />
          </div>
        ) : (
          <DataTable
            columns={columns}
            data={departments}
            rowKey={(d) => d.id}
            emptyMessage="No departments found"
          />
        )}
      </div>
    </PageContainer>
  );
}
