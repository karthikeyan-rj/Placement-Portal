import { useState, useCallback, useEffect } from 'react';
import { userApi } from '../../api/api';
import type { User } from '../../types';
import { Button, Badge, DataTable, Pagination, SearchInput, ConfirmDialog, PageHeader, PageContainer, ErrorState, FilterToolbar } from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { Shield, UserCheck, UserPlus, UserX } from 'lucide-react';
import toast from 'react-hot-toast';

export default function PrManagementPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [promoteTarget, setPromoteTarget] = useState<User | null>(null);
  const [demoteTarget, setDemoteTarget] = useState<User | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await userApi.getAll({ search: search || undefined, page, size: 50 });
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
    setPage(0);
  }, [search]);

  const prs = users.filter((u) => u.role === 'PR');
  const students = users.filter((u) => u.role === 'STUDENT');

  const handlePromote = async () => {
    if (!promoteTarget) return;
    setActionLoading(true);
    try {
      await userApi.promotePr(promoteTarget.id);
      toast.success(`${promoteTarget.name} promoted to PR`);
      setPromoteTarget(null);
      fetchUsers();
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Failed to promote');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDemote = async () => {
    if (!demoteTarget) return;
    setActionLoading(true);
    try {
      await userApi.demoteStudent(demoteTarget.id);
      toast.success(`${demoteTarget.name} demoted to Student`);
      setDemoteTarget(null);
      fetchUsers();
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Failed to demote');
    } finally {
      setActionLoading(false);
    }
  };

  if (error) {
    return (
      <PageContainer>
        <PageHeader title="PR Management" description="Promote students to Placement Representatives." />
        <ErrorState title="Unable to load users" message={error} onRetry={fetchUsers} />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        title="PR Management"
        description="Manage Placement Representatives and department capacity."
      />

      <FilterToolbar
        search={
          <SearchInput
            placeholder="Search by name or email..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
      />

      <div className="mb-8">
        <div className="flex items-center gap-2 mb-4">
          <Shield size={16} className="text-primary-600" />
          <h2 className="text-[16px] font-semibold text-neutral-900">Current Representatives</h2>
          <Badge variant="neutral">{prs.length}</Badge>
        </div>
        <DataTable<User>
          columns={[
            {
              key: 'name',
              label: 'Representative',
              render: (u) => (
                <div className="flex items-center gap-3">
                  <div className="flex items-center justify-center w-9 h-9 rounded-full bg-neutral-100 text-[12px] font-semibold text-neutral-600 shrink-0">
                    {u.name ? u.name.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2) : '--'}
                  </div>
                  <span className="text-[15px] font-medium text-neutral-900">{u.name}</span>
                </div>
              ),
            },
            {
              key: 'departmentName',
              label: 'Department',
              render: (u) => (
                u.departmentName ? (
                  <Badge variant="department">{u.departmentName}</Badge>
                ) : (
                  <span className="text-[14px] text-neutral-400">—</span>
                )
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
              className: 'w-28',
              render: (u) => (
                <Button
                  variant="outline-danger"
                  size="sm"
                  onClick={(e) => {
                    e.stopPropagation();
                    setDemoteTarget(u);
                  }}
                >
                  <UserX size={14} />
                  Demote
                </Button>
              ),
            },
          ]}
          data={prs}
          rowKey={(u) => u.id}
          loading={loading}
          emptyMessage="No Placement Representatives found"
          emptyIcon={<Shield size={40} />}
        />
      </div>

      <div>
        <div className="flex items-center gap-2 mb-4">
          <UserCheck size={16} className="text-primary-600" />
          <h2 className="text-[16px] font-semibold text-neutral-900">Available Students</h2>
          <Badge variant="neutral">{students.length}</Badge>
        </div>
        <DataTable<User>
          columns={[
            {
              key: 'name',
              label: 'Student',
              render: (u) => (
                <div className="flex items-center gap-3">
                  <div className="flex items-center justify-center w-9 h-9 rounded-full bg-neutral-100 text-[12px] font-semibold text-neutral-600 shrink-0">
                    {u.name ? u.name.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2) : '--'}
                  </div>
                  <span className="text-[15px] font-medium text-neutral-900">{u.name}</span>
                </div>
              ),
            },
            {
              key: 'departmentName',
              label: 'Department',
              render: (u) => (
                u.departmentName ? (
                  <Badge variant="department">{u.departmentName}</Badge>
                ) : (
                  <span className="text-[14px] text-neutral-400">—</span>
                )
              ),
            },
            {
              key: 'actions',
              label: '',
              className: 'w-36',
              render: (u) => (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={(e) => {
                    e.stopPropagation();
                    setPromoteTarget(u);
                  }}
                >
                  <UserPlus size={14} />
                  Promote to PR
                </Button>
              ),
            },
          ]}
          data={students}
          rowKey={(u) => u.id}
          loading={loading}
          emptyMessage="No students found to promote"
          emptyIcon={<UserCheck size={40} />}
        />
      </div>

      <div className="mt-6">
        <Pagination
          page={page}
          totalPages={totalPages}
          totalElements={totalElements}
          pageSize={50}
          onPageChange={setPage}
        />
      </div>

      <ConfirmDialog
        isOpen={!!promoteTarget}
        onClose={() => setPromoteTarget(null)}
        onConfirm={handlePromote}
        title="Promote to PR"
        message={`Are you sure you want to promote ${promoteTarget?.name} to Placement Representative?`}
        confirmLabel="Promote"
        variant="primary"
        loading={actionLoading}
      />

      <ConfirmDialog
        isOpen={!!demoteTarget}
        onClose={() => setDemoteTarget(null)}
        onConfirm={handleDemote}
        title="Demote to Student"
        message={`Are you sure you want to demote ${demoteTarget?.name} from Placement Representative? This will revoke their PR privileges.`}
        confirmLabel="Demote"
        loading={actionLoading}
      />
    </PageContainer>
  );
}
