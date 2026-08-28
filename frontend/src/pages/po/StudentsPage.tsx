import { useState, useCallback, useEffect } from 'react';
import { studentApi, authApi, departmentApi } from '../../api/api';
import type { StudentProfile, Department } from '../../types';
import {
  Badge,
  DataTable,
  Pagination,
  PageHeader,
  PageContainer,
  SearchInput,
  ErrorState,
  FilterToolbar,
  Button,
  Select,
  Input,
  Modal,
} from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { useSearchParams } from 'react-router-dom';
import { Users, UserPlus } from 'lucide-react';
import toast from 'react-hot-toast';

export default function StudentsPage() {
  const [searchParams] = useSearchParams();
  const initialDeptId = searchParams.get('departmentId') || '';

  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [students, setStudents] = useState<StudentProfile[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Filters state
  const [deptFilter, setDeptFilter] = useState(initialDeptId);
  const [interestFilter, setInterestFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  // Department options for filter and modal
  const [departments, setDepartments] = useState<Department[]>([]);

  // Add Student modal state
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    name: '',
    registerNumber: '',
    email: '',
    departmentId: '',
    batch: '',
    password: 'password123',
  });

  const fetchDepartments = useCallback(async () => {
    try {
      const res = await departmentApi.getAll();
      setDepartments(res.data.data || []);
    } catch (err) {
      console.error('Failed to load departments', err);
    }
  }, []);

  const fetchStudents = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await studentApi.getAll({
        search: search || undefined,
        departmentId: deptFilter ? Number(deptFilter) : undefined,
        page,
        size: 20,
      });
      const payload = res.data.data;
      if (Array.isArray(payload)) {
        setStudents(payload);
        setTotalPages(1);
        setTotalElements(payload.length);
      } else if (payload && typeof payload === 'object' && 'content' in payload) {
        setStudents(payload.content);
        setTotalPages(payload.totalPages);
        setTotalElements(payload.totalElements);
      }
    } catch (err) {
      setError(getErrorMessage(err));
      setStudents([]);
    } finally {
      setLoading(false);
    }
  }, [search, deptFilter, page]);

  useEffect(() => {
    fetchStudents();
  }, [fetchStudents]);

  useEffect(() => {
    fetchDepartments();
  }, [fetchDepartments]);

  useEffect(() => {
    setPage(0);
  }, [search, deptFilter, interestFilter, statusFilter]);

  const handleAddStudent = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name || !form.registerNumber || !form.email || !form.departmentId || !form.password) {
      toast.error('Please fill in all required fields.');
      return;
    }
    setSubmitting(true);
    try {
      await authApi.register({
        name: form.name.trim(),
        registerNumber: form.registerNumber.trim(),
        email: form.email.trim(),
        departmentId: Number(form.departmentId),
        batch: form.batch || undefined,
        password: form.password,
      });
      toast.success('Student added successfully.');
      setAddModalOpen(false);
      setForm({
        name: '',
        registerNumber: '',
        email: '',
        departmentId: '',
        batch: '',
        password: 'password123',
      });
      fetchStudents();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const getStatusBadge = (status: string | null) => {
    switch (status) {
      case 'PLACED':
        return <Badge variant="teal" dot>Placed</Badge>;
      case 'INTERVIEWING':
        return <Badge variant="warning" dot>Interviewing</Badge>;
      case 'INTERESTED':
        return <Badge variant="info" dot>Interested</Badge>;
      default:
        return <Badge variant="neutral" dot>Not Placed</Badge>;
    }
  };

  // Local client-side filters for Interest and Status (since backend doesn't support them directly in pagination params)
  const filteredStudents = students.filter((s) => {
    if (interestFilter) {
      const isInterested = s.placementInterested === true;
      if (interestFilter === 'INTERESTED' && !isInterested) return false;
      if (interestFilter === 'NOT_INTERESTED' && isInterested) return false;
    }
    if (statusFilter) {
      const status = s.placementStatus || '';
      if (statusFilter === 'NOT_PLACED') {
        if (status === 'PLACED' || status === 'INTERVIEWING' || status === 'INTERESTED') return false;
      } else if (status !== statusFilter) {
        return false;
      }
    }
    return true;
  });

  const currentYear = new Date().getFullYear();
  const batchOptions = [currentYear, currentYear + 1, currentYear + 2, currentYear + 3].map(
    (y) => ({ label: String(y), value: String(y) })
  );

  return (
    <PageContainer>
      <PageHeader
        title="Students"
        description="Manage student profiles, academics and placement progress."
        actions={
          <Button onClick={() => setAddModalOpen(true)} className="flex items-center gap-1.5">
            <UserPlus size={16} /> Add Student
          </Button>
        }
      />

      <FilterToolbar
        search={
          <SearchInput
            placeholder="Search students..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
        filters={
          <div className="flex flex-wrap gap-3">
            <Select
              options={departments.map((d) => ({ label: d.name, value: d.id }))}
              placeholder="All Departments"
              value={deptFilter}
              onChange={(e) => setDeptFilter(e.target.value)}
              className="w-48"
            />
            <Select
              options={[
                { label: 'Interested', value: 'INTERESTED' },
                { label: 'Not Interested', value: 'NOT_INTERESTED' },
              ]}
              placeholder="All Interests"
              value={interestFilter}
              onChange={(e) => setInterestFilter(e.target.value)}
              className="w-40"
            />
            <Select
              options={[
                { label: 'Placed', value: 'PLACED' },
                { label: 'Interviewing', value: 'INTERVIEWING' },
                { label: 'Interested', value: 'INTERESTED' },
                { label: 'Not Placed', value: 'NOT_PLACED' },
              ]}
              placeholder="All Statuses"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="w-40"
            />
          </div>
        }
        children={
          !loading && (
            <span className="text-[14px] text-neutral-500">
              {filteredStudents.length !== students.length
                ? `${filteredStudents.length} of ${totalElements} students`
                : `${totalElements} ${totalElements === 1 ? 'student' : 'students'}`}
            </span>
          )
        }
      />

      {error ? (
        <ErrorState title="Unable to load students" message={error} onRetry={fetchStudents} />
      ) : (
        <>
          <DataTable<StudentProfile>
            columns={[
              {
                key: 'userName',
                label: 'Student',
                render: (s) => (
                  <div className="flex items-center gap-3">
                    <div className="flex items-center justify-center w-9 h-9 rounded-full bg-neutral-100 text-[12px] font-semibold text-neutral-600 shrink-0">
                      {s.userName
                        ? s.userName.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2)
                        : '--'}
                    </div>
                    <span className="text-[15px] font-medium text-neutral-900">{s.userName}</span>
                  </div>
                ),
              },
              {
                key: 'registerNumber',
                label: 'Register No',
                render: (s) => (
                  <span className="text-[14px] text-neutral-600">{s.registerNumber || '—'}</span>
                ),
              },
              {
                key: 'departmentName',
                label: 'Department',
                render: (s) =>
                  s.departmentName ? (
                    <Badge variant="department">{s.departmentName}</Badge>
                  ) : (
                    <span className="text-[14px] text-neutral-400">—</span>
                  ),
              },
              {
                key: 'cgpa',
                label: 'CGPA',
                render: (s) => (
                  <span className="text-[14px] font-medium text-neutral-900">
                    {s.cgpa?.toFixed(2) ?? '—'}
                  </span>
                ),
              },
              {
                key: 'activeBacklogs',
                label: 'Backlogs',
                render: (s) => (
                  <span className="text-[14px] text-neutral-600">{s.activeBacklogs ?? 0}</span>
                ),
              },
              {
                key: 'placementStatus',
                label: 'Status',
                render: (s) => getStatusBadge(s.placementStatus),
              },
              {
                key: 'batch',
                label: 'Batch',
                render: (s) => (
                  <span className="text-[14px] text-neutral-600">{s.batch || '—'}</span>
                ),
              },
            ]}
            data={filteredStudents}
            rowKey={(s) => s.id}
            loading={loading}
            emptyMessage="No students found"
            emptyIcon={<Users size={40} />}
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

      {/* Add Student Modal */}
      <Modal
        isOpen={addModalOpen}
        onClose={() => setAddModalOpen(false)}
        title="Add Student"
        description="Create a new student account and profile."
        size="md"
        actions={
          <>
            <Button variant="secondary" onClick={() => setAddModalOpen(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={handleAddStudent} loading={submitting}>
              Create Student
            </Button>
          </>
        }
      >
        <form onSubmit={handleAddStudent} className="space-y-4">
          <Input
            label="Full Name"
            required
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="John Doe"
          />
          <Input
            label="Register Number"
            required
            value={form.registerNumber}
            onChange={(e) => setForm({ ...form, registerNumber: e.target.value })}
            placeholder="e.g., 22CS001"
          />
          <Input
            label="Email"
            required
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            placeholder="john.doe@college.edu"
          />
          <div className="grid grid-cols-2 gap-4">
            <Select
              label="Department"
              required
              options={departments.map((d) => ({ label: d.name, value: d.id }))}
              placeholder="Select department"
              value={form.departmentId}
              onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
            />
            <Select
              label="Batch / Graduation Year"
              options={batchOptions}
              placeholder="Select year"
              value={form.batch}
              onChange={(e) => setForm({ ...form, batch: e.target.value })}
            />
          </div>
          <Input
            label="Default Password"
            required
            type="password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            placeholder="Enter temporary password"
          />
        </form>
      </Modal>
    </PageContainer>
  );
}
