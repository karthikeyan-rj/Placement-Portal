import { useAuth } from '../context/AuthContext';
import PODashboard from './po/PODashboard';
import {
  Briefcase,
  FileText,
  User,
  MessageSquare,
  Contact,
  ChevronRight,
} from 'lucide-react';
import { PageContainer, PageHeader } from '../components/ui';
import type { LucideIcon } from 'lucide-react';

interface QuickLink {
  href: string;
  label: string;
  description: string;
  icon: LucideIcon;
  accent: 'navy' | 'teal';
}

const accentIconBg: Record<string, string> = {
  navy: 'bg-brand-navy text-white',
  teal: 'bg-accent-50 text-accent-600 ring-1 ring-inset ring-accent-600/10',
};

function QuickLinkCard({ link }: { link: QuickLink }) {
  return (
    <a
      href={link.href}
      className="group flex items-start gap-4 p-5 bg-white rounded-xl border border-neutral-200/70 shadow-card hover:shadow-raised hover:-translate-y-0.5 hover:border-primary-200/70 transition-all duration-150"
    >
      <div
        className={`shrink-0 p-2.5 rounded-lg ${accentIconBg[link.accent]}`}
      >
        <link.icon size={18} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[15px] font-semibold text-neutral-900 group-hover:text-primary-600 transition-colors">
          {link.label}
        </p>
        <p className="text-[15px] text-neutral-500 mt-0.5">{link.description}</p>
      </div>
      <ChevronRight
        size={16}
        className="shrink-0 mt-1 text-neutral-300 group-hover:text-neutral-400 group-hover:translate-x-0.5 transition-all duration-150"
      />
    </a>
  );
}

function QuickLinkLayout({ user, role, description, links }: {
  user: { name?: string } | null;
  role: string;
  description: string;
  links: QuickLink[];
}) {
  return (
    <PageContainer className="space-y-6">
      <PageHeader title={`Welcome, ${user?.name || role}`} description={description} />
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {links.map((link) => (
          <QuickLinkCard key={link.href} link={link} />
        ))}
      </div>
    </PageContainer>
  );
}

function PCDashboard() {
  const { user } = useAuth();

  const links: QuickLink[] = [
    {
      href: '/companies',
      label: 'Companies',
      description: 'Manage company profiles and recruitment partners',
      icon: Briefcase,
      accent: 'navy',
    },
    {
      href: '/placement-drives',
      label: 'Placement Drives',
      description: 'Create and manage placement drives',
      icon: Briefcase,
      accent: 'teal',
    },
    {
      href: '/reports',
      label: 'Reports',
      description: 'Download CSV reports and analytics',
      icon: FileText,
      accent: 'navy',
    },
  ];

  return (
    <QuickLinkLayout
      user={user}
      role="Coordinator"
      description="Placement Coordinator Dashboard"
      links={links}
    />
  );
}

function PRDashboard() {
  const { user } = useAuth();

  const links: QuickLink[] = [
    {
      href: '/messages',
      label: 'Messages',
      description: 'Send and receive messages',
      icon: MessageSquare,
      accent: 'navy',
    },
    {
      href: '/contact-requests',
      label: 'Contact Requests',
      description: 'Manage student contact requests',
      icon: Contact,
      accent: 'teal',
    },
  ];

  return (
    <QuickLinkLayout
      user={user}
      role="Representative"
      description="Placement Representative Dashboard"
      links={links}
    />
  );
}

function StudentDashboard() {
  const { user } = useAuth();

  const links: QuickLink[] = [
    {
      href: '/profile',
      label: 'My Profile',
      description: 'View and update your profile',
      icon: User,
      accent: 'navy',
    },
    {
      href: '/student/drives',
      label: 'Placement Drives',
      description: 'View available placement drives',
      icon: Briefcase,
      accent: 'teal',
    },
    {
      href: '/student/interviews',
      label: 'My Interviews',
      description: 'Track interview rounds and results',
      icon: FileText,
      accent: 'navy',
    },
  ];

  return (
    <QuickLinkLayout
      user={user}
      role="Student"
      description="Student Dashboard"
      links={links}
    />
  );
}

export default function DashboardPage() {
  const { user } = useAuth();

  switch (user?.role) {
    case 'PO':
      return <PODashboard />;
    case 'PC':
      return <PCDashboard />;
    case 'PR':
      return <PRDashboard />;
    default:
      return <StudentDashboard />;
  }
}
