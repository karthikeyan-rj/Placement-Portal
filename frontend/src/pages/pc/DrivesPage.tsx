import { useState, useEffect } from 'react';
import { companyApi, placementDriveApi } from '../../api/api';
import type { PlacementDrive, Company } from '../../types';
import {
  Button,
  SearchInput,
  Input,
  Select,
  Textarea,
  Badge,
  Modal,
  Pagination,
  Skeleton,
  EmptyState,
  PageHeader,
  Dropdown,
  ConfirmDialog,
  PageContainer,
  notify,
} from '../../components/ui';
import { usePaginatedData } from '../../hooks/useApi';
import { Calendar, MapPin, Building2, Briefcase, Clock, IndianRupee } from 'lucide-react';

type StatusFilter = 'ALL' | 'UPCOMING' | 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED' | 'CANCELLED';

const STATUS_TABS: StatusFilter[] = ['ALL', 'UPCOMING', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'CLOSED', 'CANCELLED'];

const statusBadgeVariant = (s: string): 'warning' | 'success' | 'danger' | 'info' | 'neutral' => {
  switch (s) {
    case 'UPCOMING': return 'info';
    case 'OPEN': return 'success';
    case 'IN_PROGRESS': return 'warning';
    case 'COMPLETED': return 'neutral';
    case 'CLOSED': return 'neutral';
    case 'CANCELLED': return 'danger';
    default: return 'neutral';
  }
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

export default function DrivesPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [search, setSearch] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<DriveForm>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companiesLoading, setCompaniesLoading] = useState(false);
  const [confirmAction, setConfirmAction] = useState<{
    drive: PlacementDrive;
    action: 'open' | 'close' | 'cancel';
  } | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const params: Record<string, string | number> = {};
  if (statusFilter !== 'ALL') params.status = statusFilter;
  if (search) params.search = search;

  const {
    data: drives,
    loading,
    page,
    totalPages,
    totalElements,
    setPage,
    refresh,
  } = usePaginatedData<PlacementDrive>({
    url: '/placement-drives',
    params,
  });

  useEffect(() => {
    setCompaniesLoading(true);
    companyApi
      .getAll({ size: 500 })
      .then((res) => {
        const d = res.data?.data;
        setCompanies(d?.content ?? (Array.isArray(d) ? d : []));
      })
      .catch(() => {})
      .finally(() => setCompaniesLoading(false));
  }, []);

  const handleCreate = async () => {
    if (!form.companyId || !form.jobRole.trim()) {
      notify.error('Company and Job Role are required');
      return;
    }
    setSaving(true);
    try {
      await placementDriveApi.create({
        companyId: Number(form.companyId),
        jobRole: form.jobRole,
        packageLpa: form.packageLpa ? Number(form.packageLpa) : undefined,
        driveDate: form.driveDate || undefined,
        registrationDeadline: form.registrationDeadline || undefined,
        location: form.location || undefined,
        jobDescription: form.jobDescription || undefined,
      });
      notify.success('Drive created successfully');
      setModalOpen(false);
      setForm(emptyForm);
      refresh();
    } catch {
      notify.error('Failed to create drive');
    } finally {
      setSaving(false);
    }
  };

  const executeStatusChange = async () => {
    if (!confirmAction) return;
    setActionLoading(true);
    try {
      const { drive, action } = confirmAction;
      if (action === 'open') await placementDriveApi.open(drive.id);
      else if (action === 'close') await placementDriveApi.close(drive.id);
      else if (action === 'cancel') await placementDriveApi.cancel(drive.id);
      notify.success(
        `Drive ${action === 'open' ? 'opened' : action === 'close' ? 'closed' : 'cancelled'}`
      );
      setConfirmAction(null);
      refresh();
    } catch {
      notify.error('Failed to update drive status');
    } finally {
      setActionLoading(false);
    }
  };

  const getActions = (d: PlacementDrive) => {
    const items: { label: string; onClick: () => void; danger?: boolean }[] = [];
    if (d.status === 'UPCOMING' || d.status === 'OPEN') {
      items.push({
        label: 'Open',
        onClick: () => setConfirmAction({ drive: d, action: 'open' }),
      });
    }
    if (d.status === 'OPEN' || d.status === 'IN_PROGRESS') {
      items.push({
        label: 'Close',
        onClick: () => setConfirmAction({ drive: d, action: 'close' }),
      });
    }
    if (d.status !== 'CLOSED' && d.status !== 'COMPLETED' && d.status !== 'CANCELLED') {
      items.push({
        label: 'Cancel',
        onClick: () => setConfirmAction({ drive: d, action: 'cancel' }),
        danger: true,
      });
    }
    return items;
  };

  return (
    <PageContainer>
      <PageHeader
        title="Placement Drives"
        description="Manage campus recruitment drives."
        actions={
          <Button
            onClick={() => {
              setForm(emptyForm);
              setModalOpen(true);
            }}
            size="md"
          >
            <Briefcase size={15} />
            Create Drive
          </Button>
        }
      />

      <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card">
        <div className="px-5 py-4 border-b border-neutral-100 flex items-center justify-between gap-4 flex-wrap">
          <div className="flex gap-1.5 flex-wrap">
            {STATUS_TABS.map((tab) => (
              <button
                key={tab}
                onClick={() => {
                  setStatusFilter(tab);
                  setPage(0);
                }}
                className={`px-3 py-1.5 text-[14px] font-medium rounded-md transition-colors ${
                  statusFilter === tab
                    ? 'bg-primary-500 text-white shadow-sm'
                    : 'text-neutral-600 hover:bg-neutral-50 border border-neutral-200/80'
                }`}
              >
                {tab === 'ALL' ? 'All' : tab.charAt(0) + tab.slice(1).toLowerCase().replace('_', ' ')}
              </button>
            ))}
          </div>
          <div className="w-56 shrink-0">
            <SearchInput
              placeholder="Search drives..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>

        {loading ? (
          <div className="p-6 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-5">
                <div className="flex items-start justify-between mb-3">
                  <Skeleton className="h-4 w-28" />
                  <Skeleton className="h-5 w-16 rounded-full" />
                </div>
                <Skeleton className="h-4 w-40 mb-3" />
                <Skeleton className="h-6 w-20 mb-3" />
                <div className="space-y-2 mt-auto">
                  <Skeleton className="h-3 w-36" />
                  <Skeleton className="h-3 w-32" />
                  <Skeleton className="h-3 w-24" />
                </div>
              </div>
            ))}
          </div>
        ) : drives.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={<Briefcase size={40} />}
              title="No placement drives found"
              description={
                statusFilter === 'ALL'
                  ? 'Create your first placement drive to get started.'
                  : `No drives with status "${statusFilter}".`
              }
              action={
                <Button onClick={() => { setForm(emptyForm); setModalOpen(true); }}>
                  Create Drive
                </Button>
              }
            />
          </div>
        ) : (
          <div className="p-6">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {drives.map((drive) => (
                <div
                  key={drive.id}
                  className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-5 flex flex-col hover:shadow-raised hover:-translate-y-0.5 hover:border-primary-200/70 transition-all duration-150"
                >
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex items-center gap-2 min-w-0">
                      <div className="shrink-0 p-1.5 rounded-lg bg-brand-navy">
                        <Building2 size={14} className="text-white" />
                      </div>
                      <span className="text-[15px] font-semibold text-brand-navy truncate">
                        {drive.companyName}
                      </span>
                    </div>
                    <Badge variant={statusBadgeVariant(drive.status)} dot={true}>
                      {drive.status.replace('_', ' ')}
                    </Badge>
                  </div>

                  <p className="text-[16px] font-semibold text-neutral-900 mb-2">
                    {drive.jobRole}
                  </p>

                  {drive.packageLpa != null && (
                    <div className="flex items-center gap-1.5 mb-3">
                      <IndianRupee size={15} className="text-primary-600" />
                      <span className="text-[17px] font-bold text-primary-600">
                        {drive.packageLpa}
                      </span>
                      <span className="text-[15px] font-medium text-primary-500">LPA</span>
                    </div>
                  )}

                  <div className="space-y-1.5 text-[15px] text-neutral-500 mt-auto pt-1">
                    {drive.driveDate && (
                      <div className="flex items-center gap-1.5">
                        <Calendar size={12} className="text-neutral-400 shrink-0" />
                        <span>Drive: {new Date(drive.driveDate).toLocaleDateString()}</span>
                      </div>
                    )}
                    {drive.registrationDeadline && (
                      <div className="flex items-center gap-1.5">
                        <Clock size={12} className="text-neutral-400 shrink-0" />
                        <span>Deadline: {new Date(drive.registrationDeadline).toLocaleDateString()}</span>
                      </div>
                    )}
                    {drive.location && (
                      <div className="flex items-center gap-1.5">
                        <MapPin size={12} className="text-neutral-400 shrink-0" />
                        <span>{drive.location}</span>
                      </div>
                    )}
                  </div>

                  {drive.eligibilityCriteria && (
                    <div className="mt-3 pt-3 border-t border-neutral-100">
                      <p className="text-[15px] font-medium text-neutral-400 uppercase tracking-wider mb-2">
                        Eligibility
                      </p>
                      <div className="flex flex-wrap gap-1.5">
                        {drive.eligibilityCriteria.minCgpa != null && (
                          <Badge variant="info" size="sm">CGPA &ge; {drive.eligibilityCriteria.minCgpa}</Badge>
                        )}
                        {drive.eligibilityCriteria.maxActiveBacklogs != null && (
                          <Badge variant="warning" size="sm">Backlogs &le; {drive.eligibilityCriteria.maxActiveBacklogs}</Badge>
                        )}
                        {drive.eligibilityCriteria.minTenthPct != null && (
                          <Badge variant="info" size="sm">10th &ge; {drive.eligibilityCriteria.minTenthPct}%</Badge>
                        )}
                        {drive.eligibilityCriteria.minTwelfthPct != null && (
                          <Badge variant="info" size="sm">12th &ge; {drive.eligibilityCriteria.minTwelfthPct}%</Badge>
                        )}
                      </div>
                    </div>
                  )}

                  {getActions(drive).length > 0 && (
                    <div className="mt-4 pt-3 border-t border-neutral-100 flex justify-end">
                      <Dropdown items={getActions(drive)} />
                    </div>
                  )}
                </div>
              ))}
            </div>

            {totalPages > 1 && (
              <div className="mt-5 pt-4 border-t border-neutral-100">
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  totalElements={totalElements}
                  onPageChange={setPage}
                />
              </div>
            )}
          </div>
        )}
      </div>

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Create Placement Drive"
        description="Set up a new campus recruitment drive."
        size="lg"
        actions={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={handleCreate} loading={saving}>
              Create Drive
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Select
            label="Company"
            required
            placeholder={companiesLoading ? 'Loading companies...' : 'Select company'}
            options={companies.map((c) => ({ label: c.name, value: String(c.id) }))}
            value={form.companyId}
            onChange={(e) => setForm({ ...form, companyId: e.target.value })}
          />
          <Input
            label="Job Role"
            required
            placeholder="e.g. Software Engineer"
            value={form.jobRole}
            onChange={(e) => setForm({ ...form, jobRole: e.target.value })}
          />
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Package (LPA)"
              type="number"
              min="0"
              step="0.1"
              placeholder="e.g. 6.5"
              value={form.packageLpa}
              onChange={(e) => setForm({ ...form, packageLpa: e.target.value })}
            />
            <Input
              label="Location"
              placeholder="e.g. Bangalore"
              value={form.location}
              onChange={(e) => setForm({ ...form, location: e.target.value })}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Drive Date"
              type="date"
              value={form.driveDate}
              onChange={(e) => setForm({ ...form, driveDate: e.target.value })}
            />
            <Input
              label="Registration Deadline"
              type="date"
              value={form.registrationDeadline}
              onChange={(e) => setForm({ ...form, registrationDeadline: e.target.value })}
            />
          </div>
          <Textarea
            label="Job Description"
            rows={4}
            placeholder="Describe the role, responsibilities, and requirements..."
            value={form.jobDescription}
            onChange={(e) => setForm({ ...form, jobDescription: e.target.value })}
          />
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!confirmAction}
        onClose={() => setConfirmAction(null)}
        onConfirm={executeStatusChange}
        title={`${confirmAction?.action === 'open' ? 'Open' : confirmAction?.action === 'close' ? 'Close' : 'Cancel'} Drive`}
        message={`Are you sure you want to ${confirmAction?.action} this drive for "${confirmAction?.drive.companyName}"?`}
        confirmLabel={confirmAction?.action === 'cancel' ? 'Cancel Drive' : 'Confirm'}
        variant={confirmAction?.action === 'cancel' ? 'danger' : 'primary'}
        loading={actionLoading}
      />
    </PageContainer>
  );
}
