import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { departmentApi } from '../../api/api';
import type { Department, DepartmentAggregate } from '../../types';
import { Skeleton, PageHeader, PageContainer, ErrorState, EmptyState } from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { Building2, Users, Shield, GraduationCap, ArrowRight } from 'lucide-react';

const DEPT_FULL_NAMES: Record<string, string> = {
  CSE: 'Computer Science and Engineering',
  ECE: 'Electronics and Communication Engineering',
  EEE: 'Electrical and Electronics Engineering',
  MECH: 'Mechanical Engineering',
  CIVIL: 'Civil Engineering',
  IT: 'Information Technology',
};

export default function DepartmentsPage() {
  const navigate = useNavigate();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [aggregates, setAggregates] = useState<Record<number, DepartmentAggregate>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [deptRes, aggregateRes] = await Promise.all([
        departmentApi.getAll(),
        departmentApi.aggregates(),
      ]);
      setDepartments(deptRes.data.data || []);
      const aggList = aggregateRes.data.data || [];
      setAggregates(Object.fromEntries(aggList.map((a) => [a.departmentId, a])));
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchData();
  }, [fetchData, reloadKey]);

  const getDeptStats = (deptId: number) => {
    const agg = aggregates[deptId];
    return {
      pcCount: agg?.pcCount ?? 0,
      prCount: agg?.prCount ?? 0,
      studentCount: agg?.studentCount ?? 0,
    };
  };

  return (
    <div className="bg-background min-h-screen">
      <PageContainer className="py-8">
        <PageHeader
          title="Departments"
          description="Academic departments and their placement responsibilities."
        />

        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div key={i} className="glass rounded-[14px] border border-white/40 shadow-card p-6">
                <Skeleton className="h-5 w-32 mb-4" />
                <Skeleton className="h-3 w-40 mb-3" />
                <Skeleton className="h-3 w-24" />
              </div>
            ))}
          </div>
        ) : error ? (
          <ErrorState
            title="Unable to load departments"
            message={error}
            onRetry={() => setReloadKey((k) => k + 1)}
          />
        ) : departments.length === 0 ? (
          <div className="glass rounded-[14px] border border-white/40">
            <EmptyState
              icon={<Building2 size={40} />}
              title="No departments available"
              description="Departments will appear here once created."
            />
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {departments.map((dept) => {
              const { pcCount, prCount, studentCount } = getDeptStats(dept.id);
              const fullName = DEPT_FULL_NAMES[dept.name.toUpperCase()] || `${dept.name} Department`;

              return (
                <div
                  key={dept.id}
                  className="relative glass rounded-[14px] border border-white/40 shadow-card p-6 transition-all duration-200 hover:shadow-raised hover:-translate-y-px group"
                >
                  <div className="absolute inset-x-0 top-0 h-[3px] rounded-t-[14px] bg-gradient-to-r from-primary-500 to-primary-400" />
                  <div className="flex items-start gap-3.5">
                    <div className="flex items-center justify-center w-11 h-11 rounded-[12px] bg-primary-500/10 text-primary-600 shrink-0">
                      <Building2 size={20} />
                    </div>
                    <div className="min-w-0">
                      <h3 className="text-[16px] font-semibold text-neutral-900 tracking-[-0.01em] group-hover:text-primary-600 transition-colors">
                        {dept.name}
                      </h3>
                      <p className="text-[13px] text-neutral-500 mt-0.5 leading-snug">{fullName}</p>
                    </div>
                  </div>

                  <div className="mt-6 space-y-2.5">
                    <div className="flex items-center justify-between text-[14px]">
                      <span className="text-neutral-500 flex items-center gap-2">
                        <Users size={14} className="text-neutral-400" /> Students
                      </span>
                      <span className="font-semibold text-neutral-800 tabular-nums">{studentCount}</span>
                    </div>
                    <div className="flex items-center justify-between text-[14px]">
                      <span className="text-neutral-500 flex items-center gap-2">
                        <Shield size={14} className="text-neutral-400" /> Coordinators
                      </span>
                      <span className="font-semibold text-neutral-800 tabular-nums">{pcCount}</span>
                    </div>
                    <div className="flex items-center justify-between text-[14px]">
                      <span className="text-neutral-500 flex items-center gap-2">
                        <GraduationCap size={14} className="text-neutral-400" /> Representatives
                      </span>
                      <span className="font-semibold text-neutral-800 tabular-nums">{prCount}</span>
                    </div>
                  </div>

                  <div className="mt-6 pt-4 border-t border-white/40 flex items-center justify-end">
                    <button
                      type="button"
                      onClick={() => navigate(`/students?departmentId=${dept.id}`)}
                      className="flex items-center gap-1.5 text-[13px] font-semibold text-primary-500 hover:text-primary-600 transition-colors"
                    >
                      View Department <ArrowRight size={14} />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </PageContainer>
    </div>
  );
}