import { useState, useEffect, useCallback, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { profileApi, departmentApi } from '../../api/api';
import { getErrorMessage } from '../../api/axios';
import { useEffectiveRole } from '../../hooks/useEffectiveRole';
import { roleLabels } from '../../config/navigation';
import type { ProfileResponse, Department } from '../../types';
import { Button, Badge, Skeleton, PageHeader, Avatar } from '../../components/ui';
import ChangePasswordCard from '../../components/profile/ChangePasswordCard';
import {
  User as UserIcon,
  Shield,
  Building2,
  Mail,
  BadgeCheck,
  AlertCircle,
  Briefcase,
  GraduationCap,
} from 'lucide-react';

type Tab = 'overview' | 'department' | 'security';

function isBlank(v: unknown): boolean {
  return v === null || v === undefined || v === '';
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <p className="text-[11.5px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">{label}</p>
      <div className="min-h-[20px] break-words text-[14.5px] font-medium text-neutral-800">
        {isBlank(children) ? <span className="text-neutral-300">—</span> : children}
      </div>
    </div>
  );
}

function SectionCard({
  title,
  icon,
  children,
  className = '',
}: {
  title: string;
  icon: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`bg-white rounded-[14px] border border-neutral-200/80 shadow-soft p-4 sm:p-5 ${className}`}>
      <div className="flex items-center gap-2.5 mb-3.5">
        <span className="shrink-0 flex items-center justify-center w-8 h-8 rounded-[9px] bg-primary-50 text-primary-500">
          {icon}
        </span>
        <h3 className="text-[15px] font-semibold text-neutral-900 truncate">{title}</h3>
      </div>
      {children}
    </div>
  );
}

export default function StaffProfilePage() {
  const effectiveRole = useEffectiveRole();
  const isPC = effectiveRole === 'PC';
  const roleKey = isPC ? 'PC' : 'PO';
  const roleLabel = roleLabels[roleKey];

  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [dept, setDept] = useState<Department | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>('overview');
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

  const tabs: { key: Tab; label: string }[] = isPC
    ? [
        { key: 'overview', label: 'Overview' },
        { key: 'department', label: 'Department' },
        { key: 'security', label: 'Security' },
      ]
    : [
        { key: 'overview', label: 'Overview' },
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
        <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft p-10 text-center animate-fadeIn">
          <span className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-danger-50 text-danger-500 mb-3">
            <AlertCircle size={22} />
          </span>
          <p className="text-[15px] text-neutral-500">{error || 'Unable to load profile'}</p>
          <Button variant="secondary" className="mt-4" onClick={fetchProfile}>Retry</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-[1200px] mx-auto space-y-6">
      <PageHeader title="My Profile" description="Your account information in one place." />

      {/* Hero header */}
      <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft p-5 animate-fadeIn">
        <div className="flex items-center gap-4 sm:gap-5 min-w-0">
          <Avatar name={profile.name} size="lg" />
          <div className="min-w-0">
            <h2 className="text-[20px] font-bold text-neutral-900">{profile.name}</h2>
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
        </div>
      </div>

      {/* Tab bar */}
      <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft animate-fadeIn">
        <div className="px-4 pt-3 sm:px-5">
          <div className="overflow-x-auto -mx-1 px-1">
            <div className="flex items-center gap-0.5 border-b border-neutral-200/60 w-max min-w-full">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setActiveTab(tab.key)}
                  className={`px-3.5 py-2 -mb-px border-b-2 text-[13.5px] font-medium whitespace-nowrap transition-all duration-150 ${
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
          {/* ── OVERVIEW ── */}
          {activeTab === 'overview' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5 animate-fadeIn">
              <SectionCard title="Account Information" icon={<UserIcon size={17} />}>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
                  <Field label="Full Name">{profile.name}</Field>
                  <Field label="Email">
                    <span className="inline-flex items-center gap-1.5 break-all">
                      <Mail size={13} className="text-neutral-500 shrink-0" />
                      {profile.email}
                    </span>
                  </Field>
                  <Field label="Role">{roleLabel}</Field>
                  <Field label="Account Status">
                    <Badge variant={profile.active ? 'success' : 'neutral'} dot size="sm">
                      {profile.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </Field>
                  {isPC ? (
                    <Field label="Assigned Department">{profile.departmentName}</Field>
                  ) : (
                    <Field label="Scope">Global placement administration</Field>
                  )}
                </div>
              </SectionCard>

              <SectionCard title="Role & Access" icon={<BadgeCheck size={17} />}>
                <p className="text-[13.5px] text-neutral-600 leading-relaxed">
                  {isPC
                    ? `You are a ${roleLabel}${profile.departmentName ? ` for ${profile.departmentName}` : ''}.`
                    : `You are a ${roleLabel} responsible for global placement administration across the institution.`}
                </p>
                <ul className="mt-3 space-y-2 text-[13.5px] text-neutral-600">
                  {(isPC
                    ? [
                        { icon: GraduationCap, text: 'Manage students within your department' },
                        { icon: Briefcase, text: 'Manage companies and placement drives' },
                        { icon: Building2, text: 'Coordinate with your department representatives' },
                      ]
                    : [
                        { icon: GraduationCap, text: 'Manage students and departments institution-wide' },
                        { icon: Briefcase, text: 'Oversee companies, drives and placement records' },
                        { icon: Shield, text: 'Administer coordinators, representatives and audit logs' },
                      ]
                  ).map((item) => {
                    const Icon = item.icon;
                    return (
                      <li key={item.text} className="flex items-start gap-2.5">
                        <span className="shrink-0 flex items-center justify-center w-6 h-6 rounded-[8px] bg-neutral-100 text-neutral-500">
                          <Icon size={13} />
                        </span>
                        <span className="mt-0.5">{item.text}</span>
                      </li>
                    );
                  })}
                </ul>
              </SectionCard>
            </div>
          )}

          {/* ── DEPARTMENT (PC) ── */}
          {activeTab === 'department' && isPC && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5 animate-fadeIn">
              <SectionCard title="Department" icon={<Building2 size={17} />}>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
                  <Field label="Department Name">{profile.departmentName}</Field>
                  <Field label="Department ID">{profile.departmentId}</Field>
                  {dept && (
                    <>
                      <Field label="Status">
                        <Badge variant={dept.active ? 'success' : 'neutral'} dot size="sm">
                          {dept.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </Field>
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
          )}

          {/* ── SECURITY ── */}
          {activeTab === 'security' && (
            <div className="max-w-xl animate-fadeIn">
              <div className="flex items-center gap-2.5 mb-1">
                <span className="flex items-center justify-center w-9 h-9 rounded-[10px] bg-primary-50 text-primary-500"><Shield size={17} /></span>
                <h3 className="text-[16px] font-semibold text-neutral-900">Security</h3>
              </div>
              <p className="text-[13.5px] text-neutral-500 mb-5">
                Use a password of at least 6 characters that you do not reuse elsewhere. Your current password is required to make changes.
              </p>
              <ChangePasswordCard />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
