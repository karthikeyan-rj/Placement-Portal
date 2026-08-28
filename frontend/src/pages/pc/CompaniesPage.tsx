import { useState } from 'react';
import { companyApi } from '../../api/api';
import type { Company } from '../../types';
import {
  Button,
  SearchInput,
  Input,
  Select,
  Textarea,
  Badge,
  Modal,
  DataTable,
  Pagination,
  PageHeader,
  PageContainer,
  Dropdown,
  ConfirmDialog,
  FilterToolbar,
  notify,
} from '../../components/ui';
import { usePaginatedData } from '../../hooks/useApi';
import { Building2, Globe } from 'lucide-react';

const COMPANY_TYPES = [
  { label: 'Product', value: 'PRODUCT' },
  { label: 'Service', value: 'SERVICE' },
  { label: 'Consulting', value: 'CONSULTING' },
  { label: 'Other', value: 'OTHER' },
];

interface CompanyForm {
  name: string;
  companyType: string;
  website: string;
  description: string;
}

const emptyForm: CompanyForm = { name: '', companyType: '', website: '', description: '' };

export default function CompaniesPage() {
  const [search, setSearch] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Company | null>(null);
  const [form, setForm] = useState<CompanyForm>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState<Company | null>(null);

  const {
    data: companies,
    loading,
    page,
    totalPages,
    totalElements,
    setPage,
    refresh,
  } = usePaginatedData<Company>({
    url: '/companies',
    params: search ? { search } : {},
  });

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
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
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    if (!form.name.trim()) {
      notify.error('Company name is required');
      return;
    }
    setSaving(true);
    try {
      if (editing) {
        await companyApi.update(editing.id, form);
        notify.success('Company updated successfully');
      } else {
        await companyApi.create(form);
        notify.success('Company created successfully');
      }
      setModalOpen(false);
      refresh();
    } catch {
      notify.error(editing ? 'Failed to update company' : 'Failed to create company');
    } finally {
      setSaving(false);
    }
  };

  const handleDeactivate = async () => {
    if (!deleting) return;
    try {
      await companyApi.delete(deleting.id);
      notify.success('Company deactivated');
      setDeleting(null);
      refresh();
    } catch {
      notify.error('Failed to deactivate company');
    }
  };

  const columns = [
    {
      key: 'name',
      label: 'Company',
      render: (c: Company) => (
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-brand-navy text-white shrink-0">
            <Building2 size={16} />
          </div>
          <span className="font-medium text-[15px] text-neutral-900">{c.name}</span>
        </div>
      ),
    },
    {
      key: 'companyType',
      label: 'Type',
      render: (c: Company) => (
        <Badge variant="neutral">{c.companyType || '—'}</Badge>
      ),
    },
    {
      key: 'website',
      label: 'Website',
      render: (c: Company) =>
        c.website ? (
          <a
            href={c.website}
            target="_blank"
            rel="noopener noreferrer"
            className="text-[14px] text-info-500 hover:text-info-600 hover:underline flex items-center gap-1"
            onClick={(e) => e.stopPropagation()}
          >
            <Globe size={13} />
            {c.website.replace(/^https?:\/\//, '')}
          </a>
        ) : (
          <span className="text-[14px] text-neutral-400">—</span>
        ),
    },
    {
      key: 'active',
      label: 'Status',
      render: (c: Company) => (
        <Badge variant={c.active ? 'success' : 'neutral'} dot={true}>
          {c.active ? 'Active' : 'Inactive'}
        </Badge>
      ),
    },
    {
      key: 'actions',
      label: '',
      className: 'w-12',
      render: (c: Company) => (
        <Dropdown
          items={[
            { label: 'Edit', onClick: () => openEdit(c) },
            ...(c.active
              ? [{ label: 'Deactivate', onClick: () => setDeleting(c), danger: true as const }]
              : []),
          ]}
        />
      ),
    },
  ];

  return (
    <PageContainer>
      <PageHeader
        title="Companies"
        description="Manage organizations participating in campus placements."
        actions={
          <Button onClick={openCreate}>
            <Building2 size={15} />
            Add Company
          </Button>
        }
      />

      <FilterToolbar
        search={
          <SearchInput
            placeholder="Search companies..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
        children={
          !loading && (
            <span className="text-[14px] text-neutral-500">{totalElements} companies</span>
          )
        }
      />

      <DataTable<Company>
        columns={columns}
        data={companies}
        rowKey={(c) => c.id}
        loading={loading}
        emptyMessage="No companies found"
        emptyIcon={<Building2 size={40} />}
      />

      {!loading && totalPages > 1 && (
        <div className="mt-6">
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={setPage}
          />
        </div>
      )}

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
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
        <div className="space-y-4">
          <Input
            label="Company Name"
            required
            placeholder="e.g. Acme Corp"
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
            placeholder="https://..."
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

      <ConfirmDialog
        isOpen={!!deleting}
        onClose={() => setDeleting(null)}
        onConfirm={handleDeactivate}
        title="Deactivate Company"
        message={`Are you sure you want to deactivate "${deleting?.name}"? This action will remove the company from active listings.`}
        confirmLabel="Deactivate"
        variant="danger"
      />
    </PageContainer>
  );
}
