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
  FilterToolbar,
  Skeleton,
} from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { ArrowRightLeft, UserCheck, UserPlus } from 'lucide-react';
import toast from 'react-hot-toast';

export default function PcManagementPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [departments, setDepartments] = useState<Department[]>([]);
  const [deptLoading, setDeptLoading] = useState(true);
  const [deptError, setDeptError] = useState<string | null>(null);

  const [assignTarget, setAssignTarget] = useState<User | null>(null);
  const [selectedDept, setSelectedDept] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await userApi.getAll({ search: search || undefined, page, size: 20 });
      const payload = res.data.data;
      if (Array.isArray(payload)) {
        setUsers(payload);
        setTotalPages(1);
        setTotalElements(payload.length);
      } else if (payload && typeof payload === 'object' && 'content' in payload) {
        setUsers(payload.content);
        setTotalPages(payload.totalPages);
        setTotalElements(payload.totalElements);
      }
    } catch (err) {
      setError(getErrorMessage(err));
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [search, page]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  useEffect(() => {
    departmentApi.getAll()
      .then((res) => setDepartments(res.data.data))
      .catch((err) => setDeptError(getErrorMessage(err)))
      .finally(() => setDeptLoading(false));
  }, []);

  useEffect(() => {
    setPage(0);
  }, [search]);

  const pcs = users.filter((u) => u.role === 'PC');

  const handleAssign = async () => {
    if (!assignTarget || !selectedDept) return;
    setActionLoading(true);
    try {
      await userApi.assignPc(assignTarget.id, Number(selectedDept));
      toast.success(`${assignTarget.name} assigned successfully`);
      setAssignTarget(null);
      fetchUsers();
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Failed to assign PC');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <PageContainer>
      <PageHeader
        title="PC Management"
        description="Manage and assign Placement Coordinators to academic departments."
      />

      <FilterToolbar
        search={
          <SearchInput
            placeholder="Search coordinators by name or email..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
      />

      {error ? (
        <ErrorState
          title="Failed to load coordinators"
          message={error}
          onRetry={fetchUsers}
        />
      ) : (
        <>
          <div className="mb-3 flex items-center gap-2">
            <UserCheck size={16} className="text-primary-600" />
            <span className="text-[15px] font-semibold text-neutral-900">Placement Coordinators</span>
            <Badge variant="neutral">{pcs.length}</Badge>
          </div>
          <DataTable<User>
            columns={[
              {
                key: 'name',
                label: 'Coordinator',
                render: (u) => (
                  <div className="flex items-center gap-3">
                    <div className="flex items-center justify-center w-9 h-9 rounded-full bg-neutral-100 text-[12px] font-semibold text-neutral-600 shrink-0">
                      {u.name
                        ? u.name.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2)
                        : '--'}
                    </div>
                    <span className="text-[15px] font-medium text-neutral-900">{u.name}</span>
                  </div>
                ),
              },
              {
                key: 'email',
                label: 'Email',
                render: (u) => (
                  <span className="text-[14px] text-neutral-500">{u.email}</span>
                ),
              },
              {
                key: 'departmentName',
                label: 'Department',
                render: (u) =>
                  u.departmentName ? (
                    <Badge variant="department">{u.departmentName}</Badge>
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
                        label: 'Reassign Department',
                        icon: <ArrowRightLeft size={14} />,
                        onClick: () => {
                          setAssignTarget(u);
                          setSelectedDept(u.departmentId?.toString() || '');
                        },
                      },
                    ]}
                  />
                ),
              },
            ]}
            data={pcs}
            rowKey={(u) => u.id}
            loading={loading}
            emptyMessage="No Placement Coordinators found"
            emptyIcon={<UserPlus size={40} />}
          />
          <div className="mt-6">
            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={20}
              onPageChange={setPage}
            />
          </div>
        </>
      )}

      <Modal
        isOpen={!!assignTarget}
        onClose={() => setAssignTarget(null)}
        title="Assign Department"
        description={`Select a department for ${assignTarget?.name || 'this coordinator'}.`}
        actions={
          <>
            <Button variant="secondary" onClick={() => setAssignTarget(null)} disabled={actionLoading}>
              Cancel
            </Button>
            <Button onClick={handleAssign} disabled={!selectedDept || actionLoading} loading={actionLoading}>
              Assign Department
            </Button>
          </>
        }
      >
        {deptLoading ? (
          <Skeleton className="h-[44px] w-full" />
        ) : deptError ? (
          <p className="text-[14px] text-danger-600">{deptError}</p>
        ) : (
          <Select
            label="Department"
            options={departments.map((d) => ({ label: d.name, value: d.id }))}
            placeholder="Select a department"
            value={selectedDept}
            onChange={(e) => setSelectedDept(e.target.value)}
          />
        )}
      </Modal>
    </PageContainer>
  );
}
