import { useState, useEffect, useCallback } from 'react';
import { studentApi } from '../../api/api';
import type { StudentProfile } from '../../types';
import {
  Button,
  Input,
  Textarea,
  Badge,
  Modal,
  Skeleton,
  PageHeader,
  notify,
} from '../../components/ui';
import {
  Pencil,
  User,
  Briefcase,
  GraduationCap,
  ExternalLink,
  BarChart3,
  BookOpen,
  FolderOpen,
} from 'lucide-react';

interface ProfileForm {
  phone: string;
  dateOfBirth: string;
  batch: string;
  section: string;
  placementInterested: boolean;
}

interface AcademicForm {
  tenthPercentage: string;
  twelfthPercentage: string;
  diplomaPercentage: string;
  cgpa: string;
  activeBacklogs: string;
  historyOfBacklogs: string;
}

interface ProfessionalForm {
  skills: string;
  certifications: string;
  projects: string;
}

interface LinksForm {
  resumeUrl: string;
  githubUrl: string;
  linkedinUrl: string;
  portfolioUrl: string;
}

const defaultProfileForm: ProfileForm = {
  phone: '',
  dateOfBirth: '',
  batch: '',
  section: '',
  placementInterested: false,
};

const defaultAcademicForm: AcademicForm = {
  tenthPercentage: '',
  twelfthPercentage: '',
  diplomaPercentage: '',
  cgpa: '',
  activeBacklogs: '',
  historyOfBacklogs: '',
};

const defaultProfessionalForm: ProfessionalForm = {
  skills: '',
  certifications: '',
  projects: '',
};

const defaultLinksForm: LinksForm = {
  resumeUrl: '',
  githubUrl: '',
  linkedinUrl: '',
  portfolioUrl: '',
};

const statusVariant = (s: string | null): 'success' | 'warning' | 'info' | 'neutral' => {
  switch (s) {
    case 'PLACED': return 'success';
    case 'IN_PROCESS': return 'warning';
    case 'ELIGIBLE': return 'info';
    default: return 'neutral';
  }
};

type Tab = 'overview' | 'academics' | 'professional' | 'placement';

const tabs: { key: Tab; label: string; icon: React.ElementType }[] = [
  { key: 'overview', label: 'Overview', icon: User },
  { key: 'academics', label: 'Academics', icon: GraduationCap },
  { key: 'professional', label: 'Professional', icon: Briefcase },
  { key: 'placement', label: 'Placement', icon: BarChart3 },
];

export default function ProfilePage() {
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingSection, setEditingSection] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState<Tab>('overview');

  const [profileForm, setProfileForm] = useState<ProfileForm>(defaultProfileForm);
  const [academicForm, setAcademicForm] = useState<AcademicForm>(defaultAcademicForm);
  const [professionalForm, setProfessionalForm] = useState<ProfessionalForm>(defaultProfessionalForm);
  const [linksForm, setLinksForm] = useState<LinksForm>(defaultLinksForm);

  const fetchProfile = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await studentApi.getMyProfile();
      const data: StudentProfile = res.data?.data ?? res.data;
      setProfile(data);
    } catch {
      setError('Failed to load profile');
      notify.error('Failed to load profile');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  const openProfileEdit = () => {
    if (!profile) return;
    setProfileForm({
      phone: profile.phone || '',
      dateOfBirth: profile.dateOfBirth || '',
      batch: profile.batch || '',
      section: profile.section || '',
      placementInterested: profile.placementInterested ?? false,
    });
    setEditingSection('profile');
  };

  const openAcademicEdit = () => {
    if (!profile) return;
    setAcademicForm({
      tenthPercentage: profile.tenthPercentage?.toString() || '',
      twelfthPercentage: profile.twelfthPercentage?.toString() || '',
      diplomaPercentage: profile.diplomaPercentage?.toString() || '',
      cgpa: profile.cgpa?.toString() || '',
      activeBacklogs: profile.activeBacklogs?.toString() || '',
      historyOfBacklogs: profile.historyOfBacklogs?.toString() || '',
    });
    setEditingSection('academic');
  };

  const openProfessionalEdit = () => {
    if (!profile) return;
    setProfessionalForm({
      skills: profile.skills || '',
      certifications: profile.certifications || '',
      projects: profile.projects || '',
    });
    setEditingSection('professional');
  };

  const openLinksEdit = () => {
    if (!profile) return;
    setLinksForm({
      resumeUrl: profile.resumeUrl || '',
      githubUrl: profile.githubUrl || '',
      linkedinUrl: profile.linkedinUrl || '',
      portfolioUrl: profile.portfolioUrl || '',
    });
    setEditingSection('links');
  };

  const saveProfile = async () => {
    if (!profile) return;
    setSaving(true);
    try {
      await studentApi.updateProfile(profile.id, {
        phone: profileForm.phone || undefined,
        dateOfBirth: profileForm.dateOfBirth || undefined,
        batch: profileForm.batch || undefined,
        section: profileForm.section || undefined,
        placementInterested: profileForm.placementInterested,
      });
      notify.success('Profile updated successfully');
      setEditingSection(null);
      fetchProfile();
    } catch {
      notify.error('Failed to update profile');
    } finally {
      setSaving(false);
    }
  };

  const saveAcademic = async () => {
    if (!profile) return;
    setSaving(true);
    try {
      await studentApi.updateAcademic(profile.id, {
        tenthPercentage: academicForm.tenthPercentage ? Number(academicForm.tenthPercentage) : undefined,
        twelfthPercentage: academicForm.twelfthPercentage ? Number(academicForm.twelfthPercentage) : undefined,
        diplomaPercentage: academicForm.diplomaPercentage ? Number(academicForm.diplomaPercentage) : undefined,
        cgpa: academicForm.cgpa ? Number(academicForm.cgpa) : undefined,
        activeBacklogs: academicForm.activeBacklogs !== '' ? Number(academicForm.activeBacklogs) : undefined,
        historyOfBacklogs: academicForm.historyOfBacklogs !== '' ? Number(academicForm.historyOfBacklogs) : undefined,
      });
      notify.success('Academic details updated successfully');
      setEditingSection(null);
      fetchProfile();
    } catch {
      notify.error('Failed to update academic details');
    } finally {
      setSaving(false);
    }
  };

  const saveProfessional = async () => {
    if (!profile) return;
    setSaving(true);
    try {
      await studentApi.updateProfessional(profile.id, {
        skills: professionalForm.skills || undefined,
        certifications: professionalForm.certifications || undefined,
        projects: professionalForm.projects || undefined,
      });
      notify.success('Professional details updated successfully');
      setEditingSection(null);
      fetchProfile();
    } catch {
      notify.error('Failed to update professional details');
    } finally {
      setSaving(false);
    }
  };

  const saveLinks = async () => {
    if (!profile) return;
    setSaving(true);
    try {
      await studentApi.updateProfile(profile.id, {
        resumeUrl: linksForm.resumeUrl || undefined,
        githubUrl: linksForm.githubUrl || undefined,
        linkedinUrl: linksForm.linkedinUrl || undefined,
        portfolioUrl: linksForm.portfolioUrl || undefined,
      });
      notify.success('Links updated successfully');
      setEditingSection(null);
      fetchProfile();
    } catch {
      notify.error('Failed to update links');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="p-8">
        <Skeleton className="h-8 w-48 mb-8" />
        <div className="space-y-6">
          <Skeleton.Card />
          <Skeleton.Card />
          <Skeleton.Card />
          <Skeleton.Card />
        </div>
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="p-8">
        <PageHeader title="My Profile" description="View and manage your profile information." />
        <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card">
          <div className="py-12 text-center">
            <p className="text-[15px] text-neutral-500">{error || 'Profile not found'}</p>
            <Button variant="secondary" className="mt-4" onClick={fetchProfile}>
              Retry
            </Button>
          </div>
        </div>
      </div>
    );
  }

  const overviewFields = [
    { label: 'Phone', value: profile.phone || '-' },
    { label: 'Date of Birth', value: profile.dateOfBirth ? new Date(profile.dateOfBirth).toLocaleDateString() : '-' },
    { label: 'Batch', value: profile.batch || '-' },
    { label: 'Section', value: profile.section || '-' },
  ];

  const academicFields = [
    { label: '10th Percentage', value: profile.tenthPercentage != null ? `${profile.tenthPercentage}%` : '-' },
    { label: '12th Percentage', value: profile.twelfthPercentage != null ? `${profile.twelfthPercentage}%` : '-' },
    { label: 'Diploma Percentage', value: profile.diplomaPercentage != null ? `${profile.diplomaPercentage}%` : '-' },
    { label: 'CGPA', value: profile.cgpa != null ? String(profile.cgpa) : '-' },
    { label: 'Active Backlogs', value: String(profile.activeBacklogs ?? 0) },
    { label: 'History of Backlogs', value: String(profile.historyOfBacklogs ?? 0) },
  ];

  const links = [
    { label: 'Resume', url: profile.resumeUrl },
    { label: 'GitHub', url: profile.githubUrl },
    { label: 'LinkedIn', url: profile.linkedinUrl },
    { label: 'Portfolio', url: profile.portfolioUrl },
  ];

  return (
    <div className="max-w-[1200px] mx-auto space-y-6">
      <PageHeader title="My Profile" description="View and manage your profile information." />

      {/* Profile Header Card */}
      <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-6 hover:shadow-raised transition-shadow duration-200">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-full bg-brand-navy flex items-center justify-center shrink-0 ring-1 ring-white/10">
              <User size={28} className="text-white" />
            </div>
            <div>
              <h2 className="text-[17px] font-semibold text-neutral-900">{profile.userName}</h2>
              <p className="text-[15px] text-neutral-500 mt-0.5">{profile.registerNumber}</p>
              <div className="flex items-center gap-2 mt-1.5">
                <span className="text-[15px] text-neutral-600">{profile.departmentName}</span>
                {profile.batch && (
                  <>
                    <span className="text-neutral-300">&middot;</span>
                    <span className="text-[15px] text-neutral-600">{profile.batch}</span>
                  </>
                )}
                {profile.section && (
                  <>
                    <span className="text-neutral-300">&middot;</span>
                    <span className="text-[15px] text-neutral-600">Sec {profile.section}</span>
                  </>
                )}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2.5">
            <Badge variant={statusVariant(profile.placementStatus)} dot>
              {profile.placementStatus || 'NOT_PLACED'}
            </Badge>
            <Button variant="secondary" size="sm" onClick={openProfileEdit}>
              <Pencil size={13} /> Edit
            </Button>
          </div>
        </div>
      </div>

      {/* Stats Strip */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-4 flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-primary-50 flex items-center justify-center text-primary-600">
            <BarChart3 size={16} />
          </div>
          <div>
            <p className="text-[13px] text-neutral-500 uppercase tracking-wide">CGPA</p>
            <p className="text-[17px] font-semibold text-neutral-900">{profile.cgpa ?? '-'}</p>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-4 flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-primary-50 flex items-center justify-center text-primary-600">
            <BookOpen size={16} />
          </div>
          <div>
            <p className="text-[13px] text-neutral-500 uppercase tracking-wide">Backlogs</p>
            <p className="text-[17px] font-semibold text-neutral-900">{profile.activeBacklogs ?? 0}</p>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-4 flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-primary-50 flex items-center justify-center text-primary-600">
            <FolderOpen size={16} />
          </div>
          <div>
            <p className="text-[13px] text-neutral-500 uppercase tracking-wide">Interested</p>
            <Badge variant={profile.placementInterested ? 'success' : 'neutral'} dot size="sm">
              {profile.placementInterested ? 'Yes' : 'No'}
            </Badge>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-4 flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-brand-navy flex items-center justify-center text-white">
            <Briefcase size={16} />
          </div>
          <div>
            <p className="text-[13px] text-neutral-500 uppercase tracking-wide">Status</p>
            <Badge variant={statusVariant(profile.placementStatus)} dot size="sm">
              {profile.placementStatus || 'NOT_PLACED'}
            </Badge>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="bg-white rounded-xl border border-neutral-200/70 shadow-card">
        <div className="border-b border-neutral-200">
          <nav className="flex gap-0 px-2 -mb-px">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.key;
              return (
                <button
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  className={`
                    flex items-center gap-2 px-4 py-3 text-[15px] font-medium transition-colors -mb-px border-b-2
                    ${isActive
                      ? 'text-primary-700 border-primary-500'
                      : 'text-neutral-500 hover:text-neutral-700 border-transparent'
                    }
                  `}
                >
                  <Icon size={15} className={isActive ? 'text-primary-600' : 'text-neutral-400'} />
                  {tab.label}
                </button>
              );
            })}
          </nav>
        </div>

        <div className="p-6">
          {/* Overview Tab */}
          {activeTab === 'overview' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <h3 className="text-[15px] font-semibold text-neutral-900">Personal Information</h3>
                <Button variant="ghost" size="sm" onClick={openProfileEdit}>
                  <Pencil size={13} /> Edit
                </Button>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-5">
                {overviewFields.map((field) => (
                  <div key={field.label}>
                    <p className="text-[15px] text-neutral-500 uppercase tracking-wide mb-1">{field.label}</p>
                    <p className="text-[15px] font-medium text-neutral-900">{field.value}</p>
                  </div>
                ))}
                <div>
                  <p className="text-[15px] text-neutral-500 uppercase tracking-wide mb-1">Placement Interested</p>
                  <Badge variant={profile.placementInterested ? 'success' : 'neutral'} dot size="sm">
                    {profile.placementInterested ? 'Yes' : 'No'}
                  </Badge>
                </div>
              </div>
            </div>
          )}

          {/* Academics Tab */}
          {activeTab === 'academics' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <h3 className="text-[15px] font-semibold text-neutral-900">Academic Details</h3>
                <Button variant="ghost" size="sm" onClick={openAcademicEdit}>
                  <Pencil size={13} /> Edit
                </Button>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-5">
                {academicFields.map((field) => (
                  <div key={field.label}>
                    <p className="text-[15px] text-neutral-500 uppercase tracking-wide mb-1">{field.label}</p>
                    <p className="text-[15px] font-medium text-neutral-900">{field.value}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Professional Tab */}
          {activeTab === 'professional' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <h3 className="text-[15px] font-semibold text-neutral-900">Professional Details</h3>
                <Button variant="ghost" size="sm" onClick={openProfessionalEdit}>
                  <Pencil size={13} /> Edit
                </Button>
              </div>
              <div className="space-y-5">
                {[
                  { label: 'Skills', value: profile.skills },
                  { label: 'Certifications', value: profile.certifications },
                  { label: 'Projects', value: profile.projects },
                ].map((field) => (
                  <div key={field.label}>
                    <p className="text-[15px] text-neutral-500 uppercase tracking-wide mb-1">{field.label}</p>
                    <p className="text-[15px] font-medium text-neutral-900 whitespace-pre-wrap leading-relaxed">
                      {field.value || '-'}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Placement Tab */}
          {activeTab === 'placement' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <h3 className="text-[15px] font-semibold text-neutral-900">Links & Placement</h3>
                <Button variant="ghost" size="sm" onClick={openLinksEdit}>
                  <Pencil size={13} /> Edit
                </Button>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {links.map((link) => (
                  <div key={link.label} className="flex items-center justify-between p-3.5 rounded-lg border border-neutral-200/80 bg-neutral-50/50">
                    <div>
                      <p className="text-[15px] text-neutral-500 uppercase tracking-wide mb-0.5">{link.label}</p>
                      {link.url ? (
                        <a
                          href={link.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-[15px] text-primary-600 hover:underline flex items-center gap-1"
                        >
                          {link.url.length > 40 ? link.url.slice(0, 40) + '...' : link.url}
                          <ExternalLink size={11} />
                        </a>
                      ) : (
                        <p className="text-[15px] text-neutral-400">Not provided</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Edit Profile Modal */}
      <Modal
        isOpen={editingSection === 'profile'}
        onClose={() => setEditingSection(null)}
        title="Edit Personal Information"
        description="Update your personal details."
        actions={
          <>
            <Button variant="secondary" onClick={() => setEditingSection(null)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={saveProfile} loading={saving}>
              Save Changes
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input
            label="Phone"
            placeholder="e.g. +91 98765 43210"
            value={profileForm.phone}
            onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })}
          />
          <Input
            label="Date of Birth"
            type="date"
            value={profileForm.dateOfBirth}
            onChange={(e) => setProfileForm({ ...profileForm, dateOfBirth: e.target.value })}
          />
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Batch"
              placeholder="e.g. 2021-2025"
              value={profileForm.batch}
              onChange={(e) => setProfileForm({ ...profileForm, batch: e.target.value })}
            />
            <Input
              label="Section"
              placeholder="e.g. A"
              value={profileForm.section}
              onChange={(e) => setProfileForm({ ...profileForm, section: e.target.value })}
            />
          </div>
          <div className="flex items-center gap-3 p-3 bg-neutral-50 rounded-lg">
            <input
              type="checkbox"
              id="placementInterested"
              checked={profileForm.placementInterested}
              onChange={(e) =>
                setProfileForm({ ...profileForm, placementInterested: e.target.checked })
              }
              className="rounded border-neutral-300 text-primary-600 focus:ring-primary-500"
            />
            <label htmlFor="placementInterested" className="text-[15px] text-neutral-700">
              I am interested in campus placements
            </label>
          </div>
        </div>
      </Modal>

      {/* Edit Academic Modal */}
      <Modal
        isOpen={editingSection === 'academic'}
        onClose={() => setEditingSection(null)}
        title="Edit Academic Details"
        description="Update your academic records."
        actions={
          <>
            <Button variant="secondary" onClick={() => setEditingSection(null)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={saveAcademic} loading={saving}>
              Save Changes
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="10th Percentage"
              type="number"
              min="0"
              max="100"
              step="0.01"
              placeholder="0-100"
              value={academicForm.tenthPercentage}
              onChange={(e) =>
                setAcademicForm({ ...academicForm, tenthPercentage: e.target.value })
              }
            />
            <Input
              label="12th Percentage"
              type="number"
              min="0"
              max="100"
              step="0.01"
              placeholder="0-100"
              value={academicForm.twelfthPercentage}
              onChange={(e) =>
                setAcademicForm({ ...academicForm, twelfthPercentage: e.target.value })
              }
            />
          </div>
          <Input
            label="Diploma Percentage"
            type="number"
            min="0"
            max="100"
            step="0.01"
            placeholder="0-100 (if applicable)"
            value={academicForm.diplomaPercentage}
            onChange={(e) =>
              setAcademicForm({ ...academicForm, diplomaPercentage: e.target.value })
            }
          />
          <Input
            label="CGPA"
            type="number"
            min="0"
            max="10"
            step="0.01"
            placeholder="0-10"
            value={academicForm.cgpa}
            onChange={(e) => setAcademicForm({ ...academicForm, cgpa: e.target.value })}
          />
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Active Backlogs"
              type="number"
              min="0"
              value={academicForm.activeBacklogs}
              onChange={(e) =>
                setAcademicForm({ ...academicForm, activeBacklogs: e.target.value })
              }
            />
            <Input
              label="History of Backlogs"
              type="number"
              min="0"
              value={academicForm.historyOfBacklogs}
              onChange={(e) =>
                setAcademicForm({ ...academicForm, historyOfBacklogs: e.target.value })
              }
            />
          </div>
        </div>
      </Modal>

      {/* Edit Professional Modal */}
      <Modal
        isOpen={editingSection === 'professional'}
        onClose={() => setEditingSection(null)}
        title="Edit Professional Details"
        description="Update your skills, certifications, and projects."
        size="lg"
        actions={
          <>
            <Button variant="secondary" onClick={() => setEditingSection(null)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={saveProfessional} loading={saving}>
              Save Changes
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Textarea
            label="Skills"
            rows={3}
            placeholder="e.g. JavaScript, React, Python, Node.js"
            value={professionalForm.skills}
            onChange={(e) =>
              setProfessionalForm({ ...professionalForm, skills: e.target.value })
            }
          />
          <Textarea
            label="Certifications"
            rows={3}
            placeholder="e.g. AWS Certified Developer, Google Cloud Associate"
            value={professionalForm.certifications}
            onChange={(e) =>
              setProfessionalForm({ ...professionalForm, certifications: e.target.value })
            }
          />
          <Textarea
            label="Projects"
            rows={4}
            placeholder="Describe your notable projects..."
            value={professionalForm.projects}
            onChange={(e) =>
              setProfessionalForm({ ...professionalForm, projects: e.target.value })
            }
          />
        </div>
      </Modal>

      {/* Edit Links Modal */}
      <Modal
        isOpen={editingSection === 'links'}
        onClose={() => setEditingSection(null)}
        title="Edit Links"
        description="Update your professional links."
        actions={
          <>
            <Button variant="secondary" onClick={() => setEditingSection(null)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={saveLinks} loading={saving}>
              Save Changes
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input
            label="Resume URL"
            type="url"
            placeholder="https://drive.google.com/..."
            value={linksForm.resumeUrl}
            onChange={(e) => setLinksForm({ ...linksForm, resumeUrl: e.target.value })}
          />
          <Input
            label="GitHub URL"
            type="url"
            placeholder="https://github.com/..."
            value={linksForm.githubUrl}
            onChange={(e) => setLinksForm({ ...linksForm, githubUrl: e.target.value })}
          />
          <Input
            label="LinkedIn URL"
            type="url"
            placeholder="https://linkedin.com/in/..."
            value={linksForm.linkedinUrl}
            onChange={(e) => setLinksForm({ ...linksForm, linkedinUrl: e.target.value })}
          />
          <Input
            label="Portfolio URL"
            type="url"
            placeholder="https://..."
            value={linksForm.portfolioUrl}
            onChange={(e) => setLinksForm({ ...linksForm, portfolioUrl: e.target.value })}
          />
        </div>
      </Modal>
    </div>
  );
}
