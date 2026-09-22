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
    <PageContainer>
        <PageHeader
          title="Departments"
          description="Academic departments and their placement responsibilities."
        />

        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div key={i} className="bg-white rounded-[14px] border border-neutral-200/80 shadow-soft p-4 space-y-3">
                <Skeleton className="h-5 w-32" />
                <Skeleton className="h-3 w-40" />
                <Skeleton className="h-3 w-28" />
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
          <div className="bg-white rounded-[14px] border border-neutral-200/80 shadow-soft">
            <EmptyState
              icon={<Building2 size={40} />}
              title="No departments available"
              description="Departments will appear here once created."
            />
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {departments.map((dept) => {
              const { pcCount, prCount, studentCount } = getDeptStats(dept.id);
              const fullName = DEPT_FULL_NAMES[dept.name.toUpperCase()] || `${dept.name} Department`;

              return (
                <div
                  key={dept.id}
                  className="bg-white rounded-[14px] border border-neutral-200/80 shadow-soft p-4 transition-colors duration-150 hover:border-primary-200 hover:shadow-raised group"
                >
                  <div className="flex items-start gap-3">
                    <div className="flex items-center justify-center w-9 h-9 rounded-[9px] bg-primary-50 text-primary-600 shrink-0">
                      <Building2 size={17} />
                    </div>
                    <div className="min-w-0">
                      <h3 className="text-[15px] font-semibold text-neutral-900 tracking-[-0.01em] group-hover:text-primary-600 transition-colors">
                        {dept.name}
                      </h3>
                      <p className="text-[12.5px] text-neutral-500 mt-0.5 leading-snug truncate">{fullName}</p>
                    </div>
                  </div>

                  <div className="mt-4 space-y-2">
                    <div className="flex items-center justify-between text-[13px]">
                      <span className="text-neutral-500 flex items-center gap-1.5">
                        <Users size={13} className="text-neutral-500" /> Students
                      </span>
                      <span className="font-semibold text-neutral-800 tabular-nums">{studentCount}</span>
                    </div>
                    <div className="flex items-center justify-between text-[13px]">
                      <span className="text-neutral-500 flex items-center gap-1.5">
                        <Shield size={13} className="text-neutral-500" /> Coordinators
                      </span>
                      <span className="font-semibold text-neutral-800 tabular-nums">{pcCount}</span>
                    </div>
                    <div className="flex items-center justify-between text-[13px]">
                      <span className="text-neutral-500 flex items-center gap-1.5">
                        <GraduationCap size={13} className="text-neutral-500" /> Representatives
                      </span>
                      <span className="font-semibold text-neutral-800 tabular-nums">{prCount}</span>
                    </div>
                  </div>

                  <div className="mt-4 pt-3 border-t border-neutral-100 flex items-center justify-end">
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
  );
}