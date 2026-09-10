import { useAuth } from '../../context/AuthContext';
import { usePaginatedData } from '../../hooks/useApi';
import type { ContactRequest, Message, StudentProfile } from '../../types';
import { Users, Contact, MessagesSquare } from 'lucide-react';
import { PageContainer } from '../../components/ui';
import {
  ContactRequestRow,
  DashboardEmpty,
  DashboardError,
  DashboardRowsSkeleton,
  DashboardSection,
  MessageRow,
  MetricCard,
  MetricsSkeleton,
  SectionAction,
  WelcomeHeader,
} from '../../components/dashboard';

export default function PRDashboard() {
  const { user } = useAuth();
  const deptId = user?.departmentId ?? undefined;

  const deptStudents = usePaginatedData<StudentProfile>({
    url: '/students',
    params: { ...(deptId ? { departmentId: deptId } : {}), size: 1 },
  });
  const requests = usePaginatedData<ContactRequest>({
    url: '/contact-requests/incoming',
    params: { size: 5 },
  });
  const messages = usePaginatedData<Message>({
    url: '/messages/received',
    params: { size: 4 },
  });

  const metricsLoading = deptStudents.loading || requests.loading || messages.loading;
  const metricsError = deptStudents.error || requests.error || messages.error;

  function refreshAll() {
    deptStudents.refresh();
    requests.refresh();
    messages.refresh();
  }

  return (
    <PageContainer className="space-y-6">
      <WelcomeHeader
        name={user?.name || 'User'}
        roleLabel="Placement Representative"
        meta={user?.departmentName ?? undefined}
        subtitle={`Supporting the students assigned to your department.`}
      />

      {metricsLoading ? (
        <MetricsSkeleton count={3} />
      ) : metricsError ? (
        <DashboardError
          title="Unable to load dashboard data"
          description="We couldn't retrieve your overview right now."
          onRetry={refreshAll}
        />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
          <MetricCard
            label="Department Students"
            value={deptStudents.totalElements}
            icon={Users}
            sub={user?.departmentName || 'Your department'}
            href="/students"
          />
          <MetricCard
            label="Contact Requests"
            value={requests.totalElements}
            icon={Contact}
            sub="From students seeking help"
            href="/contact-requests"
          />
          <MetricCard
            label="Messages Received"
            value={messages.totalElements}
            icon={MessagesSquare}
            sub="Latest updates from coordinators"
            href="/messages"
          />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
        <DashboardSection
          title="Contact Requests"
          subtitle="Student requests awaiting your response"
          action={<SectionAction label="Manage" to="/contact-requests" />}
          className="lg:col-span-2"
        >
          {requests.loading ? (
            <DashboardRowsSkeleton />
          ) : requests.error ? (
            <DashboardError
              title="Unable to load requests"
              description="We couldn't retrieve contact requests right now."
              onRetry={requests.refresh}
            />
          ) : requests.data.length === 0 ? (
            <DashboardEmpty
              icon={Contact}
              title="No contact requests yet"
              description="Requests from students will appear here when they reach out to you."
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
    </PageContainer>
  );
}