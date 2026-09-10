import { useState, useEffect, useCallback, useMemo } from 'react';
import { companyApi, placementDriveApi, departmentApi } from '../../api/api';
import type { PlacementDrive, Company, Department } from '../../types';
import { getErrorMessage } from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import {
  Button,
  SearchInput,
  Input,
  Select,
  Textarea,
  Badge,
  Modal,
  ConfirmDialog,
  Skeleton,
  EmptyState,
  ErrorState,
  PageHeader,
  Dropdown,
  PageContainer,
  Avatar,
  FilterToolbar,
  notify,
} from '../../components/ui';
import {
  Briefcase,
  Calendar,
  MapPin,
  IndianRupee,
  Users,
  GraduationCap,
  LogIn,
  Lock,
  Ban,
  CheckSquare,
  Square,
  Search,
  CornerDownLeft,
} from 'lucide-react';

type BadgeVariant = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

const STATUS_META: Record<string, { label: string; variant: BadgeVariant }> = {
  UPCOMING: { label: 'Upcoming', variant: 'info' },
  REGISTRATION_OPEN: { label: 'Open for Registration', variant: 'success' },
  REGISTRATION_CLOSED: { label: 'Registration Closed', variant: 'neutral' },
  ONGOING: { label: 'Ongoing', variant: 'warning' },
  COMPLETED: { label: 'Completed', variant: 'neutral' },
  CANCELLED: { label: 'Cancelled', variant: 'danger' },
};

const statusMeta = (s: string): { label: string; variant: BadgeVariant } =>
  STATUS_META[s] ?? {
    label: s.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()),
    variant: 'neutral',
  };

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const formatDriveDate = (iso: string | null): string => {
  if (!iso) return '—';
  const [y, mo, d] = iso.split('-');
  if (!y || !mo || !d || Number.isNaN(Number(mo))) return iso;
  return `${d} ${MONTHS[Number(mo) - 1]} ${y}`;
};

const cleanNum = (n: number | null | undefined): string => {
  if (n == null) return '';
  const s = String(n);
  return s.includes('.') ? s.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '') : s;
};

const isEmptyCriteria = (c: PlacementDrive['eligibilityCriteria']) =>
  !c ||
  (c.minCgpa == null &&
    c.maxActiveBacklogs == null &&
    c.minTenthPct == null &&
    c.minTwelfthPct == null &&
    c.minDiplomaPct == null &&
    (!c.allowedDepartmentNames || c.allowedDepartmentNames.length === 0));

const eligibilitySummary = (drive: PlacementDrive): string => {
  const c = drive.eligibilityCriteria;
  if (isEmptyCriteria(c)) return 'No additional eligibility restrictions';
  const parts: string[] = [];
  if (c!.minCgpa != null) parts.push(`CGPA ≥ ${cleanNum(c!.minCgpa)}`);
  if (c!.maxActiveBacklogs != null)
    parts.push(`${c!.maxActiveBacklogs === 0 ? '0' : `≤ ${c!.maxActiveBacklogs}`} backlogs`);
  if (c!.minTenthPct != null) parts.push(`10th ≥ ${cleanNum(c!.minTenthPct)}%`);
  if (c!.minTwelfthPct != null) parts.push(`12th ≥ ${cleanNum(c!.minTwelfthPct)}%`);
  if (c!.minDiplomaPct != null) parts.push(`Diploma ≥ ${cleanNum(c!.minDiplomaPct)}%`);
  if (c!.allowedDepartmentNames && c!.allowedDepartmentNames.length > 0)
    parts.push(c!.allowedDepartmentNames.join(', '));
  return parts.join('  ·  ');
};

interface DriveForm {
  companyId: string;
  jobRole: string;
  packageLpa: string;
  driveDate: string;
  registrationDeadline: string;
  location: string;
  jobDescription: string;
}

const emptyForm: DriveForm = {
  companyId: '',
  jobRole: '',
  packageLpa: '',
  driveDate: '',
  registrationDeadline: '',
  location: '',
  jobDescription: '',
};

interface EligibilityForm {
  minCgpa: string;
  maxActiveBacklogs: string;
  minTenthPct: string;
  minTwelfthPct: string;
  minDiplomaPct: string;
  allowedDepartmentIds: number[];
}

const emptyEligibilityForm = (c: PlacementDrive['eligibilityCriteria']): EligibilityForm => ({
  minCgpa: c?.minCgpa != null ? cleanNum(c.minCgpa) : '',
  maxActiveBacklogs: c?.maxActiveBacklogs != null ? String(c.maxActiveBacklogs) : '',
  minTenthPct: c?.minTenthPct != null ? cleanNum(c.minTenthPct) : '',
  minTwelfthPct: c?.minTwelfthPct != null ? cleanNum(c.minTwelfthPct) : '',
  minDiplomaPct: c?.minDiplomaPct != null ? cleanNum(c.minDiplomaPct) : '',
  allowedDepartmentIds: c?.allowedDepartmentIds ?? [],
});

const STATUS_OPTIONS = Object.keys(STATUS_META);

export default function DrivesPage() {
  const { user } = useAuth();
  const isPO = user?.role === 'PO';

  const [drives, setDrives] = useState<PlacementDrive[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [companyFilter, setCompanyFilter] = useState('ALL');

  const [companies, setCompanies] = useState<Company[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);

  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState<DriveForm>(emptyForm);
  const [createSaving, setCreateSaving] = useState(false);
  const [createError, setCreateError] = useState('');

  const [viewDrive, setViewDrive] = useState<PlacementDrive | null>(null);

  const [eligDrive, setEligDrive] = useState<PlacementDrive | null>(null);
  const [eligForm, setEligForm] = useState<EligibilityForm>(emptyEligibilityForm(null));
  const [eligSaving, setEligSaving] = useState(false);
  const [eligError, setEligError] = useState('');
  const [deptSearch, setDeptSearch] = useState('');

  const [confirm, setConfirm] = useState<{
    drive: PlacementDrive;
    action: 'close' | 'cancel';
  } | null>(null);
  const [actionSaving, setActionSaving] = useState(false);

  const fetchDrives = useCallback(async () => {
    try {
      const res = await placementDriveApi.getAll({ size: 1000 });
      setDrives(res.data?.data?.content ?? []);
      setLoadError(false);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDrives();
  }, [fetchDrives]);

  useEffect(() => {
    companyApi
      .getAll({ size: 1000 })
      .then((res) => setCompanies(res.data?.data?.content ?? []))
      .catch(() => {});
    departmentApi
      .getActive()
      .then((res) => setDepartments(res.data?.data ?? []))
      .catch(() => {});
  }, []);

  const filteredDrives = useMemo(() => {
    const q = search.trim().toLowerCase();
    return drives.filter((d) => {
      if (statusFilter !== 'ALL' && d.status !== statusFilter) return false;
      if (companyFilter !== 'ALL' && String(d.companyId) !== companyFilter) return false;
      if (q) {
        const hay = `${d.companyName} ${d.jobRole} ${d.companyType ?? ''}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [drives, search, statusFilter, companyFilter]);

  const hasFilters = search.trim() !== '' || statusFilter !== 'ALL' || companyFilter !== 'ALL';

  const clearFilters = () => {
    setSearch('');
    setStatusFilter('ALL');
    setCompanyFilter('ALL');
  };

  const companyFilterOptions = useMemo(() => {
    const seen = new Map<string, string>();
    drives.forEach((d) => seen.set(String(d.companyId), d.companyName));
    return [...seen.entries()].map(([value, label]) => ({ value, label }));
  }, [drives]);

  const companyOptions = companies.map((c) => ({ label: c.name, value: String(c.id) }));

  const deadlineWarning =
    createForm.driveDate && createForm.registrationDeadline
      ? new Date(createForm.registrationDeadline) > new Date(createForm.driveDate)
        ? 'Registration deadline is after the drive date. The drive will stay open past its start — double-check this before saving.'
        : ''
      : '';

  const validateCreate = (): string => {
    if (!createForm.companyId) return 'Company is required.';
    if (!createForm.jobRole.trim()) return 'Job role is required.';
    if (createForm.packageLpa) {
      const p = Number(createForm.packageLpa);
      if (!Number.isFinite(p) || p < 0) return 'Package must be a non-negative number.';
    }
    return '';
  };

  const handleCreate = async () => {
    const v = validateCreate();
    if (v) {
      setCreateError(v);
      return;
    }
    setCreateSaving(true);
    setCreateError('');
    try {
      await placementDriveApi.create({
        companyId: Number(createForm.companyId),
        jobRole: createForm.jobRole.trim(),
        packageLpa: createForm.packageLpa ? Number(createForm.packageLpa) : undefined,
        driveDate: createForm.driveDate || undefined,
        registrationDeadline: createForm.registrationDeadline || undefined,
        location: createForm.location || undefined,
        jobDescription: createForm.jobDescription || undefined,
      });
      notify.success('Drive created successfully');
      setCreateOpen(false);
      setCreateForm(emptyForm);
      fetchDrives();
    } catch (err) {
      setCreateError(getErrorMessage(err));
    } finally {
      setCreateSaving(false);
    }
  };

  const filteredDepartments = useMemo(() => {
    const q = deptSearch.trim().toLowerCase();
    return q
      ? departments.filter((d) => d.name.toLowerCase().includes(q))
      : departments;
  }, [departments, deptSearch]);

  const validateEligibility = (): string => {
    const rules: [string, number, number, string][] = [
      ['minCgpa', 0, 10, 'Minimum CGPA must be between 0 and 10.'],
      ['minTenthPct', 0, 100, '10th percentage must be between 0 and 100.'],
      ['minTwelfthPct', 0, 100, '12th percentage must be between 0 and 100.'],
      ['minDiplomaPct', 0, 100, 'Diploma percentage must be between 0 and 100.'],
    ];
    for (const [key, min, max, msg] of rules) {
      const raw = eligForm[key as keyof EligibilityForm] as string;
      if (raw === '') continue;
      const n = Number(raw);
      if (!Number.isFinite(n) || n < min || n > max) return msg;
    }
    if (eligForm.maxActiveBacklogs !== '') {
      const b = Number(eligForm.maxActiveBacklogs);
      if (!Number.isInteger(b) || b < 0) return 'Maximum backlogs must be a whole number 0 or greater.';
    }
    return '';
  };

  const openEligibility = (d: PlacementDrive) => {
    setEligForm(emptyEligibilityForm(d.eligibilityCriteria));
    setEligError('');
    setDeptSearch('');
    setEligDrive(d);
  };

  const toggleDept = (id: number) => {
    setEligForm((f) => ({
      ...f,
      allowedDepartmentIds: f.allowedDepartmentIds.includes(id)
        ? f.allowedDepartmentIds.filter((x) => x !== id)
        : [...f.allowedDepartmentIds, id],
    }));
  };

  const handleEligibility = async () => {
    if (!eligDrive) return;
    const v = validateEligibility();
    if (v) {
      setEligError(v);
      return;
    }
    setEligSaving(true);
    setEligError('');
    try {
      const payload: Record<string, unknown> = {
        allowedDepartmentIds: eligForm.allowedDepartmentIds,
      };
      if (eligForm.minCgpa !== '') payload.minCgpa = Number(eligForm.minCgpa);
      if (eligForm.maxActiveBacklogs !== '')
        payload.maxActiveBacklogs = Number(eligForm.maxActiveBacklogs);
      if (eligForm.minTenthPct !== '') payload.minTenthPct = Number(eligForm.minTenthPct);
      if (eligForm.minTwelfthPct !== '') payload.minTwelfthPct = Number(eligForm.minTwelfthPct);
      if (eligForm.minDiplomaPct !== '') payload.minDiplomaPct = Number(eligForm.minDiplomaPct);
      const res = await placementDriveApi.setEligibility(eligDrive.id, payload);
      notify.success(res.data?.message || 'Eligibility criteria saved');
      setEligDrive(null);
      fetchDrives();
    } catch (err) {
      setEligError(getErrorMessage(err));
    } finally {
      setEligSaving(false);
    }
  };

  const runStatus = async (driveId: number, status: string) => {
    setActionSaving(true);
    try {
      await placementDriveApi.setStatus(driveId, status);
      notify.success('Drive status updated');
      fetchDrives();
    } catch {
      notify.error('Failed to update drive status');
    } finally {
      setActionSaving(false);
    }
  };

  const runConfirmedStatus = async () => {
    if (!confirm) return;
    setActionSaving(true);
    try {
      await placementDriveApi.setStatus(
        confirm.drive.id,
        confirm.action === 'close' ? 'REGISTRATION_CLOSED' : 'CANCELLED'
      );
      notify.success(
        confirm.action === 'close'
          ? 'Registration closed'
          : `${confirm.drive.companyName} drive cancelled`
      );
      setConfirm(null);
      fetchDrives();
    } catch {
      notify.error('Failed to update drive status');
    } finally {
      setActionSaving(false);
    }
  };

  const getActions = (d: PlacementDrive) => {
    const items: { label: string; onClick: () => void; danger?: boolean; icon?: React.ReactNode }[] = [
      { label: 'View', icon: <CornerDownLeft size={15} />, onClick: () => setViewDrive(d) },
    ];
    if (isPO) {
      items.push({
        label: 'Eligibility',
        icon: <GraduationCap size={15} />,
        onClick: () => openEligibility(d),
      });
      if (d.status === 'UPCOMING' || d.status === 'REGISTRATION_CLOSED' || d.status === 'ONGOING') {
        items.push({
          label: 'Open Registration',
          icon: <LogIn size={15} />,
          onClick: () => runStatus(d.id, 'REGISTRATION_OPEN'),
        });
      }
      if (d.status === 'REGISTRATION_OPEN') {
        items.push({
          label: 'Close Registration',
          icon: <Lock size={15} />,
          onClick: () => setConfirm({ drive: d, action: 'close' }),
        });
      }
      if (d.status !== 'COMPLETED' && d.status !== 'CANCELLED') {
        items.push({
          label: 'Cancel Drive',
          icon: <Ban size={15} />,
          danger: true,
          onClick: () => setConfirm({ drive: d, action: 'cancel' }),
        });
      }
    }
    return items;
  };

  const renderInfoCell = (label: string, value: React.ReactNode, icon?: React.ReactNode) => (
    <div className="min-w-0">
      <p className="text-[10.5px] font-semibold uppercase tracking-[0.08em] text-neutral-400 mb-0.5">
        {label}
      </p>
      <p className="flex items-center gap-1.5 text-[13.5px] font-medium text-neutral-700">
        {icon}
        {value}
      </p>
    </div>
  );

  return (
    <PageContainer>
      <PageHeader
        title="Placement Drives"
        description="Manage upcoming recruitment opportunities, eligibility criteria and placement schedules."
        actions={
          isPO && (
            <Button
              onClick={() => {
                setCreateForm(emptyForm);
                setCreateError('');
                setCreateOpen(true);
              }}
              size="md"
            >
              <Briefcase size={15} />
              Create Drive
            </Button>
          )
        }
      />

      <FilterToolbar
        search={
          <SearchInput
            placeholder="Search company / role..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
        filters={
          <>
            <Select
              aria-label="Filter by status"
              options={[
                { label: 'All statuses', value: 'ALL' },
                ...STATUS_OPTIONS.map((s) => ({ label: statusMeta(s).label, value: s })),
              ]}
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            />
            <Select
              aria-label="Filter by company"
              options={[
                { label: 'All companies', value: 'ALL' },
                ...companyFilterOptions,
              ]}
              value={companyFilter}
              onChange={(e) => setCompanyFilter(e.target.value)}
            />
            {hasFilters && (
              <Button variant="secondary" size="sm" onClick={clearFilters}>
                Clear filters
              </Button>
            )}
          </>
        }
      >
        <span className="text-[13.5px] text-neutral-500">
          {filteredDrives.length} {filteredDrives.length === 1 ? 'placement drive' : 'placement drives'}
        </span>
      </FilterToolbar>

      {loading ? (
        <div className="bg-white rounded-[20px] border border-neutral-200/60 overflow-hidden divide-y divide-neutral-100/80">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="px-5 py-4 flex items-center gap-6 animate-pulse">
              <div className="w-10 h-10 rounded-full bg-neutral-200/70" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-3.5 w-40" />
                <Skeleton className="h-3 w-28" />
              </div>
              <Skeleton className="h-4 w-20 rounded-full" />
              <Skeleton className="h-4 w-28" />
              <div className="w-8 h-8 rounded-[10px] bg-neutral-200/70" />
            </div>
          ))}
        </div>
      ) : loadError ? (
        <ErrorState
          title="Unable to load placement drives"
          message="We couldn't retrieve placement drives right now. Please try again."
          onRetry={() => {
            setLoading(true);
            fetchDrives();
          }}
        />
      ) : drives.length === 0 ? (
        <EmptyState
          icon={<Briefcase size={40} />}
          title="No placement drives available"
          description={
            isPO
              ? 'Create your first placement drive to get started.'
              : 'There are no placement drives yet. Check back later.'
          }
          action={
            isPO ? (
              <Button
                onClick={() => {
                  setCreateForm(emptyForm);
                  setCreateError('');
                  setCreateOpen(true);
                }}
              >
                Create Drive
              </Button>
            ) : undefined
          }
        />
      ) : filteredDrives.length === 0 ? (
        <EmptyState
          icon={<Search size={40} />}
          title="No placement drives match your filters"
          description="Try adjusting the search or filter criteria."
          action={
            <Button variant="secondary" onClick={clearFilters}>
              Clear filters
            </Button>
          }
        />
      ) : (
        <div className="bg-white rounded-[20px] border border-neutral-200/60 shadow-soft overflow-hidden">
          <div className="px-5 py-3.5 border-b border-neutral-100/80 flex items-center justify-between">
            <h2 className="text-[14.5px] font-semibold text-neutral-900">Drives</h2>
            <Badge variant="neutral" size="sm">
              {filteredDrives.length}
            </Badge>
          </div>
          <div className="divide-y divide-neutral-100/80">
            {filteredDrives.map((d) => {
              const meta = statusMeta(d.status);
              return (
                <article
                  key={d.id}
                  className="px-4 sm:px-5 py-4 flex flex-wrap items-start sm:items-center gap-x-3 md:gap-x-6 gap-y-2.5"
                >
                  <div className="min-w-0 flex-1 basis-56 flex items-center gap-3">
                    <Avatar name={d.companyName} size="sm" className="shrink-0" />
                    <div className="min-w-0">
                      <p className="text-[15px] font-semibold text-neutral-900 truncate leading-tight">
                        {d.companyName}
                      </p>
                      <p className="text-[13.5px] font-medium text-neutral-600 truncate">
                        {d.jobRole}
                      </p>
                    </div>
                  </div>

                  {d.packageLpa != null ? (
                    <div className="min-w-[92px]">
                      <p className="text-[10.5px] font-semibold uppercase tracking-[0.08em] text-neutral-400 mb-0.5">
                        Package
                      </p>
                      <p className="flex items-center gap-1 text-[14px] font-bold text-primary-600">
                        <IndianRupee size={13} />
                        {cleanNum(d.packageLpa)} LPA
                      </p>
                    </div>
                  ) : (
                    <div className="min-w-[92px]">
                      <p className="text-[10.5px] font-semibold uppercase tracking-[0.08em] text-neutral-400 mb-0.5">
                        Package
                      </p>
                      <p className="text-[13px] text-neutral-400">Not disclosed</p>
                    </div>
                  )}

                  {renderInfoCell(
                    'Drive Date',
                    formatDriveDate(d.driveDate),
                    <Calendar size={13} className="text-neutral-400 shrink-0" />
                  )}

                  {renderInfoCell(
                    'Register By',
                    formatDriveDate(d.registrationDeadline),
                    <Calendar size={13} className="text-neutral-400 shrink-0" />
                  )}

                  <div className="min-w-[190px] flex-1 basis-44">
                    <p className="text-[10.5px] font-semibold uppercase tracking-[0.08em] text-neutral-400 mb-0.5">
                      Eligibility
                    </p>
                    <p className="flex items-start gap-1.5 text-[13px] text-neutral-600 leading-snug">
                      <Users size={13} className="text-neutral-300 shrink-0 mt-0.5" />
                      <span>{eligibilitySummary(d)}</span>
                    </p>
                  </div>

                  <div className="ml-auto shrink-0 flex items-center gap-2">
                    <Badge variant={meta.variant} dot size="sm">
                      {meta.label}
                    </Badge>
                    <Dropdown items={getActions(d)} />
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      )}

      {/* ── Create Drive ── */}
      <Modal
        isOpen={createOpen}
        onClose={() => !createSaving && setCreateOpen(false)}
        title="Create Placement Drive"
        description="Set up a new campus recruitment drive."
        size="lg"
        actions={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)} disabled={createSaving}>
              Cancel
            </Button>
            <Button onClick={handleCreate} loading={createSaving}>
              Create Drive
            </Button>
          </>
        }
      >
        {createError && (
          <div className="mb-4 rounded-[12px] border border-danger-200 bg-danger-50 px-4 py-3 text-[13.5px] text-danger-700">
            {createError}
          </div>
        )}
        <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-400 mb-3">
          Company &amp; Role
        </p>
        <div className="space-y-4 mb-6">
          <Select
            label="Company"
            required
            placeholder={companies.length === 0 ? 'Loading companies...' : 'Select company'}
            options={companyOptions}
            value={createForm.companyId}
            onChange={(e) => setCreateForm({ ...createForm, companyId: e.target.value })}
          />
          <Input
            label="Job Role"
            required
            placeholder="e.g. Software Engineer"
            value={createForm.jobRole}
            onChange={(e) => setCreateForm({ ...createForm, jobRole: e.target.value })}
          />
        </div>

        <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-400 mb-3">
          Schedule
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
          <Input
            label="Drive Date"
            type="date"
            value={createForm.driveDate}
            onChange={(e) => setCreateForm({ ...createForm, driveDate: e.target.value })}
          />
          <Input
            label="Registration Deadline"
            type="date"
            value={createForm.registrationDeadline}
            onChange={(e) => setCreateForm({ ...createForm, registrationDeadline: e.target.value })}
          />
        </div>
        {deadlineWarning && (
          <p className="mb-6 text-[13px] text-amber-600 bg-amber-50 border border-amber-200 rounded-[10px] px-3 py-2.5">
            {deadlineWarning}
          </p>
        )}

        <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-400 mb-3">
          Placement Details
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Input
            label="Package (LPA)"
            type="number"
            min="0"
            step="0.1"
            placeholder="e.g. 6.5"
            value={createForm.packageLpa}
            onChange={(e) => setCreateForm({ ...createForm, packageLpa: e.target.value })}
          />
          <Input
            label="Location"
            placeholder="e.g. Bangalore"
            value={createForm.location}
            onChange={(e) => setCreateForm({ ...createForm, location: e.target.value })}
          />
        </div>
        <div className="mt-4">
          <Textarea
            label="Description / Notes"
            rows={4}
            placeholder="Describe the role, responsibilities, and instructions for students..."
            value={createForm.jobDescription}
            onChange={(e) => setCreateForm({ ...createForm, jobDescription: e.target.value })}
          />
        </div>
      </Modal>

      {/* ── View Drive ── */}
      <Modal
        isOpen={!!viewDrive}
        onClose={() => setViewDrive(null)}
        title="Drive Details"
        description={viewDrive ? `${viewDrive.companyName} · ${viewDrive.jobRole}` : ''}
        size="lg"
        actions={
          <Button variant="secondary" onClick={() => setViewDrive(null)}>
            Close
          </Button>
        }
      >
        {viewDrive && (
          <div className="space-y-6">
            <div className="flex items-start justify-between gap-4">
              <div className="flex items-center gap-3 min-w-0">
                <Avatar name={viewDrive.companyName} size="lg" className="shrink-0" />
                <div className="min-w-0">
                  <p className="text-[18px] font-semibold text-neutral-900">
                    {viewDrive.companyName}
                  </p>
                  <p className="text-[14px] text-neutral-500">
                    {viewDrive.jobRole}
                    {viewDrive.location ? ` · ${viewDrive.location}` : ''}
                  </p>
                </div>
              </div>
              <Badge variant={statusMeta(viewDrive.status).variant} dot>
                {statusMeta(viewDrive.status).label}
              </Badge>
            </div>

            {viewDrive.packageLpa != null && (
              <div className="flex items-center gap-2">
                <IndianRupee size={20} className="text-primary-600" />
                <span className="text-[22px] font-bold text-primary-600">
                  {cleanNum(viewDrive.packageLpa)} LPA
                </span>
              </div>
            )}

            <div className="grid grid-cols-2 gap-4">
              {viewDrive.driveDate != null && (
                <div className="rounded-[14px] border border-neutral-200/70 bg-neutral-50/60 p-4">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-neutral-400 mb-1">
                    Drive Date
                  </p>
                  <p className="text-[15px] font-semibold text-neutral-800">
                    {formatDriveDate(viewDrive.driveDate)}
                  </p>
                </div>
              )}
              {viewDrive.registrationDeadline != null && (
                <div className="rounded-[14px] border border-neutral-200/70 bg-neutral-50/60 p-4">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-neutral-400 mb-1">
                    Registration Deadline
                  </p>
                  <p className="text-[15px] font-semibold text-neutral-800">
                    {formatDriveDate(viewDrive.registrationDeadline)}
                  </p>
                </div>
              )}
            </div>

            <div>
              <div className="flex items-center gap-2 mb-2.5">
                <GraduationCap size={16} className="text-primary-600" />
                <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
                  Eligibility
                </p>
              </div>
              <div className="rounded-[14px] border border-neutral-200/70 p-4 space-y-2.5">
                {isEmptyCriteria(viewDrive.eligibilityCriteria) ? (
                  <p className="text-[14px] text-neutral-500">
                    No additional eligibility restrictions.
                  </p>
                ) : (
                  <>
                    {viewDrive.eligibilityCriteria!.minCgpa != null && (
                      <EligRow
                        label="CGPA"
                        value={`≥ ${cleanNum(viewDrive.eligibilityCriteria!.minCgpa)}`}
                      />
                    )}
                    {viewDrive.eligibilityCriteria!.maxActiveBacklogs != null && (
                      <EligRow
                        label="Active Backlogs"
                        value={`≤ ${viewDrive.eligibilityCriteria!.maxActiveBacklogs}`}
                      />
                    )}
                    {viewDrive.eligibilityCriteria!.minTenthPct != null && (
                      <EligRow
                        label="10th Percentage"
                        value={`≥ ${cleanNum(viewDrive.eligibilityCriteria!.minTenthPct)}%`}
                      />
                    )}
                    {viewDrive.eligibilityCriteria!.minTwelfthPct != null && (
                      <EligRow
                        label="12th Percentage"
                        value={`≥ ${cleanNum(viewDrive.eligibilityCriteria!.minTwelfthPct)}%`}
                      />
                    )}
                    {viewDrive.eligibilityCriteria!.minDiplomaPct != null && (
                      <EligRow
                        label="Diploma Percentage"
                        value={`≥ ${cleanNum(viewDrive.eligibilityCriteria!.minDiplomaPct)}%`}
                      />
                    )}
                    {viewDrive.eligibilityCriteria!.allowedDepartmentNames?.length ? (
                      <EligRow
                        label="Departments"
                        value={viewDrive.eligibilityCriteria!.allowedDepartmentNames.join(', ')}
                      />
                    ) : (
                      <EligRow label="Departments" value="All departments" />
                    )}
                  </>
                )}
              </div>
            </div>

            {viewDrive.jobDescription && (
              <div>
                <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500 mb-2">
                  Description / Instructions
                </p>
                <p className="text-[14px] text-neutral-600 leading-relaxed whitespace-pre-wrap">
                  {viewDrive.jobDescription}
                </p>
              </div>
            )}

            {viewDrive.location && (
              <p className="flex items-center gap-1.5 text-[13.5px] text-neutral-500">
                <MapPin size={14} className="text-neutral-400 shrink-0" />
                {viewDrive.location}
              </p>
            )}
          </div>
        )}
      </Modal>

      {/* ── Set Eligibility ── */}
      <Modal
        isOpen={!!eligDrive}
        onClose={() => !eligSaving && setEligDrive(null)}
        title={eligDrive ? `Eligibility — ${eligDrive.companyName}` : 'Eligibility'}
        description={eligDrive ? `Set criteria for the ${eligDrive.jobRole} drive.` : ''}
        size="md"
        actions={
          <>
            <Button variant="secondary" onClick={() => setEligDrive(null)} disabled={eligSaving}>
              Cancel
            </Button>
            <Button onClick={handleEligibility} loading={eligSaving}>
              Save Eligibility
            </Button>
          </>
        }
      >
        {eligError && (
          <div className="mb-4 rounded-[12px] border border-danger-200 bg-danger-50 px-4 py-3 text-[13.5px] text-danger-700">
            {eligError}
          </div>
        )}
        <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-400 mb-3">
          Academic Thresholds
        </p>
        <div className="grid grid-cols-2 gap-4 mb-6">
          <Input
            label="Minimum CGPA"
            type="number"
            min="0"
            max="10"
            step="0.01"
            placeholder="e.g. 7.5"
            value={eligForm.minCgpa}
            onChange={(e) => setEligForm({ ...eligForm, minCgpa: e.target.value })}
          />
          <Input
            label="Max Active Backlogs"
            type="number"
            min="0"
            step="1"
            placeholder="e.g. 0"
            value={eligForm.maxActiveBacklogs}
            onChange={(e) => setEligForm({ ...eligForm, maxActiveBacklogs: e.target.value })}
          />
          <Input
            label="10th Percentage"
            type="number"
            min="0"
            max="100"
            step="0.1"
            placeholder="e.g. 60"
            value={eligForm.minTenthPct}
            onChange={(e) => setEligForm({ ...eligForm, minTenthPct: e.target.value })}
          />
          <Input
            label="12th Percentage"
            type="number"
            min="0"
            max="100"
            step="0.1"
            placeholder="e.g. 60"
            value={eligForm.minTwelfthPct}
            onChange={(e) => setEligForm({ ...eligForm, minTwelfthPct: e.target.value })}
          />
          <Input
            label="Diploma Percentage"
            type="number"
            min="0"
            max="100"
            step="0.1"
            placeholder="e.g. 60"
            value={eligForm.minDiplomaPct}
            onChange={(e) => setEligForm({ ...eligForm, minDiplomaPct: e.target.value })}
          />
        </div>

        <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-400 mb-1">
          Eligible Departments
        </p>
        <p className="text-[13px] text-neutral-500 mb-3">
          Leave unticked to allow students from all departments, or select the departments that can
          apply.
        </p>
        {departments.length > 6 && (
          <div className="relative mb-3">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400" />
            <input
              type="text"
              value={deptSearch}
              onChange={(e) => setDeptSearch(e.target.value)}
              placeholder="Search departments..."
              className="w-full pl-9 pr-3 py-2 text-[13.5px] rounded-[10px] border border-neutral-300 bg-white focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500"
            />
          </div>
        )}
        <div className="flex items-center justify-between mb-2">
          <span className="text-[13px] text-neutral-500">
            {eligForm.allowedDepartmentIds.length} selected
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              className="text-[12.5px] font-medium text-primary-600 hover:text-primary-700 hover:underline"
              onClick={() => setEligForm({ ...eligForm, allowedDepartmentIds: departments.map((d) => d.id) })}
            >
              Select All
            </button>
            <button
              type="button"
              className="text-[12.5px] font-medium text-neutral-500 hover:text-neutral-700 hover:underline"
              onClick={() => setEligForm({ ...eligForm, allowedDepartmentIds: [] })}
            >
              Clear
            </button>
          </div>
        </div>
        <div className="max-h-52 overflow-y-auto rounded-[12px] border border-neutral-200/80 divide-y divide-neutral-100/80">
          {filteredDepartments.length === 0 ? (
            <p className="px-4 py-5 text-center text-[13px] text-neutral-400">
              No departments found.
            </p>
          ) : (
            filteredDepartments.map((dept) => {
              const checked = eligForm.allowedDepartmentIds.includes(dept.id);
              return (
                <label
                  key={dept.id}
                  className="flex items-center gap-3 px-4 py-2.5 cursor-pointer hover:bg-neutral-50/80 transition-colors"
                >
                  <span className="text-neutral-500 shrink-0">
                    {checked ? (
                      <CheckSquare size={17} className="text-primary-600" />
                    ) : (
                      <Square size={17} />
                    )}
                  </span>
                  <span className="text-[14px] text-neutral-700">{dept.name}</span>
                  <input
                    type="checkbox"
                    className="sr-only"
                    checked={checked}
                    onChange={() => toggleDept(dept.id)}
                  />
                </label>
              );
            })
          )}
        </div>
      </Modal>

      {/* ── Status Confirmations ── */}
      <ConfirmDialog
        isOpen={!!confirm}
        onClose={() => setConfirm(null)}
        onConfirm={runConfirmedStatus}
        title={confirm?.action === 'close' ? 'Close Registration' : 'Cancel Drive'}
        message={
          confirm
            ? confirm.action === 'close'
              ? `Stop accepting registrations for the "${confirm.drive.companyName}" drive?`
              : `Cancel the "${confirm.drive.companyName}" drive? This will mark it as cancelled for students.`
            : ''
        }
        confirmLabel={confirm?.action === 'cancel' ? 'Cancel Drive' : 'Close Registration'}
        variant={confirm?.action === 'cancel' ? 'danger' : 'primary'}
        loading={actionSaving}
      />
    </PageContainer>
  );
}

function EligRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <span className="text-[13px] text-neutral-500">{label}</span>
      <span className="text-[13.5px] font-semibold text-neutral-800 text-right">{value}</span>
    </div>
  );
}