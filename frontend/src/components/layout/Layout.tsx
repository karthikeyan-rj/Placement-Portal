import { useState, useEffect, useCallback } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  LayoutDashboard,
  Users,
  Building2,
  GraduationCap,
  MessageSquare,
  FileText,
  Briefcase,
  Mail,
  ClipboardList,
  ShieldCheck,
  BarChart3,
  LogOut,
  Menu,
  X,
  ChevronRight,
  PanelLeftClose,
  PanelLeftOpen,
  type LucideIcon,
} from 'lucide-react';

interface NavItem {
  label: string;
  path: string;
  icon: LucideIcon;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const DESKTOP_BREAKPOINT = 1024;
const EXPANDED_W = 260;
const COLLAPSED_W = 76;
const STORAGE_KEY = 'placement-sidebar-collapsed';

const roleNavGroups: Record<string, NavGroup[]> = {
  PO: [
    {
      label: 'Overview',
      items: [{ label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard }],
    },
    {
      label: 'Management',
      items: [
        { label: 'Students', path: '/students', icon: Users },
        { label: 'Departments', path: '/departments', icon: Building2 },
        { label: 'PC Management', path: '/pc-management', icon: ShieldCheck },
        { label: 'PR Management', path: '/pr-management', icon: GraduationCap },
        { label: 'Companies', path: '/companies', icon: Briefcase },
        { label: 'Placement Drives', path: '/placement-drives', icon: ClipboardList },
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
      label: 'Overview',
      items: [{ label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard }],
    },
    {
      label: 'Management',
      items: [
        { label: 'Students', path: '/students', icon: Users },
        { label: 'Companies', path: '/companies', icon: Briefcase },
        { label: 'Placement Drives', path: '/placement-drives', icon: ClipboardList },
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
      label: 'Overview',
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
      label: 'Overview',
      items: [{ label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard }],
    },
    {
      label: 'Placement',
      items: [
        { label: 'My Profile', path: '/profile', icon: GraduationCap },
        { label: 'Placement Drives', path: '/student/drives', icon: ClipboardList },
        { label: 'My Interviews', path: '/interviews', icon: Briefcase },
      ],
    },
    {
      label: 'Communication',
      items: [
        { label: 'Messages', path: '/messages', icon: MessageSquare },
        { label: 'Contact Requests', path: '/contact-requests', icon: Mail },
      ],
    },
  ],
};

const roleLabels: Record<string, string> = {
  PO: 'Administrator',
  PC: 'Coordinator',
  PR: 'Representative',
  STUDENT: 'Student',
};

const pageTitles: Record<string, string> = {
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

function getInitials(name: string): string {
  return name
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);
}

function isPathActive(pathname: string, itemPath: string): boolean {
  return (
    pathname === itemPath ||
    (itemPath === '/dashboard' && pathname === '/')
  );
}

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // Desktop collapse state is centralized HERE and persisted to localStorage.
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEY) || 'false');
    } catch {
      return false;
    }
  });
  // Mobile drawer state.
  const [mobileOpen, setMobileOpen] = useState(false);

  const groups = roleNavGroups[user?.role || 'STUDENT'] || [];
  const pageTitle = pageTitles[location.pathname] || 'Page';
  const role = user?.role || 'STUDENT';

  const toggleCollapsed = useCallback(() => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      } catch {
        /* ignore */
      }
      return next;
    });
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  const closeMobile = useCallback(() => setMobileOpen(false), []);

  const handleNav = useCallback(
    (path: string) => {
      navigate(path);
      closeMobile();
    },
    [navigate, closeMobile],
  );

  // Close the mobile drawer whenever the route changes.
  useEffect(() => {
    closeMobile();
  }, [location.pathname, closeMobile]);

  // Close the mobile drawer when resizing into the desktop breakpoint.
  useEffect(() => {
    const mq = window.matchMedia(`(min-width: ${DESKTOP_BREAKPOINT}px)`);
    const onResize = () => {
      if (mq.matches) setMobileOpen(false);
    };
    onResize();
    mq.addEventListener('change', onResize);
    return () => mq.removeEventListener('change', onResize);
  }, []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMobileOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const sidebarWidth = collapsed ? COLLAPSED_W : EXPANDED_W;

  const renderItem = (item: NavItem, expanded: boolean) => {
    const isActive = isPathActive(location.pathname, item.path);
    const Icon = item.icon;
    return (
      <button
        key={item.path}
        onClick={() => handleNav(item.path)}
        aria-label={item.label}
        className={`group relative w-full flex items-center gap-2.5 h-[42px] rounded-[8px] text-[14px] font-medium transition-all duration-200 ${
          isActive
            ? 'bg-primary-500/15 text-white'
            : 'text-info-soft/80 hover:bg-sidebar-hover hover:text-white'
        } ${expanded ? 'px-3' : 'px-0 justify-center'}`}
      >
        {isActive && (
          <span className="absolute left-0 top-1/2 -translate-y-1/2 h-[18px] w-[3px] rounded-r-full bg-primary-500" />
        )}
        <Icon
          size={18}
          strokeWidth={isActive ? 2 : 1.75}
          className={
            isActive ? 'text-primary-300 shrink-0' : 'text-info-soft/60 group-hover:text-info-soft shrink-0'
          }
        />
        <span
          className={`truncate transition-opacity duration-200 overflow-hidden ${expanded ? 'opacity-100' : 'opacity-0 w-0'}`}
        >
          {item.label}
        </span>

        {!expanded && (
          <span
            role="tooltip"
            className="pointer-events-none absolute left-full ml-4 top-1/2 -translate-y-1/2 hidden lg:group-hover:block whitespace-nowrap rounded-[6px] bg-neutral-900 px-2.5 py-1.5 text-[12px] font-medium text-white shadow-overlay"
            style={{ zIndex: 60 }}
          >
            {item.label}
          </span>
        )}
      </button>
    );
  };

  const sidebarInner = (expanded: boolean) => (
    <div
      className="flex flex-col h-full bg-sidebar text-white transition-[width] duration-200 overflow-hidden"
      style={{ width: expanded ? EXPANDED_W : COLLAPSED_W }}
    >
      {/* Brand */}
      <div
        className={`flex items-center h-[72px] border-b border-white/10 transition-[padding] duration-200 ${
          expanded ? 'px-5' : 'px-0 justify-center'
        }`}
      >
        <div className="flex items-center gap-3 overflow-hidden">
          <div className="flex items-center justify-center w-9 h-9 rounded-[10px] bg-primary-500 text-white font-bold text-sm shrink-0">
            PP
          </div>
          <div className={`min-w-0 transition-opacity duration-200 ${expanded ? 'opacity-100' : 'opacity-0 w-0'}`}>
            <h1 className="text-[15px] font-semibold text-white leading-tight whitespace-nowrap">
              Placement Portal
            </h1>
            <p className="text-[11.5px] text-info-soft/60 leading-tight mt-0.5 whitespace-nowrap">
              Placement Management
            </p>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto overflow-x-hidden px-3 py-4">
        {groups.map((group, gi) => (
          <div key={group.label} className={gi > 0 ? 'mt-7' : 'mt-1'}>
            {expanded && (
              <p className="px-3 mb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-info-soft/45">
                {group.label}
              </p>
            )}
            <div className="space-y-1">{group.items.map((item) => renderItem(item, expanded))}</div>
          </div>
        ))}
      </nav>

      {/* Profile */}
      <div className="border-t border-white/10 px-3 py-4">
        {expanded ? (
          <div className="flex items-center gap-3 px-2">
            <div className="flex items-center justify-center w-9 h-9 rounded-full bg-info-500 text-white text-xs font-semibold shrink-0">
              {user?.name ? getInitials(user.name) : 'U'}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-[13.5px] font-semibold text-white truncate leading-tight">
                {user?.name || 'User'}
              </p>
              <p className="text-[12px] text-info-soft/60 leading-tight mt-0.5">
                {roleLabels[role]}
              </p>
            </div>
            <button
              onClick={handleLogout}
              className="p-2 rounded-lg text-info-soft/60 hover:text-white hover:bg-white/10 transition-colors shrink-0"
              title="Sign out"
              aria-label="Sign out"
            >
              <LogOut size={16} />
            </button>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-3">
            <div className="flex items-center justify-center w-9 h-9 rounded-full bg-info-500 text-white text-xs font-semibold shrink-0">
              {user?.name ? getInitials(user.name) : 'U'}
            </div>
            <button
              onClick={handleLogout}
              className="p-2 rounded-lg text-info-soft/60 hover:text-white hover:bg-white/10 transition-colors"
              title="Sign out"
              aria-label="Sign out"
            >
              <LogOut size={16} />
            </button>
          </div>
        )}
      </div>
    </div>
  );

  const mobileDrawer = mobileOpen && (
    <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-neutral-900/60 animate-fadeIn" onClick={closeMobile} />
      <div className="absolute inset-y-0 left-0 animate-slideRight shadow-overlay">
        <button
          onClick={closeMobile}
          className="absolute top-4 right-4 z-20 p-1.5 rounded-md text-info-soft/80 hover:text-white hover:bg-white/10 transition-colors"
          aria-label="Close menu"
        >
          <X size={18} />
        </button>
        {sidebarInner(true)}
      </div>
    </div>
  );

  return (
    <div className="flex h-screen bg-background overflow-hidden">
      {/* Desktop sidebar */}
      <aside
        className="hidden lg:flex lg:flex-col shrink-0 transition-[width] duration-200"
        style={{ width: sidebarWidth }}
      >
        {sidebarInner(!collapsed)}
      </aside>

      {/* Mobile drawer */}
      {mobileDrawer}

      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Header */}
        <header className="bg-white border-b border-border h-16 px-4 lg:px-6 flex items-center justify-between shrink-0 z-10">
          <div className="flex items-center gap-3 min-w-0">
            {/* Mobile menu button */}
            <button
              onClick={() => setMobileOpen(true)}
              className="lg:hidden p-2 -ml-1 rounded-[8px] text-neutral-500 hover:bg-neutral-100 hover:text-neutral-700 transition-colors"
              aria-label="Open menu"
            >
              <Menu size={20} />
            </button>

            {/* Desktop collapse toggle */}
            <button
              onClick={toggleCollapsed}
              className="hidden lg:inline-flex p-2 -ml-1 rounded-[8px] text-neutral-500 hover:bg-neutral-100 hover:text-neutral-700 transition-colors"
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            >
              {collapsed ? <PanelLeftOpen size={20} /> : <PanelLeftClose size={20} />}
            </button>

            <nav className="flex items-center gap-1.5 text-[14px] whitespace-nowrap">
              <span className="text-neutral-400">Home</span>
              <ChevronRight size={14} className="text-neutral-300 shrink-0" />
              <span className="font-semibold text-neutral-700 truncate">{pageTitle}</span>
            </nav>
          </div>
          <div className="flex items-center gap-3 shrink-0">
            <div className="hidden sm:block text-right">
              <p className="text-[14px] font-semibold text-neutral-900 leading-tight">{user?.name}</p>
              <p className="text-[13px] text-neutral-500 leading-tight mt-0.5">{roleLabels[role]}</p>
            </div>
            <div className="flex items-center justify-center w-9 h-9 rounded-full bg-brand-navy text-white text-xs font-semibold ring-1 ring-white/20">
              {user?.name ? getInitials(user.name) : 'U'}
            </div>
          </div>
        </header>

        {/* Main */}
        <main className="flex-1 overflow-y-auto overflow-x-hidden">
          <div className="px-5 py-8 lg:px-8 lg:py-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
