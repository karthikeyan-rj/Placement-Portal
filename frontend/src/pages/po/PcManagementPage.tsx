import { useState, useCallback, useEffect } from 'react';
import { userApi, departmentApi } from '../../api/api';
import type { User, Department } from '../../types';
import {
  Button,
  Select,
  Badge,
  Modal,
  DataTable,
  Pagination,
  SearchInput,
  PageHeader,
  PageContainer,
  Dropdown,
  ErrorState,
  EmptyState,
  Avatar,
  Skeleton,
  FilterToolbar,
} from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { ArrowRightLeft, Eye, ShieldCheck, UserCheck, UserPlus } from 'lucide-react';
import toast from 'react-hot-toast';

const STATUS_OPTIONS = [
  { label: 'Active', value: 'active' },
  { label: 'Inactive', value: 'inactive' },
];

const CANDIDATE_ROLES = [
  { label: 'Students', value: 'STUDENT' },
  { label: 'PRs', value: 'PR' },
];

const MAX_PCS_PER_DEPT = 2;
const CANDIDATE_PAGE_SIZE = 20;

type AssignMode = 'new' | 'reassign';

export default function PcManagementPage() {
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [deptFilter, setDeptFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);

  const [pcs, setPcs] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Authoritative PC snapshot (all departments) drives capacity + "full" labels.
  const [allPcs, setAllPcs] = useState<User[]>([]);

  const [departments, setDepartments] = useState<Department[]>([]);
  const [deptLoading, setDeptLoading] = useState(true);
  const [deptError, setDeptError] = useState<string | null>(null);

  // Assign / reassign modal
  const [assignOpen, setAssignOpen] = useState(false);
  const [assignMode, setAssignMode] = useState<AssignMode>('new');
  const [assignUser, setAssignUser] = useState<User | null>(null);
  const [selectedCandidate, setSelectedCandidate] = useState<User | null>(null);
  const [assignDept, setAssignDept] = useState('');
  const [assigning, setAssigning] = useState(false);
  const [assignError, setAssignError] = useState<string | null>(null);

  // Candidate picker (server paginated + debounced search, no giant select)
  const [candRole, setCandRole] = useState<'STUDENT' | 'PR'>('STUDENT');
  const [candSearch, setCandSearch] = useState('');
  const [candDebounced, setCandDebounced] = useState('');
  const [candPage, setCandPage] = useState(0);
  const [candidates, setCandidates] = useState<User[]>([]);
  const [candLoading, setCandLoading] = useState(false);
  const [candError, setCandError] = useState<string | null>(null);
  const [candTotalPages, setCandTotalPages] = useState(0);
  const [candTotalElements, setCandTotalElements] = useState(0);

  const [viewTarget, setViewTarget] = useState<User | null>(null);

  const fetchPcs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await userApi.getAll({
        role: 'PC',
        departmentId: deptFilter && Number(deptFilter) ? Number(deptFilter) : undefined,
        search: debouncedSearch || undefined,
        page,
        size: 20,
      });
      const payload = res.data.data;
      const list = Array.isArray(payload) ? payload : 'content' in payload ? payload.content : [];
      setPcs(list);
      setTotalElements(Array.isArray(payload) ? list.length : payload.totalElements);
      setTotalPages(Array.isArray(payload) ? 1 : payload.totalPages);
    } catch (err) {
      setError(getErrorMessage(err));
      setPcs([]);
    } finally {
      setLoading(false);
    }
  }, [deptFilter, debouncedSearch, page]);

  const fetchAllPcs = useCallback(async () => {
    try {
      const res = await userApi.getAll({ role: 'PC', page: 0, size: 100 });
      const payload = res.data.data;
      setAllPcs(Array.isArray(payload) ? payload : 'content' in payload ? payload.content : []);
    } catch {
      setAllPcs([]);
    }
  }, []);

  const fetchCandidates = useCallback(async () => {
    setCandLoading(true);
    setCandError(null);
    try {
      const res = await userApi.getAll({
        role: candRole,
        search: candDebounced || undefined,
        page: candPage,
        size: CANDIDATE_PAGE_SIZE,
      });
      const payload = res.data.data;
      const list = Array.isArray(payload) ? payload : 'content' in payload ? payload.content : [];
      setCandidates(list);
      setCandTotalElements(Array.isArray(payload) ? list.length : payload.totalElements);
      setCandTotalPages(Array.isArray(payload) ? 1 : payload.totalPages);
    } catch (err) {
      setCandError(getErrorMessage(err));
      setCandidates([]);
    } finally {
      setCandLoading(false);
    }
  }, [candRole, candDebounced, candPage]);

  useEffect(() => {
    fetchPcs();
  }, [fetchPcs]);

  useEffect(() => {
    fetchAllPcs();
  }, [fetchAllPcs]);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    const timer = setTimeout(() => setCandDebounced(candSearch), 300);
    return () => clearTimeout(timer);
  }, [candSearch]);

  useEffect(() => {
    setPage(0);
  }, [deptFilter, debouncedSearch]);

  useEffect(() => {
    setCandPage(0);
  }, [candRole, candDebounced]);

  useEffect(() => {
    if (assignOpen && assignMode === 'new') fetchCandidates();
  }, [fetchCandidates, assignOpen, assignMode]);

  useEffect(() => {
    departmentApi
      .getAll()
      .then((res) => setDepartments(res.data.data))
      .catch((err) => setDeptError(getErrorMessage(err)))
      .finally(() => setDeptLoading(false));
  }, []);

  const pcCountByDept = new Map<number, number>();
  for (const u of allPcs) {
    if (u.departmentId != null) pcCountByDept.set(u.departmentId, (pcCountByDept.get(u.departmentId) ?? 0) + 1);
  }

  const activeDepts = departments.filter((d) => d.active);
  const deptName = (id: number | null | undefined) =>
    departments.find((d) => d.id === id)?.name ?? null;

  const filteredPcs = pcs.filter((u) => {
    if (statusFilter === 'active' && !u.active) return false;
    if (statusFilter === 'inactive' && u.active) return false;
    return true;
  });

  const hasActiveFilters = !!search || !!deptFilter || !!statusFilter;

  const clearFilters = () => {
    setSearch('');
    setDeptFilter('');
    setStatusFilter('');
  };

  const isDeptFull = (id: number, excludeCurrent: number | null | undefined) =>
    (pcCountByDept.get(id) ?? 0) >= MAX_PCS_PER_DEPT && id !== excludeCurrent;

  const openReassign = (u: User) => {
    setAssignMode('reassign');
    setAssignUser(u);
    setSelectedCandidate(null);
    setAssignDept(u.departmentId?.toString() ?? '');
    setAssignError(null);
    setAssignOpen(true);
  };

  const openAssignNew = () => {
    setAssignMode('new');
    setAssignUser(null);
    setSelectedCandidate(null);
    setAssignDept('');
    setAssignError(null);
    setCandSearch('');
    setCandDebounced('');
    setCandPage(0);
    setCandRole('STUDENT');
    setAssignOpen(true);
  };

  const assignOptions = activeDepts.map((d) => ({
    label: isDeptFull(d.id, assignUser?.departmentId)
      ? `${d.name} (${pcCountByDept.get(d.id) ?? 0}/${MAX_PCS_PER_DEPT} — Full)`
      : d.name,
    value: d.id,
  }));

  const handleAssign = async () => {
    const selectedDept = Number(assignDept);
    if (!selectedDept) return;
    const user = assignMode === 'reassign' ? assignUser : selectedCandidate;
    if (!user) {
      setAssignError('Please choose a user first.');
      return;
    }

    if (isDeptFull(selectedDept, user.departmentId)) {
      setAssignError(
        `This department already has ${pcCountByDept.get(selectedDept) ?? 0} coordinators (maximum ${MAX_PCS_PER_DEPT}). Reassign an existing coordinator out first.`
      );
      return;
    }

    setAssigning(true);
    setAssignError(null);
    try {
      await userApi.assignPc(user.id, selectedDept);
      toast.success(`${user.name} assigned as Coordinator to ${deptName(selectedDept) ?? 'department'}`);
      setAssignOpen(false);
      setSelectedCandidate(null);
      setAssignDept('');
      fetchAllPcs();
      fetchPcs();
    } catch (err) {
      setAssignError(getErrorMessage(err));
    } finally {
      setAssigning(false);
    }
  };

  return (
    <div className="bg-background min-h-screen">
      <PageContainer className="py-8">
        <PageHeader
          title="PC Management"
          description="Manage Placement Coordinators and department assignments."
          actions={
            <Button onClick={openAssignNew} className="flex items-center gap-1.5">
              <UserPlus size={16} /> Assign Coordinator
            </Button>
          }
        />

        <div className="glass rounded-[14px] border border-white/40 p-4 mb-4">
          <FilterToolbar
            search={
              <SearchInput
                placeholder="Search coordinators..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            }
            filters={
              <div className="flex flex-wrap gap-3">
                <Select
                  options={activeDepts.map((d) => ({ label: d.name, value: d.id }))}
                  placeholder="All Departments"
                  value={deptFilter}
                  onChange={(e) => setDeptFilter(e.target.value)}
                  className="w-48"
                />
                <Select
                  options={STATUS_OPTIONS}
                  placeholder="All Statuses"
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="w-40"
                />
                {hasActiveFilters && (
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

        <div className="mb-3 flex items-center gap-2">
          <UserCheck size={16} className="text-primary-600" />
          <span className="text-[15px] font-semibold text-neutral-900">Placement Coordinators</span>
          <Badge variant="neutral">{totalElements}</Badge>
        </div>

        {error ? (
          <ErrorState title="Unable to load coordinators" message={error} onRetry={fetchPcs} />
        ) : filteredPcs.length === 0 && !loading ? (
          <div className="bg-white rounded-[14px] border border-neutral-200/60">
            <EmptyState
              icon={<UserPlus size={22} />}
              title={hasActiveFilters ? 'No coordinators match your filters' : 'No coordinators found'}
              description={
                hasActiveFilters
                  ? 'Try adjusting your search or filters.'
                  : 'Assign a Placement Coordinator to get started.'
              }
            />
          </div>
        ) : (
          <>
            <DataTable<User>
              columns={[
                {
                  key: 'name',
                  label: 'Coordinator',
                  render: (u) => (
                    <div className="flex items-center gap-3 min-w-0">
                      <Avatar name={u.name || 'U'} size="sm" />
                      <div className="min-w-0">
                        <p className="text-[14.5px] font-medium text-neutral-900 truncate">{u.name}</p>
                        <p className="text-[12.5px] text-neutral-400 truncate">{u.email || '—'}</p>
                      </div>
                    </div>
                  ),
                },
                {
                  key: 'email',
                  label: 'Email',
                  render: (u) => (
                    <span className="text-[13.5px] text-neutral-500 whitespace-nowrap">{u.email}</span>
                  ),
                },
                {
                  key: 'departmentName',
                  label: 'Department',
                  render: (u) =>
                    u.departmentName ? (
                      <Badge variant="neutral">{u.departmentName}</Badge>
                    ) : (
                      <span className="text-[14px] text-neutral-400">Unassigned</span>
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
                          label: 'Reassign Department',
                          icon: <ArrowRightLeft size={14} />,
                          onClick: () => openReassign(u),
                        },
                      ]}
                    />
                  ),
                },
              ]}
              data={filteredPcs}
              rowKey={(u) => u.id}
              loading={loading}
              density="compact"
            />
            {totalPages > 1 && (
              <div className="mt-4">
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  totalElements={totalElements}
                  pageSize={20}
                  onPageChange={setPage}
                />
              </div>
            )}
          </>
        )}

        {/* Assign / reassign modal */}
        <Modal
          isOpen={assignOpen}
          onClose={() => setAssignOpen(false)}
          title={assignMode === 'new' ? 'Assign Coordinator' : 'Reassign Department'}
          description={
            assignMode === 'new'
              ? 'Search for a user and select a department to make them a Placement Coordinator.'
              : `Choose a new department for ${assignUser?.name || 'this coordinator'}.`
          }
          size="lg"
          actions={
            <>
              <Button variant="secondary" onClick={() => setAssignOpen(false)} disabled={assigning}>
                Cancel
              </Button>
              <Button onClick={handleAssign} disabled={assigning} loading={assigning}>
                {assignMode === 'new' ? 'Assign Coordinator' : 'Reassign Department'}
              </Button>
            </>
          }
        >
          {assignMode === 'new' && (
            <div className="mb-4">
              <div className="flex items-center justify-between gap-3 mb-2 flex-wrap">
                <Select
                  label="Candidate roles"
                  options={CANDIDATE_ROLES}
                  value={candRole}
                  onChange={(e) => setCandRole(e.target.value as 'STUDENT' | 'PR')}
                  className="w-40"
                />
                <div className="w-56 max-w-full">
                  <SearchInput
                    placeholder="Search by name or email..."
                    value={candSearch}
                    onChange={(e) => setCandSearch(e.target.value)}
                  />
                </div>
              </div>
              <div className="rounded-[12px] border border-neutral-200/60 bg-white overflow-hidden">
                {candLoading ? (
                  <div className="p-3 space-y-2">
                    {Array.from({ length: 5 }).map((_, i) => (
                      <Skeleton key={i} className="h-10 w-full" />
                    ))}
                  </div>
                ) : candError ? (
                  <p className="px-4 py-6 text-center text-[13.5px] text-danger-600">{candError}</p>
                ) : candidates.length === 0 ? (
                  <p className="px-4 py-8 text-center text-[13.5px] text-neutral-400">
                    No candidates match your search.
                  </p>
                ) : (
                  <div className="divide-y divide-neutral-100/70 max-h-[280px] overflow-y-auto">
                    {candidates.map((u) => {
                      const selected = selectedCandidate?.id === u.id;
                      return (
                        <button
                          key={u.id}
                          type="button"
                          onClick={() => setSelectedCandidate(u)}
                          className={`w-full flex items-center gap-3 px-4 py-2.5 text-left transition-colors ${
                            selected ? 'bg-primary-50/60' : 'hover:bg-neutral-50/60'
                          }`}
                        >
                          <Avatar name={u.name || 'U'} size="sm" />
                          <div className="min-w-0 flex-1">
                            <p className="text-[14px] font-medium text-neutral-900 truncate">{u.name}</p>
                            <p className="text-[12.5px] text-neutral-400 truncate">{u.email || '—'}</p>
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            <span className="text-[12px] text-neutral-400">
                              {deptName(u.departmentId) ?? 'No department'}
                            </span>
                            <Badge variant={u.role === 'PR' ? 'teal' : 'neutral'}>{u.role}</Badge>
                            {selected && <Badge variant="success">Selected</Badge>}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                )}
                {candTotalPages > 1 && (
                  <div className="border-t border-neutral-100/80 p-2">
                    <Pagination
                      page={candPage}
                      totalPages={candTotalPages}
                      totalElements={candTotalElements}
                      pageSize={CANDIDATE_PAGE_SIZE}
                      onPageChange={setCandPage}
                    />
                  </div>
                )}
              </div>
            </div>
          )}

          <div className={assignMode === 'new' ? '' : 'pt-1'}>
            {deptLoading ? (
              <Skeleton className="h-[44px] w-full" />
            ) : deptError ? (
              <p className="text-[14px] text-danger-600">{deptError}</p>
            ) : (
              <>
                <Select
                  label="Department"
                  placeholder="Select a department"
                  options={assignOptions}
                  value={assignDept}
                  onChange={(e) => setAssignDept(e.target.value)}
                  required
                />
                {assignDept && (
                  <p className="mt-2 text-[12.5px] text-neutral-400">
                    {pcCountByDept.get(Number(assignDept)) ?? 0} of {MAX_PCS_PER_DEPT} coordinator
                    slots used in this department.
                  </p>
                )}
              </>
            )}
          </div>

          {assignError && (
            <p className="mt-4 rounded-[10px] bg-danger-50/70 px-3.5 py-2.5 text-[13.5px] text-danger-700 leading-relaxed">
              {assignError}
            </p>
          )}
        </Modal>

        {/* View modal */}
        <Modal
          isOpen={!!viewTarget}
          onClose={() => setViewTarget(null)}
          title="Coordinator Details"
        >
          {viewTarget && (
            <div className="flex flex-col items-center text-center pb-1">
              <Avatar name={viewTarget.name || 'U'} size="lg" />
              <h3 className="mt-3 text-[17px] font-semibold text-neutral-900">{viewTarget.name}</h3>
              <p className="mt-0.5 text-[13.5px] text-neutral-500">{viewTarget.email}</p>
              <div className="mt-3 flex items-center gap-2">
                <Badge variant="teal">
                  <ShieldCheck size={12} /> Coordinator
                </Badge>
                {viewTarget.departmentName ? (
                  <Badge variant="neutral">{viewTarget.departmentName}</Badge>
                ) : (
                  <Badge variant="neutral">Unassigned</Badge>
                )}
                <Badge variant={viewTarget.active ? 'success' : 'neutral'} dot>
                  {viewTarget.active ? 'Active' : 'Inactive'}
                </Badge>
              </div>
            </div>
          )}
        </Modal>
      </PageContainer>
    </div>
  );
}