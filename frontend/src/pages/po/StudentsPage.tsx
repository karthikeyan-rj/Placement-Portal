import { useState, useCallback, useEffect, useRef } from 'react';
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
  Avatar,
  formatStatus,
} from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { useSearchParams } from 'react-router-dom';
import { useEffectiveRole } from '../../hooks/useEffectiveRole';
import { Users, UserPlus, MoreVertical, Pencil } from 'lucide-react';
import toast from 'react-hot-toast';

const STATUS_OPTIONS = [
  { label: 'Placed', value: 'PLACED' },
  { label: 'Not Placed', value: 'NOT_PLACED' },
  { label: 'Blocked', value: 'BLOCKED' },
];

const INTEREST_OPTIONS = [
  { label: 'Interested', value: 'INTERESTED' },
  { label: 'Not Interested', value: 'NOT_INTERESTED' },
];

interface EditForm {
  phone: string;
  dateOfBirth: string;
  batch: string;
  section: string;
}

interface AcademicForm {
  cgpa: string;
  tenthPercentage: string;
  twelfthPercentage: string;
  diplomaPercentage: string;
  activeBacklogs: string;
  historyOfBacklogs: string;
}

const EMPTY_ACADEMIC: AcademicForm = {
  cgpa: '',
  tenthPercentage: '',
  twelfthPercentage: '',
  diplomaPercentage: '',
  activeBacklogs: '',
  historyOfBacklogs: '',
};

export default function StudentsPage() {
  const [searchParams] = useSearchParams();
  const initialDeptId = searchParams.get('departmentId') || '';
  const effectiveRole = useEffectiveRole();
  const canEdit = effectiveRole === 'PO' || effectiveRole === 'PC';

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [page, setPage] = useState(0);
  const [students, setStudents] = useState<StudentProfile[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [deptFilter, setDeptFilter] = useState(initialDeptId);
  const [interestFilter, setInterestFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [departments, setDepartments] = useState<Department[]>([]);

  const [addModalOpen, setAddModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    registerNumber: '',
    email: '',
    accessCode: '',
    password: 'password123',
  });

  const [editOpen, setEditOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<StudentProfile | null>(null);
  const [editForm, setEditForm] = useState<EditForm>({ phone: '', dateOfBirth: '', batch: '', section: '' });
  const [academic, setAcademic] = useState<AcademicForm>(EMPTY_ACADEMIC);
  const [saving, setSaving] = useState(false);

  const [menu, setMenu] = useState<{ student: StudentProfile; x: number; y: number } | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);

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
        search: debouncedSearch || undefined,
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
  }, [debouncedSearch, deptFilter, page]);

  useEffect(() => {
    fetchStudents();
  }, [fetchStudents]);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    fetchDepartments();
  }, [fetchDepartments]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, deptFilter]);

  useEffect(() => {
    if (!menu) return;
    const onDown = (e: MouseEvent) => {
      if (menuRef.current && menuRef.current.contains(e.target as Node)) return;
      setMenu(null);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenu(null);
    };
    const onClose = () => setMenu(null);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    window.addEventListener('resize', onClose);
    window.addEventListener('scroll', onClose, true);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
      window.removeEventListener('resize', onClose);
      window.removeEventListener('scroll', onClose, true);
    };
  }, [menu]);

  const openMenu = (e: React.MouseEvent<HTMLButtonElement>, s: StudentProfile) => {
    e.stopPropagation();
    const rect = e.currentTarget.getBoundingClientRect();
    const w = 208;
    const h = 48;
    let x = rect.right - w;
    let y = rect.bottom + 6;
    if (x < 8) x = 8;
    if (x + w > window.innerWidth - 8) x = window.innerWidth - w - 8;
    if (y + h > window.innerHeight - 8) y = rect.top - h - 6;
    setMenu({ student: s, x, y });
  };

  const openEditModal = (s: StudentProfile) => {
    setEditTarget(s);
    setEditForm({
      phone: s.phone ?? '',
      dateOfBirth: s.dateOfBirth ? s.dateOfBirth.slice(0, 10) : '',
      batch: s.batch ?? '',
      section: s.section ?? '',
    });
    setAcademic({
      cgpa: s.cgpa != null ? String(s.cgpa) : '',
      tenthPercentage: s.tenthPercentage != null ? String(s.tenthPercentage) : '',
      twelfthPercentage: s.twelfthPercentage != null ? String(s.twelfthPercentage) : '',
      diplomaPercentage: s.diplomaPercentage != null ? String(s.diplomaPercentage) : '',
      activeBacklogs: s.activeBacklogs != null ? String(s.activeBacklogs) : '',
      historyOfBacklogs: s.historyOfBacklogs != null ? String(s.historyOfBacklogs) : '',
    });
    setEditOpen(true);
  };

  const handleAddStudent = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.registerNumber || !form.email || !form.accessCode || !form.password) {
      toast.error('Please fill in all required fields.');
      return;
    }
    setSubmitting(true);
    try {
      await authApi.register({
        registerNumber: form.registerNumber.trim(),
        email: form.email.trim(),
        accessCode: form.accessCode.trim(),
        password: form.password,
      });
      toast.success('Student added successfully.');
      setAddModalOpen(false);
      setForm({
        registerNumber: '',
        email: '',
        accessCode: '',
        password: 'password123',
      });
      fetchStudents();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveEdit = async () => {
    if (!editTarget) return;
    setSaving(true);
    try {
      const num = (v: string) => (v.trim() === '' ? undefined : Number(v));
      await Promise.all([
        studentApi.updateProfile(editTarget.id, {
          phone: editForm.phone.trim() || undefined,
          dateOfBirth: editForm.dateOfBirth || undefined,
          batch: editForm.batch.trim() || undefined,
          section: editForm.section.trim() || undefined,
        }),
        studentApi.updateAcademic(editTarget.id, {
          cgpa: num(academic.cgpa),
          tenthPercentage: num(academic.tenthPercentage),
          twelfthPercentage: num(academic.twelfthPercentage),
          diplomaPercentage: num(academic.diplomaPercentage),
          activeBacklogs: num(academic.activeBacklogs),
          historyOfBacklogs: num(academic.historyOfBacklogs),
        }),
      ]);
      toast.success('Student profile updated.');
      setEditOpen(false);
      setEditTarget(null);
      fetchStudents();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const clearFilters = () => {
    setSearch('');
    setDeptFilter('');
    setInterestFilter('');
    setStatusFilter('');
  };

  const filteredStudents = students.filter((s) => {
    if (deptFilter && s.departmentId !== Number(deptFilter)) return false;
    if (interestFilter) {
      const interested = s.placementInterested === true;
      if (interestFilter === 'INTERESTED' && !interested) return false;
      if (interestFilter === 'NOT_INTERESTED' && interested) return false;
    }
    if (statusFilter && s.placementStatus !== statusFilter) return false;
    return true;
  });

  const hasLocalFilter = !!interestFilter || !!statusFilter || !!deptFilter;
  const hasActiveFilters = !!search || !!deptFilter || hasLocalFilter;
  const countText = hasLocalFilter
    ? `${filteredStudents.length} of ${totalElements} ${totalElements === 1 ? 'student' : 'students'}`
    : `${totalElements} ${totalElements === 1 ? 'student' : 'students'}`;

  const getStatusBadge = (status: string | null) => {
    switch (status) {
      case 'PLACED':
        return <Badge variant="success" dot>{formatStatus(status)}</Badge>;
      case 'BLOCKED':
        return <Badge variant="danger" dot>{formatStatus(status)}</Badge>;
      default:
        return <Badge variant="neutral" dot>{formatStatus(status)}</Badge>;
    }
  };

  return (
    <div className="bg-background min-h-screen">
      <PageContainer className="py-8">
        <PageHeader
          title="Students"
          description="Manage student profiles, academics and placement information."
          actions={
            <Button onClick={() => setAddModalOpen(true)} className="flex items-center gap-1.5">
              <UserPlus size={16} /> Add Student
            </Button>
          }
        />

        <div className="glass rounded-[14px] border border-white/40 p-4 mb-4">
          <FilterToolbar
            search={
              <SearchInput
                placeholder="Search by name, email or register number..."
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
                  options={INTEREST_OPTIONS}
                  placeholder="All Interests"
                  value={interestFilter}
                  onChange={(e) => setInterestFilter(e.target.value)}
                  className="w-40"
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

        {!loading && (
          <p className="mb-3 text-[14px] font-medium text-neutral-500">{countText}</p>
        )}

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
                    <div className="flex items-center gap-3 min-w-0">
                      <Avatar name={s.userName || 'U'} size="sm" />
                      <div className="min-w-0">
                        <p className="text-[14.5px] font-medium text-neutral-900 truncate">
                          {s.userName}
                        </p>
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
                      {s.cgpa?.toFixed(2) ?? '—'}
                    </span>
                  ),
                },
                {
                  key: 'placementInterested',
                  label: 'Placement Interest',
                  render: (s) =>
                    s.placementInterested === true ? (
                      <Badge variant="teal" dot>Interested</Badge>
                    ) : s.placementInterested === false ? (
                      <Badge variant="neutral" dot>Not Interested</Badge>
                    ) : (
                      <span className="text-[14px] text-neutral-400">—</span>
                    ),
                },
                {
                  key: 'placementStatus',
                  label: 'Placement Status',
                  render: (s) => getStatusBadge(s.placementStatus),
                },
                {
                  key: 'actions',
                  label: '',
                  className: '!py-0 w-[52px]',
                  render: (s) =>
                    canEdit ? (
                      <button
                        type="button"
                        aria-label={`Actions for ${s.userName}`}
                        aria-haspopup="true"
                        aria-expanded={menu?.student.id === s.id}
                        onClick={(e) => openMenu(e, s)}
                        className="p-1.5 -my-1 rounded-[8px] text-neutral-400 hover:text-neutral-700 hover:bg-neutral-100/80 transition-colors"
                      >
                        <MoreVertical size={17} />
                      </button>
                    ) : (
                      <span />
                    ),
                },
              ]}
              data={filteredStudents}
              rowKey={(s) => s.id}
              density="compact"
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

        {menu && (
          <div
            ref={menuRef}
            role="menu"
            className="fixed z-50 w-[208px] glass-strong rounded-[12px] border border-white/40 shadow-overlay py-1.5 animate-slideDown"
            style={{ left: menu.x, top: menu.y }}
          >
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setMenu(null);
                openEditModal(menu.student);
              }}
              className="w-full flex items-center gap-2.5 px-3 py-2.5 my-0.5 text-[14px] text-left text-neutral-700 hover:bg-neutral-100/60 transition-colors rounded-[8px]"
            >
              <Pencil size={14} className="shrink-0 text-neutral-400" />
              Edit Profile
            </button>
          </div>
        )}

        {/* Add Student Modal */}
        <Modal
          isOpen={addModalOpen}
          onClose={() => setAddModalOpen(false)}
          title="Add Student"
          description="Register a student using their access code. Name and department are taken from the authorized list."
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
              placeholder="student@tce.edu"
            />
            <Input
              label="Access Code"
              required
              value={form.accessCode}
              onChange={(e) => setForm({ ...form, accessCode: e.target.value })}
              placeholder="Enter the 8-character access code"
            />
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

        {/* Edit Student Modal */}
        <Modal
          isOpen={editOpen}
          onClose={() => setEditOpen(false)}
          title="Edit Student"
          description={editTarget ? `Update profile and academic details for ${editTarget.userName || editTarget.registerNumber}.` : undefined}
          size="lg"
          actions={
            <>
              <Button variant="secondary" onClick={() => setEditOpen(false)} disabled={saving}>
                Cancel
              </Button>
              <Button onClick={handleSaveEdit} loading={saving}>
                Save Changes
              </Button>
            </>
          }
        >
          <div className="space-y-6">
            <div>
              <p className="text-[12px] font-semibold uppercase tracking-[0.08em] text-neutral-400 mb-3">
                Profile
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input
                  label="Phone"
                  value={editForm.phone}
                  onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
                  placeholder="+91 98765 43210"
                />
                <Input
                  label="Date of Birth"
                  type="date"
                  value={editForm.dateOfBirth}
                  onChange={(e) => setEditForm({ ...editForm, dateOfBirth: e.target.value })}
                />
                <Input
                  label="Batch"
                  value={editForm.batch}
                  onChange={(e) => setEditForm({ ...editForm, batch: e.target.value })}
                  placeholder="e.g., 2026"
                />
                <Input
                  label="Section"
                  value={editForm.section}
                  onChange={(e) => setEditForm({ ...editForm, section: e.target.value })}
                  placeholder="e.g., A"
                />
              </div>
            </div>
            <div className="border-t border-neutral-100/80 pt-5">
              <p className="text-[12px] font-semibold uppercase tracking-[0.08em] text-neutral-400 mb-3">
                Academics
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                <Input
                  label="CGPA"
                  type="number"
                  step="0.01"
                  min="0"
                  max="10"
                  value={academic.cgpa}
                  onChange={(e) => setAcademic({ ...academic, cgpa: e.target.value })}
                  placeholder="0.00 – 10.00"
                />
                <Input
                  label="Active Backlogs"
                  type="number"
                  min="0"
                  value={academic.activeBacklogs}
                  onChange={(e) => setAcademic({ ...academic, activeBacklogs: e.target.value })}
                />
                <Input
                  label="History of Backlogs"
                  type="number"
                  min="0"
                  value={academic.historyOfBacklogs}
                  onChange={(e) => setAcademic({ ...academic, historyOfBacklogs: e.target.value })}
                />
                <Input
                  label="10th Percentage"
                  type="number"
                  step="0.01"
                  min="0"
                  value={academic.tenthPercentage}
                  onChange={(e) => setAcademic({ ...academic, tenthPercentage: e.target.value })}
                />
                <Input
                  label="12th Percentage"
                  type="number"
                  step="0.01"
                  min="0"
                  value={academic.twelfthPercentage}
                  onChange={(e) => setAcademic({ ...academic, twelfthPercentage: e.target.value })}
                />
                <Input
                  label="Diploma Percentage"
                  type="number"
                  step="0.01"
                  min="0"
                  value={academic.diplomaPercentage}
                  onChange={(e) => setAcademic({ ...academic, diplomaPercentage: e.target.value })}
                />
              </div>
            </div>
          </div>
        </Modal>
      </PageContainer>
    </div>
  );
}