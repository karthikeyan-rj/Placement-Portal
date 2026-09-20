import {
  LayoutDashboard,
  Users,
  Building2,
  ShieldCheck,
  GraduationCap,
  Briefcase,
  ClipboardList,
  MessageSquare,
  Mail,
  BarChart3,
  FileText,
  User,
  type LucideIcon,
} from 'lucide-react';

export type Role = 'PO' | 'PC' | 'PR' | 'STUDENT';

export interface NavItem {
  label: string;
  path: string;
  icon: LucideIcon;
  aliases?: string[];
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
      label: 'Communication',
      items: [{ label: 'Messages', path: '/messages', icon: MessageSquare }],
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
      label: 'Communication',
      items: [
        { label: 'Messages', path: '/messages', icon: MessageSquare },
        { label: 'Contact Requests', path: '/contact-requests', icon: Mail },
      ],
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
      label: 'Management',
      items: [{ label: 'Students', path: '/students', icon: Users }],
    },
    {
      label: 'Communication',
      items: [
        { label: 'Messages', path: '/messages', icon: MessageSquare },
        { label: 'Contact Requests', path: '/contact-requests', icon: Mail },
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
        { label: 'My Profile', path: '/profile', icon: User },
        { label: 'Placement Drives', path: '/student/drives', icon: ClipboardList },
        { label: 'Interviews', path: '/student/interviews', icon: Briefcase, aliases: ['/interviews'] },
      ],
    },
    {
      label: 'Communication',
      items: [{ label: 'Messages', path: '/messages', icon: MessageSquare }],
    },
  ],
};

export const roleLabels: Record<Role, string> = {
  PO: 'Administrator',
  PC: 'Coordinator',
  PR: 'Representative',
  STUDENT: 'Student',
};

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
  };
  return exact[pathname] || 'Page';
}