import { useAuth } from '../../context/AuthContext';
import { usePaginatedData } from '../../hooks/useApi';
import type { ContactRequest, Message, PlacementDrive, StudentProfile } from '../../types';
import { Users, Briefcase, CalendarClock, Contact, MessagesSquare } from 'lucide-react';
import { PageContainer } from '../../components/ui';
import {
  ContactRequestRow,
  DashboardEmpty,
  DashboardError,
  DashboardRowsSkeleton,
  DashboardSection,
  DriveRow,
  MessageRow,
  MetricCard,
  MetricsSkeleton,
  SectionAction,
  WelcomeHeader,
} from '../../components/dashboard';

export default function PCDashboard() {
  const { user } = useAuth();
  const deptId = user?.departmentId ?? undefined;

  const deptStudents = usePaginatedData<StudentProfile>({
    url: '/students',
    params: { ...(deptId ? { departmentId: deptId } : {}), size: 1 },
  });
  const activeDrives = usePaginatedData<PlacementDrive>({
    url: '/placement-drives',
    params: { status: 'REGISTRATION_OPEN', size: 5 },
  });
  const upcomingDrives = usePaginatedData<PlacementDrive>({
    url: '/placement-drives',
    params: { status: 'UPCOMING', size: 5 },
  });
  const requests = usePaginatedData<ContactRequest>({
    url: '/contact-requests/incoming',
    params: { size: 5 },
  });
  const messages = usePaginatedData<Message>({
    url: '/messages/received',
    params: { size: 4 },
  });

  const metricsLoading =
    deptStudents.loading || activeDrives.loading || upcomingDrives.loading || requests.loading;
  const metricsError = deptStudents.error || activeDrives.error || upcomingDrives.error || requests.error;

  const departmentName = user?.departmentName || 'your department';

  function refreshAll() {
    deptStudents.refresh();
    activeDrives.refresh();
    upcomingDrives.refresh();
    requests.refresh();
    messages.refresh();
  }

  return (
    <PageContainer className="space-y-6">
      <WelcomeHeader
        name={user?.name || 'User'}
        roleLabel="Placement Coordinator"
        meta={user?.departmentName ?? undefined}
        subtitle={`Drives, requests and messages across ${departmentName}.`}
      />

      {metricsLoading ? (
        <MetricsSkeleton />
      ) : metricsError ? (
        <DashboardError
          title="Unable to load dashboard data"
          description="We couldn't retrieve your overview right now."
          onRetry={refreshAll}
        />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
          <MetricCard
            label="Department Students"
            value={deptStudents.totalElements}
            icon={Users}
            sub={user?.departmentName || 'Your department'}
            href="/students"
          />
          <MetricCard
            label="Active Drives"
            value={activeDrives.totalElements}
            icon={Briefcase}
            sub="Open for registration"
            href="/placement-drives"
          />
          <MetricCard
            label="Upcoming Drives"
            value={upcomingDrives.totalElements}
            icon={CalendarClock}
            sub="Scheduled ahead"
            href="/placement-drives"
          />
          <MetricCard
            label="Contact Requests"
            value={requests.totalElements}
            icon={Contact}
            sub="Awaiting your response"
            href="/contact-requests"
          />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
        <DashboardSection
          title="Active Drives"
          subtitle="Placement drives currently open for registration"
          action={<SectionAction label="View all" to="/placement-drives" />}
          className="lg:col-span-2"
        >
          {activeDrives.loading ? (
            <DashboardRowsSkeleton />
          ) : activeDrives.error ? (
            <DashboardError
              title="Unable to load drives"
              description="We couldn't retrieve placement drives right now."
              onRetry={activeDrives.refresh}
            />
          ) : activeDrives.data.length === 0 ? (
            <DashboardEmpty
              icon={Briefcase}
              title="No drives open right now"
              description="When drives are opened for registration, they will appear here."
            />
          ) : (
            activeDrives.data.map((drive) => (
              <DriveRow key={drive.id} drive={drive} to="/placement-drives" />
            ))
          )}
        </DashboardSection>

        <div className="space-y-6">
          <DashboardSection
            title="Contact Requests"
            subtitle="Awaiting your response"
            action={<SectionAction label="Manage" to="/contact-requests" />}
          >
            {requests.loading ? (
              <DashboardRowsSkeleton rows={3} />
            ) : requests.error ? (
              <DashboardError
                title="Unable to load requests"
                description="We couldn't retrieve contact requests right now."
                onRetry={requests.refresh}
              />
            ) : requests.data.length === 0 ? (
              <DashboardEmpty
                icon={Contact}
                title="No pending contact requests"
                description="Requests from students will appear here when they reach out."
              />
            ) : (
              requests.data.map((request) => (
                <ContactRequestRow key={request.id} request={request} to="/contact-requests" />
              ))
            )}
          </DashboardSection>

          <DashboardSection title="Latest Messages" subtitle="Messages received recently">
            {messages.loading ? (
              <DashboardRowsSkeleton rows={3} />
            ) : messages.error ? (
              <DashboardError
                title="Unable to load messages"
                description="We couldn't retrieve your messages right now."
                onRetry={messages.refresh}
              />
            ) : messages.data.length === 0 ? (
              <DashboardEmpty
                icon={MessagesSquare}
                title="No messages yet"
                description="Messages sent to you will appear here."
              />
            ) : (
              messages.data.map((message) => <MessageRow key={message.id} message={message} to="/messages" />)
            )}
          </DashboardSection>
        </div>
      </div>
    </PageContainer>
  );
}