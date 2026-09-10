import { useState, useCallback, useEffect } from 'react';
import { userApi, studentApi, departmentApi } from '../../api/api';
import type { User, Department, StudentProfile } from '../../types';
import {
  Button,
  Select,
  Badge,
  Modal,
  DataTable,
  SearchInput,
  PageHeader,
  PageContainer,
  Dropdown,
  ConfirmDialog,
  ErrorState,
  EmptyState,
  Avatar,
  FilterToolbar,
} from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { Eye, Gauge, Shield, UserCheck, UserPlus, UserX } from 'lucide-react';
import toast from 'react-hot-toast';

const DEFAULT_PR_LIMIT = 5;

type PrRow = User & { registerNumber: string | null };

export default function PrManagementPage() {
  const [deptFilter, setDeptFilter] = useState('');
  const [studentSearch, setStudentSearch] = useState('');

  const [users, setUsers] = useState<User[]>([]);
  const [usersLoading, setUsersLoading] = useState(true);
  const [usersError, setUsersError] = useState<string | null>(null);

  const [profiles, setProfiles] = useState<StudentProfile[]>([]);
  const [profilesLoading, setProfilesLoading] = useState(true);
  const [profilesError, setProfilesError] = useState<string | null>(null);

  const [departments, setDepartments] = useState<Department[]>([]);

  const [promoteTarget, setPromoteTarget] = useState<User | null>(null);
  const [demoteTarget, setDemoteTarget] = useState<PrRow | null>(null);
  const [viewTarget, setViewTarget] = useState<PrRow | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const fetchUsers = useCallback(async () => {
    setUsersLoading(true);
    setUsersError(null);
    try {
      const res = await userApi.getAll({ size: 1000 });
      const payload = res.data.data;
      setUsers(Array.isArray(payload) ? payload : 'content' in payload ? payload.content : []);
    } catch (err) {
      setUsersError(getErrorMessage(err));
      setUsers([]);
    } finally {
      setUsersLoading(false);
    }
  }, []);

  const fetchProfiles = useCallback(async () => {
    setProfilesLoading(true);
    setProfilesError(null);
    try {
      const res = await studentApi.getAll({ size: 1000 });
      const payload = res.data.data;
      setProfiles(Array.isArray(payload) ? payload : 'content' in payload ? payload.content : []);
    } catch (err) {
      setProfilesError(getErrorMessage(err));
      setProfiles([]);
    } finally {
      setProfilesLoading(false);
    }
  }, []);

  const reload = useCallback(() => {
    fetchUsers();
    fetchProfiles();
  }, [fetchUsers, fetchProfiles]);

  useEffect(() => {
    reload();
  }, [reload]);

  useEffect(() => {
    departmentApi.getAll().then((res) => setDepartments(res.data.data)).catch(() => {});
  }, []);

  const loading = usersLoading || profilesLoading;
  const error = usersError || profilesError;
  const activeDepts = departments.filter((d) => d.active);
  const deptName = (id: number | null | undefined) =>
    departments.find((d) => d.id === id)?.name ?? null;

  const prs = users.filter((u) => u.role === 'PR');
  const studentUserIds = new Set(users.filter((u) => u.role === 'STUDENT').map((u) => u.id));

  const firstNameByUser = new Map<number, string>();
  for (const p of profiles) firstNameByUser.set(p.userId, p.userName || '');
  const registerNumberByUser = new Map<number, string | null>();
  for (const p of profiles) registerNumberByUser.set(p.userId, p.registerNumber || null);
  const cgpaByUser = new Map<number, number | null>();
  for (const p of profiles) cgpaByUser.set(p.userId, p.cgpa ?? null);

  const prRows: PrRow[] = prs.map((u) => ({
    ...u,
    registerNumber: registerNumberByUser.get(u.id) ?? null,
  }));

  const prLimitFor = (deptId: number | null | undefined) =>
    departments.find((d) => d.id === deptId)?.prLimit ?? DEFAULT_PR_LIMIT;

  const prCountByDept = new Map<number, number>();
  for (const p of prs) {
    if (p.departmentId != null) prCountByDept.set(p.departmentId, (prCountByDept.get(p.departmentId) ?? 0) + 1);
  }

  const filteredPrs =
    deptFilter && Number(deptFilter)
      ? prRows.filter((p) => p.departmentId === Number(deptFilter))
      : prRows;

  // Available students = student profiles whose user account is still a STUDENT
  const available = profiles.filter((p) => studentUserIds.has(p.userId));
  const filteredAvailable = available.filter((p) => {
    if (deptFilter && Number(deptFilter) && p.departmentId !== Number(deptFilter)) return false;
    const term = studentSearch.trim().toLowerCase();
    if (term) {
      const hay = `${p.userName} ${p.userEmail} ${p.registerNumber}`.toLowerCase();
      if (!hay.includes(term)) return false;
    }
    return true;
  });

  const hasStudentFilters = !!deptFilter || !!studentSearch;

  // Capacity for the selected filter
  const capTargetDept = deptFilter && Number(deptFilter) ? Number(deptFilter) : null;
  const capUsed = capTargetDept
    ? prCountByDept.get(capTargetDept) ?? 0
    : prs.length;
  const capMax = capTargetDept
    ? prLimitFor(capTargetDept)
    : activeDepts.reduce((sum, d) => sum + (d.prLimit ?? DEFAULT_PR_LIMIT), 0);
  const capPct = capMax > 0 ? Math.min(100, Math.round((capUsed / capMax) * 100)) : 0;
  const capLabel = capTargetDept
    ? `Representatives currently assigned in ${deptName(capTargetDept) ?? 'this department'}.`
    : `Aggregated across ${activeDepts.length} active departments.`;

  const isDeptFull = (deptId: number | null | undefined) =>
    deptId != null && (prCountByDept.get(deptId) ?? 0) >= prLimitFor(deptId);

  const handlePromote = async () => {
    if (!promoteTarget) return;
    setActionLoading(true);
    setActionError(null);
    try {
      await userApi.promotePr(promoteTarget.id);
      toast.success(`${promoteTarget.name} promoted to PR`);
      setPromoteTarget(null);
      reload();
    } catch (err) {
      const msg = getErrorMessage(err);
      setActionError(msg);
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDemote = async () => {
    if (!demoteTarget) return;
    setActionLoading(true);
    setActionError(null);
    try {
      await userApi.demoteStudent(demoteTarget.id);
      toast.success(`${demoteTarget.name} demoted to Student`);
      setDemoteTarget(null);
      reload();
    } catch (err) {
      const msg = getErrorMessage(err);
      setActionError(msg);
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  const clearFilters = () => {
    setDeptFilter('');
    setStudentSearch('');
  };

  return (
    <div className="bg-background min-h-screen">
      <PageContainer className="py-8">
        <PageHeader
          title="PR Management"
          description="Manage Placement Representatives by department."
        />

        <div className="glass rounded-[14px] border border-white/40 p-4 mb-6">
          <FilterToolbar
            filters={
              <div className="flex flex-wrap gap-3">
                <Select
                  options={activeDepts.map((d) => ({ label: d.name, value: d.id }))}
                  placeholder="All Departments"
                  value={deptFilter}
                  onChange={(e) => setDeptFilter(e.target.value)}
                  className="w-48"
                />
                {hasStudentFilters && (
                  <button
                    type="button"
                    onClick={clearFilters}
                    className="self-center px-3 py-2 text-[13px] font-medium text-primary-600 hover:text-primary-700 hover:bg-primary-50/60 rounded-[10px] transition-colors"
                  >
                    Clear filters
                  </button>
                )}
              </div>
            }
          />
        </div>

        {/* PR capacity */}
        <div className="glass rounded-[14px] border border-white/40 p-4 mb-6">
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <div className="flex items-center gap-2">
              <Gauge size={16} className="text-primary-600" />
              <span className="text-[15px] font-semibold text-neutral-900">PR Capacity</span>
            </div>
            <span className="text-[15px] font-semibold text-neutral-800 tabular-nums">
              {capUsed} of {capMax} assigned
            </span>
          </div>
          <div className="mt-3 h-2.5 rounded-full bg-neutral-200/70 overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-500 ${capPct >= 100 ? 'bg-danger-500' : 'bg-primary-500'}`}
              style={{ width: `${capPct}%` }}
            />
          </div>
          <p className="mt-2 text-[12.5px] text-neutral-400">{capLabel}</p>
        </div>

        {actionError && (
          <div className="mb-6 rounded-[12px] border border-danger-100 bg-danger-50/70 px-4 py-3 text-[13.5px] text-danger-700 flex items-start justify-between gap-3">
            <span className="leading-relaxed">{actionError}</span>
            <button
              type="button"
              onClick={() => setActionError(null)}
              className="shrink-0 p-0.5 rounded-[8px] text-danger-400 hover:text-danger-700 hover:bg-danger-100/60 transition-colors"
              aria-label="Dismiss error"
            >
              ✕
            </button>
          </div>
        )}

        {error ? (
          <ErrorState title="Unable to load representatives" message={error} onRetry={reload} />
        ) : (
          <>
            {/* Current representatives */}
            <div className="mb-3 flex items-center gap-2">
              <Shield size={16} className="text-primary-600" />
              <span className="text-[15px] font-semibold text-neutral-900">Current Representatives</span>
              <Badge variant="neutral">{filteredPrs.length}</Badge>
            </div>
            {filteredPrs.length === 0 && !loading ? (
              <div className="bg-white rounded-[14px] border border-neutral-200/60">
                <EmptyState
                  icon={<Shield size={22} />}
                  title="No representatives assigned"
                  description="Promote an eligible student to Placement Representative."
                />
              </div>
            ) : (
              <DataTable<PrRow>
                columns={[
                  {
                    key: 'name',
                    label: 'Representative',
                    render: (u) => (
                      <div className="flex items-center gap-3 min-w-0">
                        <Avatar name={u.name || 'U'} size="sm" />
                        <div className="min-w-0">
                          <p className="text-[14.5px] font-medium text-neutral-900 truncate">
                            {firstNameByUser.get(u.id) || u.name}
                          </p>
                          <p className="text-[12.5px] text-neutral-400 truncate">{u.email || '—'}</p>
                        </div>
                      </div>
                    ),
                  },
                  {
                    key: 'registerNumber',
                    label: 'Register No',
                    render: (u) => (
                      <span className="text-[14px] font-mono text-neutral-600 whitespace-nowrap">
                        {u.registerNumber || '—'}
                      </span>
                    ),
                  },
                  {
                    key: 'departmentName',
                    label: 'Department',
                    render: (u) =>
                      u.departmentName ? (
                        <Badge variant="neutral">{u.departmentName}</Badge>
                      ) : (
                        <span className="text-[14px] text-neutral-400">—</span>
                      ),
                  },
                  {
                    key: 'active',
                    label: 'Status',
                    render: (u) => (
                      <Badge variant={u.active ? 'success' : 'neutral'} dot>
                        {u.active ? 'Active' : 'Inactive'}
                      </Badge>
                    ),
                  },
                  {
                    key: 'actions',
                    label: '',
                    className: 'w-12',
                    render: (u) => (
                      <Dropdown
                        items={[
                          {
                            label: 'View',
                            icon: <Eye size={14} />,
                            onClick: () => setViewTarget(u),
                          },
                          {
                            label: 'Demote to Student',
                            icon: <UserX size={14} />,
                            danger: true,
                            onClick: () => setDemoteTarget(u),
                          },
                        ]}
                      />
                    ),
                  },
                ]}
                data={filteredPrs}
                rowKey={(u) => u.id}
                loading={loading}
                density="compact"
              />
            )}

            {/* Available students */}
            <div className="mt-8 mb-3 flex items-center gap-2 flex-wrap">
              <UserCheck size={16} className="text-primary-600" />
              <span className="text-[15px] font-semibold text-neutral-900">Available Students</span>
              <Badge variant="neutral">{filteredAvailable.length}</Badge>
              <div className="ml-auto w-64 max-w-full">
                <SearchInput
                  placeholder="Search students..."
                  value={studentSearch}
                  onChange={(e) => setStudentSearch(e.target.value)}
                />
              </div>
            </div>
            {profilesError ? (
              <ErrorState title="Unable to load students" message={profilesError} onRetry={fetchProfiles} />
            ) : filteredAvailable.length === 0 && !profilesLoading ? (
              <div className="bg-white rounded-[14px] border border-neutral-200/60">
                <EmptyState
                  icon={<UserCheck size={22} />}
                  title="No students found"
                  description={
                    hasStudentFilters
                      ? 'Try adjusting your filters or search.'
                      : 'There are no students available to promote right now.'
                  }
                />
              </div>
            ) : (
              <DataTable<StudentProfile>
                columns={[
                  {
                    key: 'userName',
                    label: 'Student',
                    render: (s) => (
                      <div className="flex items-center gap-3 min-w-0">
                        <Avatar name={s.userName || 'U'} size="sm" />
                        <div className="min-w-0">
                          <p className="text-[14.5px] font-medium text-neutral-900 truncate">{s.userName}</p>
                          <p className="text-[12.5px] text-neutral-400 truncate">{s.userEmail || '—'}</p>
                        </div>
                      </div>
                    ),
                  },
                  {
                    key: 'registerNumber',
                    label: 'Register No',
                    render: (s) => (
                      <span className="text-[14px] font-mono text-neutral-600 whitespace-nowrap">
                        {s.registerNumber || '—'}
                      </span>
                    ),
                  },
                  {
                    key: 'departmentName',
                    label: 'Department',
                    render: (s) =>
                      s.departmentName ? (
                        <Badge variant="neutral">{s.departmentName}</Badge>
                      ) : (
                        <span className="text-[14px] text-neutral-400">—</span>
                      ),
                  },
                  {
                    key: 'cgpa',
                    label: 'CGPA',
                    render: (s) => (
                      <span className="text-[14px] font-semibold text-neutral-800 tabular-nums">
                        {cgpaByUser.get(s.userId)?.toFixed(2) ?? '—'}
                      </span>
                    ),
                  },
                  {
                    key: 'actions',
                    label: '',
                    className: 'w-28',
                    render: (s) => {
                      const full = isDeptFull(s.departmentId);
                      return (
                        <div className="flex items-center gap-2">
                          <Button
                            variant="primary"
                            size="sm"
                            disabled={full}
                            title={full ? `PR capacity reached for ${deptName(s.departmentId) ?? 'this department'}` : 'Promote this student to Placement Representative'}
                            onClick={(e) => {
                              e.stopPropagation();
                              const u = users.find((x) => x.id === s.userId);
                              if (u) setPromoteTarget(u);
                            }}
                          >
                            <UserPlus size={14} />
                            Promote
                          </Button>
                          {full && (
                            <span className="text-[12px] text-neutral-400 whitespace-nowrap">
                              Full
                            </span>
                          )}
                        </div>
                      );
                    },
                  },
                ]}
                data={filteredAvailable}
                rowKey={(s) => s.id}
                loading={profilesLoading}
                density="compact"
              />
            )}
          </>
        )}

        <ConfirmDialog
          isOpen={!!promoteTarget}
          onClose={() => { setPromoteTarget(null); setActionError(null); }}
          onConfirm={handlePromote}
          title="Promote to PR"
          message={`Are you sure you want to promote ${promoteTarget?.name} to Placement Representative?`}
          confirmLabel="Promote"
          variant="primary"
          loading={actionLoading}
        />

        <ConfirmDialog
          isOpen={!!demoteTarget}
          onClose={() => { setDemoteTarget(null); setActionError(null); }}
          onConfirm={handleDemote}
          title="Demote to Student"
          message={`Are you sure you want to demote ${demoteTarget?.name} from Placement Representative? This will revoke their PR access.`}
          confirmLabel="Demote"
          variant="danger"
          loading={actionLoading}
        />

        <Modal isOpen={!!viewTarget} onClose={() => setViewTarget(null)} title="Representative Details">
          {viewTarget && (
            <div className="flex flex-col items-center text-center pb-1">
              <Avatar name={viewTarget.name || 'U'} size="lg" />
              <h3 className="mt-3 text-[17px] font-semibold text-neutral-900">{viewTarget.name}</h3>
              <p className="mt-0.5 text-[13.5px] text-neutral-500">{viewTarget.email}</p>
              <div className="mt-3 flex items-center gap-2 flex-wrap justify-center">
                <Badge variant="teal">PR</Badge>
                {viewTarget.departmentName ? (
                  <Badge variant="neutral">{viewTarget.departmentName}</Badge>
                ) : (
                  <Badge variant="neutral">Unassigned</Badge>
                )}
                <Badge variant={viewTarget.active ? 'success' : 'neutral'} dot>
                  {viewTarget.active ? 'Active' : 'Inactive'}
                </Badge>
              </div>
              <div className="mt-5 w-full rounded-[12px] bg-neutral-50/80 divide-y divide-neutral-100/70 text-left">
                <div className="flex justify-between px-4 py-2.5 text-[13.5px]">
                  <span className="text-neutral-500">Register No</span>
                  <span className="font-medium text-neutral-800 font-mono">{viewTarget.registerNumber || '—'}</span>
                </div>
                <div className="flex justify-between px-4 py-2.5 text-[13.5px]">
                  <span className="text-neutral-500">Department</span>
                  <span className="font-medium text-neutral-800">{deptName(viewTarget.departmentId) || '—'}</span>
                </div>
              </div>
            </div>
          )}
        </Modal>
      </PageContainer>
    </div>
  );
}