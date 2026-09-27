import { useState, useEffect, useCallback } from 'react';
import { useLocation } from 'react-router-dom';
import { Pencil, Shield, Building2, Mail, BadgeCheck, AlertCircle, Briefcase, GraduationCap } from 'lucide-react';
import { profileApi, departmentApi } from '../../api/api';
import { getErrorMessage } from '../../api/axios';
import { useEffectiveRole } from '../../hooks/useEffectiveRole';
import { roleLabels } from '../../config/navigation';
import type { ProfileResponse, Department } from '../../types';
import { Badge, Skeleton, PageHeader, Avatar, Button, notify } from '../../components/ui';
import ChangePasswordCard from '../../components/profile/ChangePasswordCard';
import EditStaffProfileModal from '../../components/profile/EditStaffProfileModal';
import { ProfessionalSummary, EditProfileButton } from '../../components/profile/StaffProfessionalPanels';
import { AccountField, ErrorPanel, Field, SectionCard, ValueOrEmpty, isBlank } from '../../components/profile/staffProfileParts';

type Tab = 'overview' | 'professional' | 'department' | 'security';

const ROLE_SCOPE: Record<'PC' | 'PO', string[]> = {
  PC: [
    'Manage students within your department',
    'Manage companies and placement drives',
    'Coordinate with your department representatives',
  ],
  PO: [
    'Manage students and departments institution-wide',
    'Oversee companies, drives and placement records',
    'Administer coordinators, representatives and audit logs',
  ],
};

const ROLE_ICON = { PC: GraduationCap, PO: Shield } as const;

export default function StaffProfilePage() {
  const effectiveRole = useEffectiveRole();
  const isPC = effectiveRole === 'PC';
  const roleKey: 'PC' | 'PO' = isPC ? 'PC' : 'PO';
  const roleLabel = roleLabels[roleKey];

  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [dept, setDept] = useState<Department | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const location = useLocation();

  useEffect(() => {
    const state = location.state as { security?: boolean } | null;
    if (state?.security) {
      setActiveTab('security');
      window.scrollTo({ top: 0 });
    }
  }, [location.state]);

  const fetchProfile = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await profileApi.getMyProfile();
      const data: ProfileResponse = res.data?.data ?? res.data;
      setProfile(data);
      if (data?.departmentId) {
        try {
          const dres = await departmentApi.getById(data.departmentId);
          setDept(dres.data?.data ?? dres.data);
        } catch {
          setDept(null);
        }
      }
    } catch (e) {
      setError(getErrorMessage(e) || 'Failed to load profile');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchProfile(); }, [fetchProfile]);

  const staff = profile?.staffProfile ?? null;
  const hasProfessionalDetails =
    !isBlank(staff?.designation) ||
    !isBlank(staff?.officeLocation) ||
    !isBlank(staff?.phone) ||
    !isBlank(staff?.bio) ||
    !isBlank(staff?.linkedinUrl) ||
    (staff?.expertise?.length ?? 0) > 0;

  const save = async (values: {
    phone: string;
    designation: string;
    officeLocation: string;
    bio: string;
    linkedinUrl: string;
    expertise: string[];
  }) => {
    setSaving(true);
    try {
      await profileApi.updateMyStaffProfile(values);
      notify.success('Profile updated');
      setEditOpen(false);
      // Re-read so the tabs show exactly what was persisted.
      await fetchProfile();
    } catch (e) {
      notify.error(getErrorMessage(e) || 'Failed to update profile');
    } finally {
      setSaving(false);
    }
  };

  const tabs: { key: Tab; label: string }[] = isPC
    ? [
        { key: 'overview', label: 'Overview' },
        { key: 'professional', label: 'Professional' },
        { key: 'department', label: 'Department' },
        { key: 'security', label: 'Security' },
      ]
    : [
        { key: 'overview', label: 'Overview' },
        { key: 'professional', label: 'Professional' },
        { key: 'security', label: 'Security' },
      ];

  if (loading) {
    return (
      <div className="max-w-[1200px] mx-auto space-y-6 animate-fadeIn">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-[150px] w-full rounded-[16px]" />
        <Skeleton className="h-[240px] w-full rounded-[16px]" />
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="max-w-[1200px] mx-auto">
        <PageHeader title="My Profile" description="Your account information in one place." />
        <ErrorPanel message={error || 'Unable to load profile'} onRetry={fetchProfile} />
      </div>
    );
  }

  const RoleIcon = ROLE_ICON[roleKey];

  return (
    <div className="max-w-[1200px] mx-auto space-y-6">
      <PageHeader title="My Profile" description="Your account information in one place." />

      {/* Hero header */}
      <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft p-5 animate-fadeIn">
        <div className="flex items-start gap-4 sm:gap-5 min-w-0">
          <Avatar name={profile.name} size="lg" />
          <div className="min-w-0 flex-1">
            <h2 className="text-[20px] font-bold text-neutral-900 break-words">{profile.name}</h2>
            <p className="text-[13px] text-neutral-500 mt-0.5 break-all">{profile.email}</p>
            <div className="flex flex-wrap items-center gap-2 mt-2">
              <span className="px-2.5 py-0.5 rounded-full bg-primary-50 text-primary-600 text-xs font-medium">{roleLabel}</span>
              {isPC && profile.departmentName && (
                <span className="px-2.5 py-0.5 rounded-full bg-neutral-100 text-neutral-600 text-xs font-medium">{profile.departmentName}</span>
              )}
              <Badge variant={profile.active ? 'success' : 'neutral'} dot size="sm">
                {profile.active ? 'Active' : 'Inactive'}
              </Badge>
            </div>
          </div>
          <Button variant="secondary" onClick={() => setEditOpen(true)} className="hidden sm:inline-flex shrink-0">
            <Pencil size={15} />
            Edit profile
          </Button>
        </div>
        <Button variant="secondary" onClick={() => setEditOpen(true)} className="sm:hidden w-full mt-4">
          <Pencil size={15} />
          Edit profile
        </Button>
      </div>

      {/* Tab bar */}
      <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft animate-fadeIn">
        <div className="px-4 pt-3 sm:px-5">
          <div className="overflow-x-auto -mx-1 px-1">
            <div
              role="tablist"
              aria-label="Profile sections"
              className="flex items-center gap-0.5 border-b border-neutral-200/60 w-max min-w-full"
            >
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  role="tab"
                  id={`tab-${tab.key}`}
                  aria-selected={activeTab === tab.key}
                  aria-controls={`panel-${tab.key}`}
                  onClick={() => setActiveTab(tab.key)}
                  className={`px-3.5 py-2 -mb-px border-b-2 text-[13.5px] font-medium whitespace-nowrap transition-all duration-150 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/40 ${
                    activeTab === tab.key
                      ? 'border-primary-500 text-primary-600'
                      : 'border-transparent text-neutral-500 hover:text-neutral-700 hover:border-neutral-300'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="p-4 sm:p-5">
          {/*
            Every panel stays mounted and inactive ones are hidden, so each
            tab's aria-controls always resolves to a real element.
          */}
          <div role="tabpanel" id="panel-overview" aria-labelledby="tab-overview" hidden={activeTab !== 'overview'}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5 animate-fadeIn">
              <SectionCard title="Account Information" icon={<Mail size={17} />}>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
                  <AccountField label="Full Name" value={profile.name} />
                  <AccountField label="Email" value={profile.email} />
                  <AccountField label="Role" value={roleLabel} />
                  <div className="min-w-0">
                    <p className="text-[11.5px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">Account Status</p>
                    <Badge variant={profile.active ? 'success' : 'neutral'} dot size="sm">
                      {profile.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </div>
                  {isPC ? (
                    <AccountField label="Assigned Department" value={profile.departmentName} />
                  ) : (
                    <AccountField label="Scope" value="Institution-wide placement operations" />
                  )}
                </div>
              </SectionCard>

              <SectionCard title="Role & Access" icon={<BadgeCheck size={17} />}>
                <p className="text-[13.5px] text-neutral-600 leading-relaxed">
                  {isPC
                    ? `You are a ${roleLabel}${profile.departmentName ? ` for ${profile.departmentName}` : ''}.`
                    : `You are a ${roleLabel} responsible for institution-wide placement operations.`}
                </p>
                <ul className="mt-3 space-y-2 text-[13.5px] text-neutral-600">
                  {ROLE_SCOPE[roleKey].map((text) => (
                    <li key={text} className="flex items-start gap-2.5">
                      <span className="shrink-0 flex items-center justify-center w-6 h-6 rounded-[8px] bg-neutral-100 text-neutral-500">
                        <RoleIcon size={13} />
                      </span>
                      <span className="mt-0.5">{text}</span>
                    </li>
                  ))}
                </ul>
              </SectionCard>

              <SectionCard
                title="Contact Information"
                icon={<Briefcase size={17} />}
                action={<EditProfileButton onClick={() => setEditOpen(true)} />}
                className="md:col-span-2"
              >
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-4">
                  <Field label="Phone">
                    <ValueOrEmpty value={staff?.phone} />
                  </Field>
                  <Field label="Designation">
                    <ValueOrEmpty value={staff?.designation} />
                  </Field>
                  <Field label="Office Location">
                    <ValueOrEmpty value={staff?.officeLocation} />
                  </Field>
                </div>
                {!hasProfessionalDetails && (
                  <p className="mt-4 flex items-center gap-1.5 text-[13px] text-neutral-500">
                    <AlertCircle size={14} className="shrink-0 text-neutral-400" aria-hidden="true" />
                    You have not added professional details yet. Use Edit profile to add them.
                  </p>
                )}
              </SectionCard>
            </div>
          </div>

          {/* ── PROFESSIONAL ── */}
          <div
            role="tabpanel"
            id="panel-professional"
            aria-labelledby="tab-professional"
            hidden={activeTab !== 'professional'}
          >
            <div className="animate-fadeIn">
              <div className="flex items-center justify-between gap-3 mb-4">
                <div>
                  <h3 className="text-[16px] font-semibold text-neutral-900">Professional Profile</h3>
                  <p className="text-[13px] text-neutral-500 mt-0.5">
                    Shared with students and departments so they know who they are dealing with.
                  </p>
                </div>
                <Button variant="secondary" onClick={() => setEditOpen(true)} className="shrink-0">
                  <Pencil size={15} />
                  Edit
                </Button>
              </div>
              <ProfessionalSummary staffProfile={staff} />
            </div>
          </div>

          {/* ── DEPARTMENT (PC only) ── */}
          {isPC && (
            <div
              role="tabpanel"
              id="panel-department"
              aria-labelledby="tab-department"
              hidden={activeTab !== 'department'}
            >
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5 animate-fadeIn">
                <SectionCard title="Department" icon={<Building2 size={17} />}>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
                    <AccountField label="Department Name" value={profile.departmentName} />
                    <AccountField label="Department ID" value={profile.departmentId != null ? String(profile.departmentId) : null} />
                    {dept && (
                      <>
                        <div className="min-w-0">
                          <p className="text-[11.5px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">Status</p>
                          <Badge variant={dept.active ? 'success' : 'neutral'} dot size="sm">
                            {dept.active ? 'Active' : 'Inactive'}
                          </Badge>
                        </div>
                        <Field label="PR Limit">{dept.prLimit != null ? String(dept.prLimit) : null}</Field>
                      </>
                    )}
                  </div>
                </SectionCard>
                <SectionCard title="Coordination" icon={<GraduationCap size={17} />}>
                  <p className="text-[13.5px] text-neutral-600 leading-relaxed">
                    As {roleLabel}, you coordinate placement activities for {profile.departmentName || 'your department'}, including student records, companies and drives within your department.
                  </p>
                </SectionCard>
              </div>
            </div>
          )}

          {/* ── SECURITY ── */}
          <div
            role="tabpanel"
            id="panel-security"
            aria-labelledby="tab-security"
            hidden={activeTab !== 'security'}
          >
            <div className="max-w-xl animate-fadeIn">
              <div className="flex items-center gap-2.5 mb-1">
                <span className="flex items-center justify-center w-9 h-9 rounded-[10px] bg-primary-50 text-primary-500">
                  <Shield size={17} />
                </span>
                <h3 className="text-[16px] font-semibold text-neutral-900">Security</h3>
              </div>
              <p className="text-[13.5px] text-neutral-500 mb-5">
                Use a password of at least 6 characters that you do not reuse elsewhere. Your current password is required to make changes.
              </p>
              <ChangePasswordCard />
            </div>
          </div>
        </div>
      </div>

      <EditStaffProfileModal
        isOpen={editOpen}
        staffProfile={staff}
        saving={saving}
        onClose={() => setEditOpen(false)}
        onSave={save}
      />
    </div>
  );
}
