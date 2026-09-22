import {
  LayoutDashboard,
  Users,
  Building2,
  ShieldCheck,
  GraduationCap,
  Briefcase,
  ClipboardList,
  Mail,
  BarChart3,
  FileText,
  BookOpen,
  Mic,
  Map,
  CalendarDays,
  Clock3,
  FolderOpen,
  Handshake,
  Sparkles,
  UsersRound,
  Target,
  Share2,
  Trophy,
  Bell,
  type LucideIcon,
} from 'lucide-react';

export type Role = 'PO' | 'PC' | 'PR' | 'STUDENT';

export interface NavItem {
  label: string;
  path: string;
  icon: LucideIcon;
  aliases?: string[];
  /** Rendered when the feature is implemented. Hidden (no dead links) until then. */
  enabled?: boolean;
  /** Optional subtle label group used inside wide dropdowns (e.g. Community). */
  section?: string;
  /** Optional tiny helper text shown inside dropdown rows. */
  description?: string;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

export const NAV_GROUPS: Record<Role, NavGroup[]> = {
  PO: [
    {
      label: 'Dashboard',
      items: [{ label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard }],
    },
    {
      label: 'Placement',
      items: [{ label: 'Placement Drives', path: '/placement-drives', icon: ClipboardList }],
    },
    {
      label: 'Management',
      items: [
        { label: 'Students', path: '/students', icon: Users },
        { label: 'Departments', path: '/departments', icon: Building2 },
        { label: 'PC Management', path: '/pc-management', icon: ShieldCheck },
        { label: 'PR Management', path: '/pr-management', icon: GraduationCap },
        { label: 'Companies', path: '/companies', icon: Briefcase },
      ],
    },
    {
      label: 'Insights',
      items: [
        { label: 'Reports', path: '/reports', icon: BarChart3 },
        { label: 'Audit Logs', path: '/audit-logs', icon: FileText },
      ],
    },
  ],
  PC: [
    {
      label: 'Dashboard',
      items: [{ label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard }],
    },
    {
      label: 'Placement',
      items: [{ label: 'Placement Drives', path: '/placement-drives', icon: ClipboardList }],
    },
    {
      label: 'Management',
      items: [
        { label: 'Students', path: '/students', icon: Users },
        { label: 'Companies', path: '/companies', icon: Briefcase },
      ],
    },
    {
      label: 'Contact Requests',
      items: [{ label: 'Contact Requests', path: '/contact-requests', icon: Mail }],
    },
    {
      label: 'Insights',
      items: [{ label: 'Reports', path: '/reports', icon: BarChart3 }],
    },
  ],
  PR: [
    {
      label: 'Dashboard',
      items: [{ label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard }],
    },
    {
      label: 'Students',
      items: [{ label: 'Students', path: '/students', icon: Users }],
    },
    {
      label: 'Contact Requests',
      items: [{ label: 'Contact Requests', path: '/contact-requests', icon: Mail }],
    },
    {
      label: 'Placement',
      items: [
        { label: 'Placement Drives', path: '/student/drives', icon: ClipboardList, aliases: ['/placement-drives'] },
        { label: 'Interviews', path: '/student/interviews', icon: Briefcase, aliases: ['/interviews'] },
      ],
    },
    {
      label: 'Prepare',
      items: [
        { label: 'Interview Prep', path: '/preparation', icon: BookOpen, enabled: false },
        { label: 'Resume Analyzer', path: '/resume-analyzer', icon: FileText },
        { label: 'Mock Interview', path: '/mock-interview', icon: Mic, enabled: false },
        { label: 'Skill Roadmap', path: '/skill-roadmap', icon: Map, enabled: false },
      ],
    },
    {
      label: 'Career',
      items: [
        { label: 'Calendar', path: '/calendar', icon: CalendarDays, enabled: false },
        { label: 'Slots', path: '/slots', icon: Clock3, enabled: false },
        { label: 'Documents', path: '/documents', icon: FolderOpen, enabled: false },
      ],
    },
    {
      label: 'Community',
      items: [
        { label: 'Mentorship', path: '/community/mentorship', icon: Handshake, section: 'Connect', enabled: false },
        { label: 'Stories', path: '/community/stories', icon: Sparkles, section: 'Connect', enabled: false },
        { label: 'Squads', path: '/community/squads', icon: UsersRound, section: 'Discover', enabled: false },
        { label: 'Tracker', path: '/community/tracker', icon: Target, section: 'Discover', enabled: false },
        { label: 'Referrals', path: '/community/referrals', icon: Share2, section: 'Progress', enabled: false },
        { label: 'Leaderboard', path: '/community/leaderboard', icon: Trophy, section: 'Progress', enabled: false },
        { label: 'Notifications', path: '/community/notifications', icon: Bell, section: 'Updates', enabled: false },
      ],
    },
  ],
  STUDENT: [
    {
      label: 'Dashboard',
      items: [{ label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard }],
    },
    {
      label: 'Placement',
      items: [
        { label: 'Placement Drives', path: '/student/drives', icon: ClipboardList, aliases: ['/placement-drives'] },
        { label: 'Interviews', path: '/student/interviews', icon: Briefcase, aliases: ['/interviews'] },
      ],
    },
    {
      label: 'Prepare',
      items: [
        { label: 'Interview Prep', path: '/preparation', icon: BookOpen, enabled: false },
        { label: 'Resume Analyzer', path: '/resume-analyzer', icon: FileText },
        { label: 'Mock Interview', path: '/mock-interview', icon: Mic, enabled: false },
        { label: 'Skill Roadmap', path: '/skill-roadmap', icon: Map, enabled: false },
      ],
    },
    {
      label: 'Career',
      items: [
        { label: 'Calendar', path: '/calendar', icon: CalendarDays, enabled: false },
        { label: 'Slots', path: '/slots', icon: Clock3, enabled: false },
        { label: 'Documents', path: '/documents', icon: FolderOpen, enabled: false },
      ],
    },
    {
      label: 'Community',
      items: [
        { label: 'Mentorship', path: '/community/mentorship', icon: Handshake, section: 'Connect', enabled: false },
        { label: 'Stories', path: '/community/stories', icon: Sparkles, section: 'Connect', enabled: false },
        { label: 'Squads', path: '/community/squads', icon: UsersRound, section: 'Discover', enabled: false },
        { label: 'Tracker', path: '/community/tracker', icon: Target, section: 'Discover', enabled: false },
        { label: 'Referrals', path: '/community/referrals', icon: Share2, section: 'Progress', enabled: false },
        { label: 'Leaderboard', path: '/community/leaderboard', icon: Trophy, section: 'Progress', enabled: false },
        { label: 'Notifications', path: '/community/notifications', icon: Bell, section: 'Updates', enabled: false },
      ],
    },
  ],
};

export const roleLabels: Record<Role, string> = {
  PO: 'Placement Officer',
  PC: 'Placement Coordinator',
  PR: 'Placement Representative',
  STUDENT: 'Student',
};

/** Drops not-yet-implemented items/groups so no dead links are ever rendered. */
export function visibleGroups(groups: NavGroup[]): NavGroup[] {
  return groups
    .map((group) => ({ ...group, items: group.items.filter((item) => item.enabled !== false) }))
    .filter((group) => group.items.length > 0);
}

export function isPathActive(pathname: string, item: NavItem): boolean {
  if (pathname === item.path) return true;
  if (item.aliases?.includes(pathname)) return true;
  if (pathname.startsWith(item.path + '/')) return true;
  return false;
}

export function isGroupActive(pathname: string, group: NavGroup): boolean {
  return group.items.some((item) => isPathActive(pathname, item));
}

export function getBreadcrumbTitle(pathname: string): string {
  const exact: Record<string, string> = {
    '/dashboard': 'Dashboard',
    '/students': 'Students',
    '/departments': 'Departments',
    '/pc-management': 'PC Management',
    '/pr-management': 'PR Management',
    '/companies': 'Companies',
    '/placement-drives': 'Placement Drives',
    '/messages': 'Messages',
    '/reports': 'Reports',
    '/audit-logs': 'Audit Logs',
    '/contact-requests': 'Contact Requests',
    '/profile': 'My Profile',
    '/interviews': 'My Interviews',
    '/student/drives': 'Placement Drives',
    '/student/interviews': 'My Interviews',
    '/preparation': 'Interview Prep',
    '/resume-analyzer': 'Resume Analyzer',
    '/mock-interview': 'Mock Interview',
    '/skill-roadmap': 'Skill Roadmap',
    '/calendar': 'Calendar',
    '/slots': 'Slots',
    '/documents': 'Documents',
    '/community/mentorship': 'Mentorship',
    '/community/stories': 'Stories',
    '/community/squads': 'Squads',
    '/community/tracker': 'Tracker',
    '/community/referrals': 'Referrals',
    '/community/leaderboard': 'Leaderboard',
    '/community/notifications': 'Notifications',
  };
  return exact[pathname] || 'Page';
}