import { useState, useEffect, useCallback, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { studentApi, profileApi } from '../../api/api';
import { getErrorMessage } from '../../api/axios';
import { useEffectiveRole } from '../../hooks/useEffectiveRole';
import { roleLabels } from '../../config/navigation';
import type { StudentProfile, ProfileResponse } from '../../types';
import ChangePasswordCard from '../../components/profile/ChangePasswordCard';
import {
  Button,
  Input,
  Textarea,
  Badge,
  Modal,
  Skeleton,
  PageHeader,
  Avatar,
  notify,
} from '../../components/ui';
import {
  Pencil,
  GraduationCap,
  Briefcase,
  Shield,
  Calendar,
  Building2,
  ExternalLink,
  CheckCircle2,
  BarChart3,
  AlertCircle,
  User,
  Phone,
  Info,
} from 'lucide-react';

type Tab = 'overview' | 'academics' | 'professional' | 'placement' | 'security';

type EditTab = 'personal' | 'academics' | 'professional' | 'placement';

const tabItems: { key: Tab; label: string }[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'academics', label: 'Academics' },
  { key: 'professional', label: 'Professional' },
  { key: 'placement', label: 'Placement' },
  { key: 'security', label: 'Security' },
];

const editTabItems: { key: EditTab; label: string }[] = [
  { key: 'personal', label: 'Personal' },
  { key: 'academics', label: 'Academics' },
  { key: 'professional', label: 'Professional' },
  { key: 'placement', label: 'Placement' },
];

function isEmpty(v: unknown): boolean {
  return v === null || v === undefined || v === '';
}

function formatPlacementStatus(s: string | null | undefined): string {
  if (!s) return '—';
  switch (s) {
    case 'PLACED': return 'Placed';
    case 'NOT_PLACED': return 'Not Placed';
    case 'BLOCKED': return 'Blocked';
    case 'NOT_INTERESTED': return 'Not Interested';
    default:
      return s.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
  }
}

function placementBadgeVariant(s: string | null | undefined): 'success' | 'danger' | 'neutral' | 'info' {
  switch (s) {
    case 'PLACED': return 'success';
    case 'BLOCKED': return 'danger';
    case 'NOT_INTERESTED': return 'info';
    default: return 'neutral';
  }
}

function formatDate(d: string | null | undefined): string {
  if (!d) return '—';
  try {
    return new Date(d).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  } catch {
    return d;
  }
}

function formatPackage(lpa: number | null | undefined): string {
  if (lpa == null) return '—';
  return `${Number(lpa)} LPA`;
}

function safeUrl(u: string | null | undefined): string | null {
  if (!u) return null;
  try {
    const url = new URL(u);
    return url.protocol === 'http:' || url.protocol === 'https:' ? u : null;
  } catch {
    return null;
  }
}

function missingProfileItems(p: StudentProfile): string[] {
  const items: string[] = [];
  if (isEmpty(p.phone)) items.push('Phone number');
  if (isEmpty(p.dateOfBirth)) items.push('Date of birth');
  if (p.cgpa == null) items.push('CGPA');
  if (isEmpty(p.skills)) items.push('Skills');
  if (isEmpty(p.resumeUrl) && isEmpty(p.githubUrl) && isEmpty(p.linkedinUrl) && isEmpty(p.portfolioUrl)) {
    items.push('Professional links');
  }
  return items;
}

function TextValue({ value }: { value: ReactNode }) {
  if (isEmpty(value)) return <span className="text-[14.5px] text-neutral-300">—</span>;
  return <span className="text-[14.5px] font-medium text-neutral-800">{value}</span>;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <p className="text-[11.5px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">{label}</p>
      <div className="min-h-[20px] break-words">{children ?? <TextValue value={null} />}</div>
    </div>
  );
}

function SectionCard({
  title,
  icon,
  action,
  children,
  className = '',
}: {
  title: string;
  icon: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`bg-white rounded-[14px] border border-neutral-200/80 shadow-soft p-4 sm:p-5 ${className}`}>
      <div className="flex items-center justify-between gap-3 mb-3.5">
        <div className="flex items-center gap-2.5 min-w-0">
          <span className="shrink-0 flex items-center justify-center w-8 h-8 rounded-[9px] bg-primary-50 text-primary-500">
            {icon}
          </span>
          <h3 className="text-[15px] font-semibold text-neutral-900 truncate">{title}</h3>
        </div>
        {action && <div className="shrink-0">{action}</div>}
      </div>
      {children}
    </div>
  );
}

function LinkCard({ label, url }: { label: string; url: string | null }) {
  const valid = safeUrl(url);
  return (
    <div className="flex items-center justify-between gap-3 p-3.5 rounded-[12px] bg-neutral-50/60 border border-neutral-100 hover:border-primary-200/60 transition-colors">
      <div className="min-w-0">
        <p className="text-[11.5px] font-semibold uppercase tracking-wider text-neutral-500 mb-0.5">{label}</p>
        {valid ? (
          <a
            href={valid}
            target="_blank"
            rel="noopener noreferrer"
            className="text-[13.5px] text-primary-600 hover:underline flex items-center gap-1 break-all"
          >
            {valid.length > 42 ? `${valid.slice(0, 42)}…` : valid}
            <ExternalLink size={12} className="shrink-0" />
          </a>
        ) : url ? (
          <p className="text-[13.5px] text-neutral-500 break-all" title={`${url} is not a valid http(s) link`}>
            {url.length > 42 ? `${url.slice(0, 42)}…` : url}
          </p>
        ) : (
          <p className="text-[13.5px] text-neutral-300">Not provided</p>
        )}
      </div>
    </div>
  );
}

function ProfileTabs({ active, onChange }: { active: Tab; onChange: (t: Tab) => void }) {
  return (
    <div className="overflow-x-auto -mx-1 px-1">
      <div className="flex items-center gap-0.5 border-b border-neutral-200/60 w-max min-w-full">
        {tabItems.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => onChange(tab.key)}
            className={`px-3.5 py-2 -mb-px border-b-2 text-[13.5px] font-medium whitespace-nowrap transition-all duration-150 ${
              active === tab.key
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-neutral-500 hover:text-neutral-700 hover:border-neutral-300'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
    </div>
  );
}

interface ProfileFormState {
  phone: string;
  dateOfBirth: string;
  batch: string;
  section: string;
}

interface AcademicFormState {
  tenthPercentage: string;
  twelfthPercentage: string;
  diplomaPercentage: string;
  cgpa: string;
  activeBacklogs: string;
  historyOfBacklogs: string;
}

interface ProfessionalFormState {
  skills: string;
  certifications: string;
  projects: string;
  resumeUrl: string;
  githubUrl: string;
  linkedinUrl: string;
  portfolioUrl: string;
}

export default function ProfilePage() {
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [account, setAccount] = useState<ProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const location = useLocation();

  const role = useEffectiveRole();
  const isPR = role === 'PR';
  const canEdit = !isPR;

  const [editOpen, setEditOpen] = useState(false);
  const [editTab, setEditTab] = useState<EditTab>('personal');
  const [saving, setSaving] = useState(false);

  const [personalForm, setPersonalForm] = useState<ProfileFormState>({ phone: '', dateOfBirth: '', batch: '', section: '' });
  const [academicForm, setAcademicForm] = useState<AcademicFormState>({ tenthPercentage: '', twelfthPercentage: '', diplomaPercentage: '', cgpa: '', activeBacklogs: '', historyOfBacklogs: '' });
  const [professionalForm, setProfessionalForm] = useState<ProfessionalFormState>({ skills: '', certifications: '', projects: '', resumeUrl: '', githubUrl: '', linkedinUrl: '', portfolioUrl: '' });
  const [interested, setInterested] = useState(true);

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
      setAccount(data);
      setProfile(data?.studentProfile ?? null);
    } catch (e) {
      setError(getErrorMessage(e) || 'Failed to load profile');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchProfile(); }, [fetchProfile]);

  const openEdit = (tab: EditTab = 'personal') => {
    if (!profile) return;
    setPersonalForm({
      phone: profile.phone || '',
      dateOfBirth: profile.dateOfBirth || '',
      batch: profile.batch || '',
      section: profile.section || '',
    });
    setAcademicForm({
      tenthPercentage: profile.tenthPercentage != null ? String(profile.tenthPercentage) : '',
      twelfthPercentage: profile.twelfthPercentage != null ? String(profile.twelfthPercentage) : '',
      diplomaPercentage: profile.diplomaPercentage != null ? String(profile.diplomaPercentage) : '',
      cgpa: profile.cgpa != null ? String(profile.cgpa) : '',
      activeBacklogs: profile.activeBacklogs != null ? String(profile.activeBacklogs) : '',
      historyOfBacklogs: profile.historyOfBacklogs != null ? String(profile.historyOfBacklogs) : '',
    });
    setProfessionalForm({
      skills: profile.skills || '',
      certifications: profile.certifications || '',
      projects: profile.projects || '',
      resumeUrl: profile.resumeUrl || '',
      githubUrl: profile.githubUrl || '',
      linkedinUrl: profile.linkedinUrl || '',
      portfolioUrl: profile.portfolioUrl || '',
    });
    setInterested(profile.placementInterested ?? true);
    setEditTab(tab);
    setEditOpen(true);
  };

  const savePersonal = async () => {
    if (!profile) return;
    setSaving(true);
    try {
      await studentApi.updateProfile(profile.id, {
        phone: personalForm.phone.trim() !== '' ? personalForm.phone.trim() : undefined,
        dateOfBirth: personalForm.dateOfBirth || undefined,
        batch: personalForm.batch.trim() !== '' ? personalForm.batch.trim() : undefined,
        section: personalForm.section.trim() !== '' ? personalForm.section.trim() : undefined,
      });
      notify.success('Personal information updated');
      setEditOpen(false);
      fetchProfile();
    } catch (e) {
      notify.error(getErrorMessage(e) || 'Failed to update personal information');
    } finally {
      setSaving(false);
    }
  };

  const savePlacement = async () => {
    if (!profile) return;
    setSaving(true);
    try {
      await studentApi.updateProfile(profile.id, { placementInterested: interested });
      notify.success('Placement preference updated');
      setEditOpen(false);
      fetchProfile();
    } catch (e) {
      notify.error(getErrorMessage(e) || 'Failed to update placement preference');
    } finally {
      setSaving(false);
    }
  };

  const saveAcademic = async () => {
    if (!profile) return;
    setSaving(true);
    try {
      await studentApi.updateAcademic(profile.id, {
        tenthPercentage: academicForm.tenthPercentage !== '' ? Number(academicForm.tenthPercentage) : undefined,
        twelfthPercentage: academicForm.twelfthPercentage !== '' ? Number(academicForm.twelfthPercentage) : undefined,
        diplomaPercentage: academicForm.diplomaPercentage !== '' ? Number(academicForm.diplomaPercentage) : undefined,
        cgpa: academicForm.cgpa !== '' ? Number(academicForm.cgpa) : undefined,
        activeBacklogs: academicForm.activeBacklogs !== '' ? Number(academicForm.activeBacklogs) : undefined,
        historyOfBacklogs: academicForm.historyOfBacklogs !== '' ? Number(academicForm.historyOfBacklogs) : undefined,
      });
      notify.success('Academic details updated');
      setEditOpen(false);
      fetchProfile();
    } catch (e) {
      notify.error(getErrorMessage(e) || 'Failed to update academic details');
    } finally {
      setSaving(false);
    }
  };

  const saveProfessional = async () => {
    if (!profile) return;
    setSaving(true);
    try {
      await studentApi.updateProfessional(profile.id, {
        skills: professionalForm.skills.trim() !== '' ? professionalForm.skills.trim() : undefined,
        certifications: professionalForm.certifications.trim() !== '' ? professionalForm.certifications.trim() : undefined,
        projects: professionalForm.projects.trim() !== '' ? professionalForm.projects.trim() : undefined,
        resumeUrl: professionalForm.resumeUrl.trim() !== '' ? professionalForm.resumeUrl.trim() : undefined,
        githubUrl: professionalForm.githubUrl.trim() !== '' ? professionalForm.githubUrl.trim() : undefined,
        linkedinUrl: professionalForm.linkedinUrl.trim() !== '' ? professionalForm.linkedinUrl.trim() : undefined,
        portfolioUrl: professionalForm.portfolioUrl.trim() !== '' ? professionalForm.portfolioUrl.trim() : undefined,
      });
      notify.success('Professional details updated');
      setEditOpen(false);
      fetchProfile();
    } catch (e) {
      notify.error(getErrorMessage(e) || 'Failed to update professional details');
    } finally {
      setSaving(false);
    }
  };

  const saveCurrentEditTab = async () => {
    if (editTab === 'personal') return savePersonal();
    if (editTab === 'academics') return saveAcademic();
    if (editTab === 'professional') return saveProfessional();
    return savePlacement();
  };

  if (loading) {
    return (
      <div className="max-w-[1200px] mx-auto space-y-6 animate-fadeIn">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-[150px] w-full rounded-[16px]" />
        <Skeleton className="h-[240px] w-full rounded-[16px]" />
        <Skeleton className="h-[320px] w-full rounded-[16px]" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-[1200px] mx-auto">
        <PageHeader title="My Profile" description="Manage your academic, professional and placement information." />
        <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft p-10 text-center animate-fadeIn">
          <span className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-danger-50 text-danger-500 mb-3">
            <AlertCircle size={22} />
          </span>
          <p className="text-[15px] text-neutral-500">{error}</p>
          <Button variant="secondary" className="mt-4" onClick={fetchProfile}>Retry</Button>
        </div>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="max-w-[1200px] mx-auto space-y-6">
        <PageHeader title="My Profile" description="Your account and placement profile." />
        {account && (
          <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft p-5 animate-fadeIn">
            <div className="flex items-center gap-4 sm:gap-5 min-w-0">
              <Avatar name={account.name} size="lg" />
              <div className="min-w-0">
                <h2 className="text-[20px] font-bold text-neutral-900">{account.name}</h2>
                <p className="text-[13px] text-neutral-500 mt-0.5 break-all">{account.email}</p>
                <span className="inline-block mt-2 px-2.5 py-0.5 rounded-full bg-primary-50 text-primary-600 text-xs font-medium">
                  {roleLabels[isPR ? 'PR' : 'STUDENT']}
                </span>
              </div>
            </div>
          </div>
        )}
        <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft p-10 text-center animate-fadeIn">
          <span className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-amber-50 text-amber-500 mb-3">
            <AlertCircle size={22} />
          </span>
          <p className="text-[15px] text-neutral-600">Student profile information is unavailable.</p>
          <Button variant="secondary" className="mt-4" onClick={fetchProfile}>Retry</Button>
        </div>
      </div>
    );
  }

  const missing = missingProfileItems(profile);

  return (
    <div className="max-w-[1200px] mx-auto space-y-6">
      <PageHeader title="My Profile" description="Your complete placement profile in one place." />

      {/* Hero header */}
      <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft p-5 animate-fadeIn">
        <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-5">
          <div className="flex items-center gap-4 sm:gap-5 min-w-0">
            <Avatar name={profile.userName} size="lg" />
            <div className="min-w-0">
              <h2 className="text-[20px] font-bold text-neutral-900">{profile.userName}</h2>
              <p className="text-[13px] text-neutral-500 mt-0.5 font-mono tracking-wide break-all">
                {profile.registerNumber} · {profile.departmentName}
              </p>
              <div className="flex flex-wrap items-center gap-2 mt-2">
                <span className="px-2.5 py-0.5 rounded-full bg-primary-50 text-primary-600 text-xs font-medium">{roleLabels[isPR ? 'PR' : 'STUDENT']}</span>
                <Badge variant={placementBadgeVariant(profile.placementStatus)} dot size="sm">
                  {formatPlacementStatus(profile.placementStatus)}
                </Badge>
                {!profile.placementInterested && (
                  <span className="px-2.5 py-0.5 rounded-full bg-neutral-100 text-neutral-600 text-xs font-medium">Not interested in placements</span>
                )}
              </div>
            </div>
          </div>
          <div className="shrink-0">
            {canEdit && (
              <Button variant="secondary" onClick={() => openEdit('personal')} className="w-full sm:w-auto">
                <Pencil size={14} /> Edit Profile
              </Button>
            )}
          </div>
        </div>
      </div>

      {missing.length > 0 && (
        <div className="flex items-start gap-3 rounded-[14px] border border-amber-200/70 bg-amber-50/70 px-4 py-3 animate-fadeIn">
          <Info size={16} className="text-amber-500 shrink-0 mt-0.5" />
          <div>
            <p className="text-[13.5px] font-semibold text-amber-800">Complete missing profile information</p>
            <p className="text-[12.5px] text-amber-700/80 mt-0.5">Missing: {missing.join(', ')}</p>
          </div>
        </div>
      )}

      {missing.length === 0 && (
        <div className="flex items-center gap-2 rounded-[14px] border border-success-200/70 bg-success-50/70 px-4 py-2.5 animate-fadeIn">
          <CheckCircle2 size={15} className="text-success-500" />
          <p className="text-[13px] font-medium text-success-700">Your profile looks complete.</p>
        </div>
      )}

      {/* Tab bar */}
      <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft animate-fadeIn">
        <div className="px-4 pt-3 sm:px-5">
          <ProfileTabs active={activeTab} onChange={setActiveTab} />
        </div>

        <div className="p-4 sm:p-5">
          {/* ── OVERVIEW ── */}
          {activeTab === 'overview' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5 animate-fadeIn">
              <SectionCard
                title="Personal Information"
                icon={<User size={17} />}
                action={canEdit ? <Button variant="ghost" size="sm" onClick={() => openEdit('personal')}><Pencil size={12} /> Edit</Button> : undefined}
              >
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
                  <Field label="Email"><TextValue value={profile.userEmail} /></Field>
                  <Field label="Register Number"><TextValue value={profile.registerNumber} /></Field>
                  <Field label="Department"><TextValue value={profile.departmentName} /></Field>
                  <Field label="Batch"><TextValue value={profile.batch} /></Field>
                  <Field label="Phone"><TextValue value={profile.phone} /></Field>
                  <Field label="Date of Birth"><TextValue value={formatDate(profile.dateOfBirth)} /></Field>
                </div>
              </SectionCard>

              <SectionCard
                title="Academic Snapshot"
                icon={<GraduationCap size={17} />}
                action={canEdit ? <Button variant="ghost" size="sm" onClick={() => openEdit('academics')}><Pencil size={12} /> Edit</Button> : undefined}
              >
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
                  <Field label="Current CGPA"><TextValue value={profile.cgpa != null ? String(profile.cgpa) : null} /></Field>
                  <Field label="Active Backlogs"><TextValue value={profile.activeBacklogs != null ? String(profile.activeBacklogs) : null} /></Field>
                  <Field label="History of Backlogs"><TextValue value={profile.historyOfBacklogs != null ? String(profile.historyOfBacklogs) : null} /></Field>
                </div>
              </SectionCard>

              <SectionCard
                title="Professional Links"
                icon={<Briefcase size={17} />}
                action={canEdit ? <Button variant="ghost" size="sm" onClick={() => openEdit('professional')}><Pencil size={12} /> Edit</Button> : undefined}
              >
                <div className="space-y-2.5">
                  <LinkCard label="Resume" url={profile.resumeUrl} />
                  <LinkCard label="GitHub" url={profile.githubUrl} />
                  <LinkCard label="LinkedIn" url={profile.linkedinUrl} />
                  <LinkCard label="Portfolio" url={profile.portfolioUrl} />
                </div>
              </SectionCard>

              <SectionCard
                title="Placement Status"
                icon={<BarChart3 size={17} />}
              >
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="text-[13.5px] font-medium text-neutral-500">Interest</span>
                    <Badge variant={profile.placementInterested ? 'success' : 'neutral'} size="sm">
                      {profile.placementInterested ? 'Interested' : 'Not interested'}
                    </Badge>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-[13.5px] font-medium text-neutral-500">Status</span>
                    <Badge variant={placementBadgeVariant(profile.placementStatus)} dot size="sm">
                      {formatPlacementStatus(profile.placementStatus)}
                    </Badge>
                  </div>
                  {profile.placedCompanyName && (
                    <div className="flex items-center justify-between">
                      <span className="text-[13.5px] font-medium text-neutral-500">Placed at</span>
                      <span className="text-[14px] font-semibold text-neutral-800 text-right">{profile.placedCompanyName}</span>
                    </div>
                  )}
                  {profile.packageLpa != null && (
                    <div className="flex items-center justify-between">
                      <span className="text-[13.5px] font-medium text-neutral-500">Package</span>
                      <span className="text-[14px] font-semibold text-neutral-800">{formatPackage(profile.packageLpa)}</span>
                    </div>
                  )}
                </div>
              </SectionCard>
            </div>
          )}

          {/* ── ACADEMICS ── */}
          {activeTab === 'academics' && (
            <div className="space-y-4 sm:space-y-5 animate-fadeIn">
              <div className="flex items-center justify-between">
                <h3 className="text-[16px] font-semibold text-neutral-900">Academic Details</h3>
                {canEdit && <Button variant="ghost" size="sm" onClick={() => openEdit('academics')}><Pencil size={13} /> Edit</Button>}
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 sm:gap-5">
                <SectionCard title="Performance" icon={<GraduationCap size={17} />}>
                  <div className="flex items-baseline gap-2">
                    <span className="text-[30px] font-bold text-neutral-900 tracking-tight">
                      {profile.cgpa != null ? profile.cgpa : '—'}
                    </span>
                    {profile.cgpa != null && <span className="text-[13px] text-neutral-500 font-medium">/ 10 CGPA</span>}
                  </div>
                </SectionCard>
                <SectionCard title="Backlogs" icon={<BarChart3 size={17} />}>
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-[13.5px] font-medium text-neutral-500">Active</span>
                      <span className="text-[15px] font-semibold text-neutral-800">{profile.activeBacklogs ?? 0}</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-[13.5px] font-medium text-neutral-500">History</span>
                      <span className="text-[15px] font-semibold text-neutral-800">{profile.historyOfBacklogs ?? 0}</span>
                    </div>
                  </div>
                </SectionCard>
                <SectionCard title="Schooling" icon={<Building2 size={17} />}>
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-[13.5px] font-medium text-neutral-500">10th</span>
                      <TextValue value={profile.tenthPercentage != null ? `${profile.tenthPercentage}%` : null} />
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-[13.5px] font-medium text-neutral-500">12th</span>
                      <TextValue value={profile.twelfthPercentage != null ? `${profile.twelfthPercentage}%` : null} />
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-[13.5px] font-medium text-neutral-500">Diploma</span>
                      <TextValue value={profile.diplomaPercentage != null ? `${profile.diplomaPercentage}%` : null} />
                    </div>
                  </div>
                </SectionCard>
              </div>
            </div>
          )}

          {/* ── PROFESSIONAL ── */}
          {activeTab === 'professional' && (
            <div className="space-y-4 sm:space-y-5 animate-fadeIn">
              <div className="flex items-center justify-between">
                <h3 className="text-[16px] font-semibold text-neutral-900">Professional Details</h3>
                {canEdit && <Button variant="ghost" size="sm" onClick={() => openEdit('professional')}><Pencil size={13} /> Edit</Button>}
              </div>
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-5">
                <SectionCard title="Skills" icon={<CheckCircle2 size={17} />} className="lg:col-span-2">
                  <p className="text-[14.5px] font-medium text-neutral-800 whitespace-pre-wrap leading-relaxed">
                    {profile.skills || <span className="text-neutral-300 font-normal">Not provided</span>}
                  </p>
                </SectionCard>
                <SectionCard title="Certifications" icon={<CheckCircle2 size={17} />} className="lg:col-span-2">
                  <p className="text-[14.5px] font-medium text-neutral-800 whitespace-pre-wrap leading-relaxed">
                    {profile.certifications || <span className="text-neutral-300 font-normal">Not provided</span>}
                  </p>
                </SectionCard>
                <SectionCard title="Projects" icon={<Briefcase size={17} />} className="lg:col-span-2">
                  <p className="text-[14.5px] font-medium text-neutral-800 whitespace-pre-wrap leading-relaxed">
                    {profile.projects || <span className="text-neutral-300 font-normal">Not provided</span>}
                  </p>
                </SectionCard>
                <SectionCard title="Professional Links" icon={<ExternalLink size={17} />} className="lg:col-span-2">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    <LinkCard label="Resume" url={profile.resumeUrl} />
                    <LinkCard label="GitHub" url={profile.githubUrl} />
                    <LinkCard label="LinkedIn" url={profile.linkedinUrl} />
                    <LinkCard label="Portfolio" url={profile.portfolioUrl} />
                  </div>
                </SectionCard>
              </div>
            </div>
          )}

          {/* ── PLACEMENT ── */}
          {activeTab === 'placement' && (
            <div className="space-y-4 sm:space-y-5 animate-fadeIn">
              <div className="flex items-center justify-between">
                <h3 className="text-[16px] font-semibold text-neutral-900">Placement</h3>
                {canEdit && <Button variant="ghost" size="sm" onClick={() => openEdit('placement')}><Pencil size={13} /> Edit</Button>}
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5">
                <SectionCard title="Placement Interest" icon={<CheckCircle2 size={17} />}>
                  <div className="flex items-center gap-2.5">
                    <Badge variant={profile.placementInterested ? 'success' : 'neutral'} dot>
                      {profile.placementInterested ? 'Interested' : 'Not Interested'}
                    </Badge>
                  </div>
                </SectionCard>
                <SectionCard title="Placement Status" icon={<BarChart3 size={17} />}>
                  <Badge variant={placementBadgeVariant(profile.placementStatus)} dot>
                    {formatPlacementStatus(profile.placementStatus)}
                  </Badge>
                </SectionCard>
                {profile.placedCompanyName && (
                  <SectionCard title="Placed Company" icon={<Building2 size={17} />}>
                    <p className="text-[15px] font-semibold text-neutral-800">{profile.placedCompanyName}</p>
                  </SectionCard>
                )}
                {profile.packageLpa != null && (
                  <SectionCard title="Package" icon={<Briefcase size={17} />}>
                    <p className="text-[15px] font-semibold text-neutral-800">{formatPackage(profile.packageLpa)}</p>
                  </SectionCard>
                )}
                {profile.interviewsAttended != null && profile.interviewsAttended > 0 && (
                  <SectionCard title="Interviews Attended" icon={<GraduationCap size={17} />}>
                    <p className="text-[15px] font-semibold text-neutral-800">{profile.interviewsAttended}</p>
                  </SectionCard>
                )}
              </div>
              <p className="text-[13px] text-neutral-500">
                Placement status, placed company and package details are managed by the Placement Office and are read-only here.
              </p>
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

      {/* Edit Profile modal */}
      <Modal
        isOpen={editOpen}
        onClose={() => setEditOpen(false)}
        title="Edit Profile"
        description="Update only the fields you own. Administrative fields are read-only."
        size="xl"
        actions={<>
          <Button variant="secondary" onClick={() => setEditOpen(false)} disabled={saving}>Cancel</Button>
          <Button onClick={saveCurrentEditTab} loading={saving}>Save</Button>
        </>}
      >
        <div className="space-y-5">
          <div className="overflow-x-auto -mx-1 px-1">
            <div className="flex items-center gap-0.5 border-b border-neutral-200/60 w-max min-w-full">
              {editTabItems.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setEditTab(tab.key)}
                  className={`px-3.5 py-2 -mb-px border-b-2 text-[13.5px] font-medium whitespace-nowrap transition-all duration-150 ${
                    editTab === tab.key
                      ? 'border-primary-500 text-primary-600'
                      : 'border-transparent text-neutral-500 hover:text-neutral-700'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          {editTab === 'personal' && (
            <div className="space-y-4 animate-fadeIn">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input label="Phone" placeholder="e.g. +91 98765 43210" value={personalForm.phone} onChange={(e) => setPersonalForm({ ...personalForm, phone: e.target.value })} icon={<Phone size={16} />} />
                <Input label="Date of Birth" type="date" value={personalForm.dateOfBirth} onChange={(e) => setPersonalForm({ ...personalForm, dateOfBirth: e.target.value })} icon={<Calendar size={16} />} />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input label="Batch" placeholder="e.g. 2021-2025" value={personalForm.batch} onChange={(e) => setPersonalForm({ ...personalForm, batch: e.target.value })} />
                <Input label="Section" placeholder="e.g. A" value={personalForm.section} onChange={(e) => setPersonalForm({ ...personalForm, section: e.target.value })} />
              </div>
              <p className="text-[12.5px] text-neutral-500 flex items-center gap-1.5">
                <Info size={13} /> Your register number, email and department are assigned by the college and cannot be changed here.
              </p>
            </div>
          )}

          {editTab === 'academics' && (
            <div className="space-y-4 animate-fadeIn">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <Input label="10th Percentage" type="number" min="0" max="100" step="0.01" placeholder="0-100" value={academicForm.tenthPercentage} onChange={(e) => setAcademicForm({ ...academicForm, tenthPercentage: e.target.value })} />
                <Input label="12th Percentage" type="number" min="0" max="100" step="0.01" placeholder="0-100" value={academicForm.twelfthPercentage} onChange={(e) => setAcademicForm({ ...academicForm, twelfthPercentage: e.target.value })} />
                <Input label="Diploma Percentage" type="number" min="0" max="100" step="0.01" placeholder="0-100" value={academicForm.diplomaPercentage} onChange={(e) => setAcademicForm({ ...academicForm, diplomaPercentage: e.target.value })} />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <Input label="CGPA" type="number" min="0" max="10" step="0.01" placeholder="0-10" value={academicForm.cgpa} onChange={(e) => setAcademicForm({ ...academicForm, cgpa: e.target.value })} />
                <Input label="Active Backlogs" type="number" min="0" value={academicForm.activeBacklogs} onChange={(e) => setAcademicForm({ ...academicForm, activeBacklogs: e.target.value })} />
                <Input label="History of Backlogs" type="number" min="0" value={academicForm.historyOfBacklogs} onChange={(e) => setAcademicForm({ ...academicForm, historyOfBacklogs: e.target.value })} />
              </div>
            </div>
          )}

          {editTab === 'professional' && (
            <div className="space-y-4 animate-fadeIn">
              <Textarea label="Skills" rows={2} placeholder="e.g. JavaScript, React, Python, Node.js" value={professionalForm.skills} onChange={(e) => setProfessionalForm({ ...professionalForm, skills: e.target.value })} />
              <Textarea label="Certifications" rows={2} placeholder="e.g. AWS Certified Developer, Google Cloud Associate" value={professionalForm.certifications} onChange={(e) => setProfessionalForm({ ...professionalForm, certifications: e.target.value })} />
              <Textarea label="Projects" rows={3} placeholder="Describe your notable projects..." value={professionalForm.projects} onChange={(e) => setProfessionalForm({ ...professionalForm, projects: e.target.value })} />
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input label="Resume URL" type="url" placeholder="https://…" value={professionalForm.resumeUrl} onChange={(e) => setProfessionalForm({ ...professionalForm, resumeUrl: e.target.value })} />
                <Input label="GitHub URL" type="url" placeholder="https://github.com/…" value={professionalForm.githubUrl} onChange={(e) => setProfessionalForm({ ...professionalForm, githubUrl: e.target.value })} />
                <Input label="LinkedIn URL" type="url" placeholder="https://linkedin.com/in/…" value={professionalForm.linkedinUrl} onChange={(e) => setProfessionalForm({ ...professionalForm, linkedinUrl: e.target.value })} />
                <Input label="Portfolio URL" type="url" placeholder="https://…" value={professionalForm.portfolioUrl} onChange={(e) => setProfessionalForm({ ...professionalForm, portfolioUrl: e.target.value })} />
              </div>
            </div>
          )}

          {editTab === 'placement' && (
            <div className="space-y-4 animate-fadeIn">
              <div className="flex items-start gap-3 p-4 rounded-[12px] bg-neutral-50 border border-neutral-100">
                <input
                  id="placementInterested"
                  type="checkbox"
                  checked={interested}
                  onChange={(e) => setInterested(e.target.checked)}
                  className="mt-0.5 rounded border-neutral-300 text-primary-600 focus:ring-primary-500"
                />
                <div>
                  <label htmlFor="placementInterested" className="text-[14px] font-medium text-neutral-800">I am interested in campus placements</label>
                  <p className="text-[12.5px] text-neutral-500 mt-0.5">You can change this preference any time.</p>
                </div>
              </div>
              <div className="flex items-start gap-2 rounded-[10px] bg-primary-50/60 border border-primary-100/60 px-3.5 py-3">
                <Info size={14} className="text-primary-500 mt-0.5 shrink-0" />
                <p className="text-[12.5px] text-primary-700/90">
                  Placement status, placed company, role and package are recorded by the Placement Office and are read-only for students.
                </p>
              </div>
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
}