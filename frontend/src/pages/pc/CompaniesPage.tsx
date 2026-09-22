import { useState, useCallback, useEffect } from 'react';
import { companyApi } from '../../api/api';
import type { Company } from '../../types';
import {
  Button,
  Input,
  Select,
  Textarea,
  Badge,
  Modal,
  DataTable,
  SearchInput,
  PageHeader,
  PageContainer,
  Dropdown,
  ConfirmDialog,
  FilterToolbar,
  EmptyState,
  ErrorState,
  Avatar,
} from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import { Building2, Eye, Globe, Pencil, ShieldOff } from 'lucide-react';
import toast from 'react-hot-toast';

const COMPANY_TYPES = [
  { label: 'Product', value: 'PRODUCT' },
  { label: 'Service', value: 'SERVICE' },
  { label: 'Core', value: 'CORE' },
  { label: 'Other', value: 'OTHER' },
];

const STATUS_OPTIONS = [
  { label: 'Active', value: 'active' },
  { label: 'Inactive', value: 'inactive' },
];

const typeLabel = (type: string | null | undefined) =>
  COMPANY_TYPES.find((t) => t.value === type)?.label ?? type ?? '—';

interface CompanyForm {
  name: string;
  companyType: string;
  website: string;
  description: string;
}

const emptyForm: CompanyForm = { name: '', companyType: '', website: '', description: '' };

export default function CompaniesPage() {
  const { user } = useAuth();
  const isPO = user?.role === 'PO';

  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const [companies, setCompanies] = useState<Company[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Company | null>(null);
  const [form, setForm] = useState<CompanyForm>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [viewTarget, setViewTarget] = useState<Company | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<Company | null>(null);
  const [deactivating, setDeactivating] = useState(false);

  const fetchCompanies = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await companyApi.getAll({ size: 1000 });
      const payload = res.data.data;
      setCompanies(
        Array.isArray(payload) ? payload : 'content' in payload ? payload.content : []
      );
    } catch (err) {
      setError(getErrorMessage(err));
      setCompanies([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCompanies();
  }, [fetchCompanies]);

  const hasFilters = !!search.trim() || !!typeFilter || !!statusFilter;

  const filteredCompanies = companies.filter((c) => {
    if (typeFilter && c.companyType !== typeFilter) return false;
    if (statusFilter === 'active' && !c.active) return false;
    if (statusFilter === 'inactive' && c.active) return false;
    const term = search.trim().toLowerCase();
    if (term && !`${c.name} ${c.website ?? ''}`.toLowerCase().includes(term)) return false;
    return true;
  });

  const clearFilters = () => {
    setSearch('');
    setTypeFilter('');
    setStatusFilter('');
  };

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setFormError(null);
    setModalOpen(true);
  };

  const openEdit = (c: Company) => {
    setEditing(c);
    setForm({
      name: c.name,
      companyType: c.companyType || '',
      website: c.website || '',
      description: c.description || '',
    });
    setFormError(null);
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    if (!form.name.trim()) {
      setFormError('Company name is required');
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      if (editing) {
        await companyApi.update(editing.id, {
          name: form.name.trim(),
          description: form.description.trim() || undefined,
          companyType: form.companyType || undefined,
          website: form.website.trim() || undefined,
        });
        toast.success('Company updated successfully');
      } else {
        await companyApi.create({
          name: form.name.trim(),
          description: form.description.trim() || undefined,
          companyType: form.companyType || undefined,
          website: form.website.trim() || undefined,
        });
        toast.success('Company created successfully');
      }
      setModalOpen(false);
      fetchCompanies();
    } catch (err) {
      const msg = getErrorMessage(err);
      setFormError(msg);
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  const handleDeactivate = async () => {
    if (!deactivateTarget) return;
    setDeactivating(true);
    try {
      await companyApi.delete(deactivateTarget.id);
      toast.success(`${deactivateTarget.name} deactivated`);
      setDeactivateTarget(null);
      fetchCompanies();
    } catch (err) {
      const msg = getErrorMessage(err);
      toast.error(msg);
    } finally {
      setDeactivating(false);
    }
  };

  return (
    <PageContainer>
        <PageHeader
          title="Companies"
          description="Manage organizations participating in campus placements."
          actions={
            isPO && (
              <Button onClick={openCreate}>
                <Building2 size={15} />
                Add Company
              </Button>
            )
          }
        />

        <div className="bg-white rounded-[12px] border border-neutral-200/80 shadow-soft p-3.5 mb-4">
          <FilterToolbar
            search={
              <SearchInput
                placeholder="Search companies..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            }
            filters={
              <div className="flex flex-wrap items-end gap-3">
                <Select
                  options={COMPANY_TYPES}
                  placeholder="Type"
                  value={typeFilter}
                  onChange={(e) => setTypeFilter(e.target.value)}
                  className="w-40"
                />
                <Select
                  options={STATUS_OPTIONS}
                  placeholder="Status"
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="w-40"
                />
                {hasFilters && (
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
            children={
              !loading &&
              !error && (
                <span className="text-[14px] text-neutral-500">
                  {filteredCompanies.length} total
                </span>
              )
            }
          />
        </div>

        {error ? (
          <ErrorState title="Unable to load companies" message={error} onRetry={fetchCompanies} />
        ) : (
          <>
            <div className="mb-3 flex items-center gap-2">
              <Building2 size={16} className="text-primary-600" />
              <span className="text-[15px] font-semibold text-neutral-900">Companies</span>
              <Badge variant="neutral">{filteredCompanies.length}</Badge>
            </div>
            {filteredCompanies.length === 0 && !loading ? (
              <div className="bg-white rounded-[14px] border border-neutral-200/60">
                <EmptyState
                  icon={<Building2 size={22} />}
                  title={hasFilters ? 'No companies match your filters' : 'No companies found'}
                  description={
                    hasFilters
                      ? 'Try adjusting your search or filters.'
                      : 'Add a company to get started.'
                  }
                />
              </div>
            ) : (
              <DataTable<Company>
                columns={[
                  {
                    key: 'name',
                    label: 'Company',
                    render: (c) => (
                      <div className="flex items-center gap-3 min-w-0">
                        <Avatar name={c.name || 'U'} size="sm" />
                        <div className="min-w-0">
                          <p className="text-[14.5px] font-medium text-neutral-900 truncate">{c.name}</p>
                          <p className="text-[12.5px] text-neutral-500 truncate">
                            {c.website ? (
                              <span className="inline-flex items-center gap-1">
                                <Globe size={11} />
                                {c.website.replace(/^https?:\/\//, '')}
                              </span>
                            ) : (
                              '—'
                            )}
                          </p>
                        </div>
                      </div>
                    ),
                  },
                  {
                    key: 'companyType',
                    label: 'Type',
                    render: (c) => <Badge variant="neutral">{typeLabel(c.companyType)}</Badge>,
                  },
                  {
                    key: 'active',
                    label: 'Status',
                    render: (c) => (
                      <Badge variant={c.active ? 'success' : 'neutral'} dot>
                        {c.active ? 'Active' : 'Inactive'}
                      </Badge>
                    ),
                  },
                  {
                    key: 'actions',
                    label: '',
                    className: 'w-12',
                    render: (c) => (
                      <Dropdown
                        items={[
                          {
                            label: 'View',
                            icon: <Eye size={14} />,
                            onClick: () => setViewTarget(c),
                          },
                          ...(isPO
                            ? [
                                {
                                  label: 'Edit',
                                  icon: <Pencil size={14} />,
                                  onClick: () => openEdit(c),
                                },
                                ...(c.active
                                  ? [
                                      {
                                        label: 'Deactivate',
                                        icon: <ShieldOff size={14} />,
                                        danger: true as const,
                                        onClick: () => setDeactivateTarget(c),
                                      },
                                    ]
                                  : []),
                              ]
                            : []),
                        ]}
                      />
                    ),
                  },
                ]}
                data={filteredCompanies}
                rowKey={(c) => c.id}
                loading={loading}
                density="compact"
              />
            )}
          </>
        )}

        <Modal
          isOpen={modalOpen}
          onClose={() => {
            if (!saving) setModalOpen(false);
          }}
          title={editing ? 'Edit Company' : 'Add Company'}
          description={editing ? 'Update company details.' : 'Add a new recruitment partner.'}
          size="md"
          actions={
            <>
              <Button variant="secondary" onClick={() => setModalOpen(false)} disabled={saving}>
                Cancel
              </Button>
              <Button onClick={handleSubmit} loading={saving}>
                {editing ? 'Save Changes' : 'Create Company'}
              </Button>
            </>
          }
        >
          {formError && (
            <div className="mb-4 rounded-[10px] border border-danger-100 bg-danger-50/70 px-3.5 py-2.5 text-[13px] text-danger-700">
              {formError}
            </div>
          )}
          <div className="space-y-4">
            <Input
              label="Company Name"
              required
              placeholder="e.g. Zoho"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
            <Select
              label="Company Type"
              options={COMPANY_TYPES}
              placeholder="Select type"
              value={form.companyType}
              onChange={(e) => setForm({ ...form, companyType: e.target.value })}
            />
            <Input
              label="Website"
              type="url"
              placeholder="https://www.example.com"
              value={form.website}
              onChange={(e) => setForm({ ...form, website: e.target.value })}
            />
            <Textarea
              label="Description"
              rows={3}
              placeholder="Brief description of the company..."
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </div>
        </Modal>

        <Modal isOpen={!!viewTarget} onClose={() => setViewTarget(null)} title="Company Details">
          {viewTarget && (
            <div className="flex flex-col items-center text-center pb-1">
              <Avatar name={viewTarget.name || 'U'} size="lg" />
              <h3 className="mt-3 text-[17px] font-semibold text-neutral-900">{viewTarget.name}</h3>
              <div className="mt-3 flex items-center gap-2 flex-wrap justify-center">
                <Badge variant="teal">{typeLabel(viewTarget.companyType)}</Badge>
                <Badge variant={viewTarget.active ? 'success' : 'neutral'} dot>
                  {viewTarget.active ? 'Active' : 'Inactive'}
                </Badge>
              </div>
              <div className="mt-5 w-full rounded-[12px] bg-neutral-50/80 divide-y divide-neutral-100/70 text-left">
                <div className="flex justify-between gap-6 px-4 py-2.5 text-[13.5px]">
                  <span className="text-neutral-500">Type</span>
                  <span className="font-medium text-neutral-800">{typeLabel(viewTarget.companyType)}</span>
                </div>
                <div className="flex justify-between gap-6 px-4 py-2.5 text-[13.5px]">
                  <span className="text-neutral-500">Website</span>
                  {viewTarget.website ? (
                    <a
                      href={viewTarget.website}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="font-medium text-info-500 hover:text-info-600 hover:underline break-all"
                    >
                      {viewTarget.website.replace(/^https?:\/\//, '')}
                    </a>
                  ) : (
                    <span className="font-medium text-neutral-500">—</span>
                  )}
                </div>
                <div className="flex justify-between gap-6 px-4 py-2.5 text-[13.5px]">
                  <span className="text-neutral-500">Status</span>
                  <span className="font-medium text-neutral-800">
                    {viewTarget.active ? 'Active' : 'Inactive'}
                  </span>
                </div>
                <div className="px-4 py-2.5 text-[13.5px]">
                  <span className="text-neutral-500">Description</span>
                  <p className="mt-1 text-[13.5px] leading-relaxed text-neutral-700 text-left">
                    {viewTarget.description?.trim() || '—'}
                  </p>
                </div>
              </div>
            </div>
          )}
        </Modal>

        <ConfirmDialog
          isOpen={!!deactivateTarget}
          onClose={() => setDeactivateTarget(null)}
          onConfirm={handleDeactivate}
          title="Deactivate Company"
          message={`Are you sure you want to deactivate "${deactivateTarget?.name}"? This action will remove the company from active listings.`}
          confirmLabel="Deactivate"
          variant="danger"
          loading={deactivating}
        />
      </PageContainer>
  );
}