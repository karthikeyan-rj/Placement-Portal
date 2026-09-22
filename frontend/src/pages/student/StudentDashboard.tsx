import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { studentApi, studentInterviewApi } from '../../api/api';
import { getErrorMessage } from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import { usePaginatedData } from '../../hooks/useApi';
import type { PlacementDrive, StudentInterview, StudentProfile } from '../../types';
import { AlertCircle, ArrowRight, Briefcase, CalendarClock, ClipboardList, GraduationCap } from 'lucide-react';
import { PageContainer } from '../../components/ui';
import {
  DashboardEmpty,
  DashboardError,
  DashboardRowsSkeleton,
  DashboardSection,
  DriveRow,
  InterviewRow,
  MetricCard,
  MetricsSkeleton,
  SectionAction,
  WelcomeHeader,
} from '../../components/dashboard';

const PROFILE_FIELDS: Array<[keyof StudentProfile, string]> = [
  ['phone', 'Contact number'],
  ['cgpa', 'CGPA'],
  ['tenthPercentage', '10th percentage'],
  ['twelfthPercentage', '12th percentage'],
  ['skills', 'Skills'],
  ['placementInterested', 'Placement interest'],
];

function missingProfileFields(profile: StudentProfile | null): string[] {
  if (!profile) return [];
  return PROFILE_FIELDS.filter(([key]) => {
    const value = profile[key];
    return value == null || value === '';
  }).map(([, label]) => label);
}

export default function StudentDashboard() {
  const { user } = useAuth();
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);

  const [interviews, setInterviews] = useState<StudentInterview[]>([]);
  const [interviewsTotal, setInterviewsTotal] = useState(0);
  const [interviewsLoading, setInterviewsLoading] = useState(true);
  const [interviewsError, setInterviewsError] = useState<string | null>(null);

  const [retryKey, setRetryKey] = useState(0);

  const openDrives = usePaginatedData<PlacementDrive>({
    url: '/placement-drives',
    params: { status: 'REGISTRATION_OPEN', size: 5 },
    cacheTtl: 10_000,
  });
  const upcomingDrives = usePaginatedData<PlacementDrive>({
    url: '/placement-drives',
    params: { status: 'UPCOMING', size: 5 },
    cacheTtl: 10_000,
  });

  useEffect(() => {
    let cancelled = false;
    setProfileLoading(true);
    setProfileError(null);
    studentApi
      .getMyProfile()
      .then((res) => {
        if (!cancelled) setProfile(res.data.data);
      })
      .catch((err) => {
        if (!cancelled) setProfileError(getErrorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setProfileLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [retryKey]);

  useEffect(() => {
    let cancelled = false;
    setInterviewsLoading(true);
    setInterviewsError(null);
    studentInterviewApi
      .getMine()
      .then((res) => {
        if (cancelled) return;
        const payload = res.data.data;
        if (Array.isArray(payload)) {
          setInterviews(payload.slice(0, 5));
          setInterviewsTotal(payload.length);
        } else {
          const list = payload?.content ?? [];
          setInterviews(list.slice(0, 5));
          setInterviewsTotal(payload?.totalElements ?? list.length ?? 0);
        }
      })
      .catch((err) => {
        if (!cancelled) setInterviewsError(getErrorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setInterviewsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [retryKey]);

  const missing = missingProfileFields(profile);
  const metricsReady = !profileLoading && !interviewsLoading;
  const cgpaValue =
    profile && profile.cgpa != null ? Number(profile.cgpa).toFixed(2) : '—';
  const displayName = user?.name || profile?.userName || 'User';
  const studentMeta = profile
    ? [profile.departmentName, profile.batch ? `Batch ${profile.batch}` : null]
        .filter((part): part is string => !!part)
        .join(' · ')
    : undefined;

  return (
    <PageContainer className="space-y-6">
      <WelcomeHeader
        name={displayName}
        roleLabel="Student"
        meta={studentMeta}
        subtitle="Track placement drives, interviews and your readiness."
      />

      {!metricsReady ? (
        <MetricsSkeleton />
      ) : profileError ? (
        <DashboardError
          title="Unable to load your profile"
          description="We couldn't retrieve your student profile right now."
          onRetry={() => setRetryKey((k) => k + 1)}
        />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
          <MetricCard
            label="Upcoming Drives"
            value={upcomingDrives.totalElements}
            icon={CalendarClock}
            sub="Scheduled drives"
            href="/student/drives"
          />
          <MetricCard
            label="Open Drives"
            value={openDrives.totalElements}
            icon={Briefcase}
            sub="Register before the deadline"
            href="/student/drives"
          />
          <MetricCard
            label="My Interviews"
            value={interviewsTotal}
            icon={ClipboardList}
            sub="Interview rounds so far"
            href="/student/interviews"
          />
          <MetricCard
            label="Current CGPA"
            value={cgpaValue}
            icon={GraduationCap}
            sub={profile?.placementStatus ? fmtStatus(profile.placementStatus) : 'Update in your profile'}
          />
        </div>
      )}

      {!profileLoading && profile && missing.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-4 px-4 py-3.5 rounded-[14px] border border-warning-600/20 bg-warning-50 shadow-soft">
          <div className="flex items-center gap-3">
            <span className="shrink-0 flex items-center justify-center w-9 h-9 rounded-[9px] bg-warning-500/10 text-warning-600">
              <AlertCircle size={17} />
            </span>
            <div>
              <p className="text-[13.5px] font-semibold text-warning-700">Complete your profile</p>
              <p className="text-[12.5px] text-warning-700/80 mt-0.5 max-w-xl">
                {missing.length} {missing.length === 1 ? 'detail is' : 'details are'} still missing —
                add {missing.slice(0, 3).join(', ')}
                {missing.length > 3 ? ` and ${missing.length - 3} more` : ''} to stay eligible for drives.
              </p>
            </div>
          </div>
          <Link
            to="/profile"
            className="shrink-0 inline-flex items-center gap-1 text-[13px] font-semibold text-warning-700 hover:underline"
          >
            Complete profiling
            <ArrowRight size={14} />
          </Link>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
        <DashboardSection
          title="Open for Registration"
          subtitle="Drives you can apply to right now"
          action={<SectionAction label="View all" to="/student/drives" />}
          className="lg:col-span-2"
        >
          {openDrives.loading ? (
            <DashboardRowsSkeleton />
          ) : openDrives.error ? (
            <DashboardError
              title="Unable to load drives"
              description="We couldn't retrieve placement drives right now."
              onRetry={openDrives.refresh}
            />
          ) : openDrives.data.length === 0 ? (
            <DashboardEmpty
              icon={Briefcase}
              title="No drives open for registration"
              description="When a drive opens, it will appear here with its eligibility details."
            />
          ) : (
            openDrives.data.map((drive) => (
              <DriveRow key={drive.id} drive={drive} to="/student/drives" />
            ))
          )}
        </DashboardSection>

        <DashboardSection
          title="Recent Interviews"
          subtitle="Your latest interview rounds"
          action={<SectionAction label="View all" to="/student/interviews" />}
        >
          {interviewsLoading ? (
            <DashboardRowsSkeleton rows={3} />
          ) : interviewsError ? (
            <DashboardError
              title="Unable to load interviews"
              description="We couldn't retrieve your interview rounds right now."
              onRetry={() => setRetryKey((k) => k + 1)}
            />
          ) : interviews.length === 0 ? (
            <DashboardEmpty
              icon={ClipboardList}
              title="No interviews yet"
              description="Interviews scheduled for you will appear here once your drives begin."
            />
          ) : (
            interviews.map((interview) => (
              <InterviewRow key={interview.id} interview={interview} to="/student/interviews" />
            ))
          )}
        </DashboardSection>
      </div>

      <DashboardSection
        title="Upcoming Drives"
        subtitle="Placement drives announced for later"
        action={<SectionAction label="View all" to="/student/drives" />}
      >
        {upcomingDrives.loading ? (
          <DashboardRowsSkeleton />
        ) : upcomingDrives.error ? (
          <DashboardError
            title="Unable to load drives"
            description="We couldn't retrieve placement drives right now."
            onRetry={upcomingDrives.refresh}
          />
        ) : upcomingDrives.data.length === 0 ? (
          <DashboardEmpty
            icon={CalendarClock}
            title="No upcoming placement drives"
            description="New opportunities will appear here when they are published."
          />
        ) : (
          upcomingDrives.data.map((drive) => (
            <DriveRow key={drive.id} drive={drive} to="/student/drives" />
          ))
        )}
      </DashboardSection>
    </PageContainer>
  );
}

function fmtStatus(value: string): string {
  return value
    .split('_')
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ');
}