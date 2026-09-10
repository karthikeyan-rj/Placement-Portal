import { useState, useEffect, useRef, type KeyboardEvent as ReactKeyboardEvent } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useDevMode } from '../../context/DevModeContext';
import { useEffectiveRole } from '../../hooks/useEffectiveRole';
import { Avatar, Breadcrumb, type BreadcrumbSegment } from '../ui';
import {
  NAV_GROUPS,
  roleLabels,
  getBreadcrumbTitle,
  isGroupActive,
  isPathActive,
  type NavGroup,
  type NavItem,
} from '../../config/navigation';
import { Menu, X, ChevronDown, LogOut, User, Lock } from 'lucide-react';

const TOPBAR_H = 70;

function getBreadcrumbs(pathname: string): BreadcrumbSegment[] {
  const segments: BreadcrumbSegment[] = [{ label: 'Dashboard', path: '/dashboard' }];
  if (pathname !== '/dashboard') {
    segments.push({ label: getBreadcrumbTitle(pathname) });
  }
  return segments;
}

function NavUnderline() {
  return (
    <span className="absolute left-1/2 bottom-[3px] -translate-x-1/2 h-[3px] w-5 rounded-full bg-primary-500" />
  );
}

interface GroupDropdownProps {
  group: NavGroup;
  pathname: string;
  open: boolean;
  active: boolean;
  onToggle: () => void;
  onClose: () => void;
  onNavigate: (path: string) => void;
}

function GroupDropdown({ group, pathname, open, active, onToggle, onClose, onNavigate }: GroupDropdownProps) {
  const triggerRef = useRef<HTMLButtonElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const [focusIndex, setFocusIndex] = useState(-1);

  useEffect(() => {
    if (open && focusIndex >= 0 && itemRefs.current[focusIndex]) {
      itemRefs.current[focusIndex]?.focus();
    }
  }, [focusIndex, open]);

  const handleTriggerKey = (e: ReactKeyboardEvent<HTMLButtonElement>) => {
    if (!open) {
      if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        onToggle();
        setFocusIndex(0);
      }
      return;
    }
    if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
      triggerRef.current?.focus();
    }
  };

  const handleItemKey = (e: ReactKeyboardEvent<HTMLButtonElement>, index: number) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
      triggerRef.current?.focus();
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setFocusIndex((focusIndex + 1) % group.items.length);
      return;
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      setFocusIndex((focusIndex - 1 + group.items.length) % group.items.length);
      return;
    }
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onNavigate(group.items[index].path);
    }
  };

  return (
    <div className="relative">
      <button
        ref={triggerRef}
        type="button"
        onClick={onToggle}
        onKeyDown={handleTriggerKey}
        aria-haspopup="true"
        aria-expanded={open}
        className={`relative h-[40px] px-3.5 rounded-[10px] text-[14px] font-medium flex items-center gap-1.5 transition-all duration-150 ${
          active || open
            ? 'text-primary-700 bg-primary-50/70'
            : 'text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100/70'
        }`}
      >
        {group.label}
        <ChevronDown
          size={15}
          className={`transition-transform duration-150 ${open ? 'rotate-180' : ''} ${
            active || open ? 'text-primary-500' : 'text-neutral-400'
          }`}
        />
        {(active || open) && <NavUnderline />}
      </button>

      {open && (
        <div
          className="absolute left-0 top-full mt-2 z-50 w-60 glass-strong rounded-[14px] border border-white/40 shadow-overlay py-1.5 animate-slideDown"
          role="menu"
          aria-label={group.label}
        >
          {group.items.map((item, index) => {
            const itemActive = isPathActive(pathname, item);
            return (
              <button
                key={item.path}
                ref={(el) => {
                  itemRefs.current[index] = el;
                }}
                type="button"
                role="menuitem"
                onClick={() => onNavigate(item.path)}
                onKeyDown={(e) => handleItemKey(e, index)}
                className={`w-full flex items-center gap-3 px-3.5 py-2.5 my-0.5 text-[14px] text-left rounded-[10px] transition-colors outline-none ${
                  itemActive
                    ? 'bg-primary-50/80 text-primary-700 font-medium'
                    : 'text-neutral-700 hover:bg-neutral-100/70 hover:text-neutral-900'
                }`}
              >
                <span className={`shrink-0 ${itemActive ? 'text-primary-500' : 'text-neutral-400'}`}>
                  <item.icon size={17} />
                </span>
                {item.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface SingleLinkProps {
  item: NavItem;
  active: boolean;
  onNavigate: (path: string) => void;
}

function SingleLink({ item, active, onNavigate }: SingleLinkProps) {
  return (
    <button
      type="button"
      onClick={() => onNavigate(item.path)}
      className={`relative h-[40px] px-3.5 rounded-[10px] text-[14px] font-medium flex items-center transition-all duration-150 ${
        active ? 'text-primary-700 bg-primary-50/70' : 'text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100/70'
      }`}
    >
      {item.label}
      {active && <NavUnderline />}
    </button>
  );
}

export default function Layout() {
  const { user, logout } = useAuth();
  const { isPreviewing, exitPreview } = useDevMode();
  const effectiveRole = useEffectiveRole();
  const navigate = useNavigate();
  const location = useLocation();

  const [openGroup, setOpenGroup] = useState<string | null>(null);
  const [profileOpen, setProfileOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  const topNavRef = useRef<HTMLDivElement>(null);
  const profileRef = useRef<HTMLDivElement>(null);

  const role = (effectiveRole || 'STUDENT') as keyof typeof NAV_GROUPS;
  const groups = NAV_GROUPS[role] || [];
  const breadcrumbs = getBreadcrumbs(location.pathname);
  const displayName = user?.name || (isPreviewing ? 'Preview' : 'User');
  const isStudent = role === 'STUDENT';

  const closeMenus = () => {
    setOpenGroup(null);
    setProfileOpen(false);
  };

  const handleNavigate = (path: string) => {
    closeMenus();
    setMobileOpen(false);
    navigate(path);
  };

  const handleLogout = () => {
    closeMenus();
    setMobileOpen(false);
    if (isPreviewing) {
      exitPreview();
      navigate('/');
      return;
    }
    logout();
    navigate('/login', { replace: true });
  };

  useEffect(() => {
    closeMenus();
    setMobileOpen(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname]);

  useEffect(() => {
    const onDown = (e: MouseEvent) => {
      const target = e.target as Node;
      if (topNavRef.current && !topNavRef.current.contains(target)) {
        setOpenGroup(null);
      }
      if (profileRef.current && !profileRef.current.contains(target)) {
        setProfileOpen(false);
      }
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpenGroup(null);
        setProfileOpen(false);
        setMobileOpen(false);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    if (mobileOpen) {
      document.body.style.overflow = 'hidden';
      return () => {
        document.body.style.overflow = '';
      };
    }
  }, [mobileOpen]);

  const brand = (
    <div className="flex items-center gap-3 min-w-0">
      <div className="flex items-center justify-center w-9 h-9 rounded-[10px] bg-gradient-to-br from-primary-500 to-primary-700 text-white font-bold text-[14px] shrink-0 shadow-soft">
        PP
      </div>
      <p className="text-[18px] font-semibold text-neutral-900 leading-tight whitespace-nowrap tracking-tight">
        Placement Portal
      </p>
    </div>
  );

  const renderGroup = (group: NavGroup) =>
    group.items.length === 1 ? (
      <SingleLink
        key={group.items[0].path}
        item={group.items[0]}
        active={isPathActive(location.pathname, group.items[0])}
        onNavigate={handleNavigate}
      />
    ) : (
      <GroupDropdown
        key={group.label}
        group={group}
        pathname={location.pathname}
        open={openGroup === group.label}
        active={isGroupActive(location.pathname, group)}
        onToggle={() => setOpenGroup(openGroup === group.label ? null : group.label)}
        onClose={() => setOpenGroup(null)}
        onNavigate={handleNavigate}
      />
    );

  const desktopNav = (
    <nav ref={topNavRef} aria-label="Primary" className="hidden lg:flex items-center gap-1 ml-8">
      {groups.map(renderGroup)}
    </nav>
  );

  const profileMenuButton = (
    <button
      type="button"
      onClick={() => {
        setProfileOpen((prev) => !prev);
        setOpenGroup(null);
      }}
      aria-haspopup="true"
      aria-expanded={profileOpen}
      className={`relative flex items-center gap-2.5 rounded-[12px] px-2 py-1.5 transition-all duration-150 outline-none ${
        profileOpen ? 'bg-neutral-100/80' : 'hover:bg-neutral-100/70'
      }`}
    >
      <Avatar name={displayName} size="sm" />
      <div className="hidden sm:block text-left">
        <p className="text-[14px] font-semibold text-neutral-900 leading-tight max-w-[160px] truncate">{displayName}</p>
        <p className="text-[12px] text-neutral-400 leading-tight mt-0.5">{roleLabels[role]}</p>
      </div>
      <ChevronDown
        size={15}
        className={`hidden sm:block text-neutral-400 transition-transform duration-150 ${profileOpen ? 'rotate-180' : ''}`}
      />
    </button>
  );

  const profileMenu = (
    <div
      className="absolute right-0 top-full mt-2 z-50 w-60 glass-strong rounded-[14px] border border-white/40 shadow-overlay py-1.5 animate-slideDown"
      role="menu"
    >
      <div className="px-3.5 py-2.5 border-b border-neutral-100/60 mb-1">
        <p className="text-[14px] font-semibold text-neutral-900 truncate">{displayName}</p>
        <p className="text-[12px] text-neutral-400 truncate mt-0.5">{user?.email || roleLabels[role]}</p>
      </div>
      {isStudent && (
        <>
          <button
            type="button"
            role="menuitem"
            onClick={() => handleNavigate('/profile')}
            className="w-full flex items-center gap-2.5 px-3.5 py-2.5 my-0.5 text-[14px] text-left rounded-[10px] text-neutral-700 hover:bg-neutral-100/70 transition-colors"
          >
            <User size={15} className="text-neutral-400 shrink-0" />
            My Profile
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              closeMenus();
              navigate('/profile', { state: { security: true } });
            }}
            className="w-full flex items-center gap-2.5 px-3.5 py-2.5 my-0.5 text-[14px] text-left rounded-[10px] text-neutral-700 hover:bg-neutral-100/70 transition-colors"
          >
            <Lock size={15} className="text-neutral-400 shrink-0" />
            Change Password / Security
          </button>
        </>
      )}
      <div className="border-t border-neutral-100/60 mt-1 pt-1">
        <button
          type="button"
          role="menuitem"
          onClick={handleLogout}
          className="w-full flex items-center gap-2.5 px-3.5 py-2.5 my-0.5 text-[14px] text-left rounded-[10px] text-danger-600 hover:bg-danger-50/60 transition-colors"
        >
          <LogOut size={15} className="shrink-0" />
          Logout
        </button>
      </div>
    </div>
  );

  const mobileMenu = mobileOpen && (
    <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true">
      <div
        className="absolute inset-0 bg-dark/50 backdrop-blur-sm animate-fadeIn"
        onClick={() => setMobileOpen(false)}
      />
      <div className="absolute inset-y-0 right-0 w-[320px] max-w-[88vw] bg-white shadow-overlay animate-slideInRight flex flex-col">
        <div className="flex items-center justify-between border-b border-neutral-200/60 px-4 py-3 shrink-0">
          {brand}
          <button
            type="button"
            onClick={() => setMobileOpen(false)}
            className="p-2 -mr-1 rounded-[10px] text-neutral-400 hover:text-neutral-700 hover:bg-neutral-100/80 transition-colors"
            aria-label="Close menu"
          >
            <X size={20} />
          </button>
        </div>

        <nav aria-label="Primary" className="flex-1 overflow-y-auto overflow-x-hidden px-3 py-4">
          {groups.map((group) => {
            const groupActive = isGroupActive(location.pathname, group);
            return (
              <div key={group.label} className="mb-5">
                <p className="px-3 mb-1.5 text-[11px] font-semibold uppercase tracking-[0.12em] text-neutral-400">
                  {group.label}
                </p>
                <div className="space-y-0.5">
                  {group.items.map((item) => {
                    const itemActive = isPathActive(location.pathname, item);
                    const Icon = item.icon;
                    return (
                      <button
                        key={item.path}
                        type="button"
                        onClick={() => handleNavigate(item.path)}
                        className={`w-full flex items-center gap-3 h-11 px-3.5 rounded-[10px] text-[14px] font-medium transition-colors ${
                          groupActive && itemActive
                            ? 'bg-primary-50/80 text-primary-700'
                            : 'text-neutral-700 hover:bg-neutral-100/70'
                        }`}
                      >
                        <Icon size={17} className={groupActive && itemActive ? 'text-primary-500' : 'text-neutral-400'} />
                        {item.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </nav>

        <div className="border-t border-neutral-200/60 px-3 py-3 shrink-0">
          <div className="flex items-center gap-3 px-2 py-1.5">
            <Avatar name={displayName} size="sm" />
            <div className="min-w-0 flex-1">
              <p className="text-[14px] font-semibold text-neutral-900 truncate leading-tight">{displayName}</p>
              <p className="text-[12px] text-neutral-400 leading-tight mt-0.5">{roleLabels[role]}</p>
            </div>
            <button
              type="button"
              onClick={handleLogout}
              className="p-2.5 rounded-[10px] text-neutral-400 hover:text-danger-600 hover:bg-danger-50/60 transition-colors"
              title="Sign out"
              aria-label="Sign out"
            >
              <LogOut size={17} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className="flex flex-col h-screen app-bg overflow-hidden">
      <header
        className="sticky top-0 z-40 shrink-0 border-b border-neutral-200/70 bg-white/85 backdrop-blur-md shadow-soft"
        style={{ height: TOPBAR_H }}
      >
        <div className="mx-auto w-full max-w-[1500px] h-full flex items-center justify-between px-4 sm:px-6 lg:px-8 gap-4">
          <div className="flex items-center min-w-0">{brand}</div>

          {desktopNav}

          <div className="flex items-center gap-1 shrink-0">
            {isPreviewing && (
              <span className="hidden sm:inline-flex rounded-full bg-accent-50 text-accent-500 px-2.5 py-0.5 text-[11px] font-semibold ring-1 ring-inset ring-accent-300/30 mr-1">
                Preview
              </span>
            )}
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              className="lg:hidden p-2.5 rounded-[10px] text-neutral-500 hover:bg-neutral-100 hover:text-neutral-800 transition-colors"
              aria-label="Open menu"
            >
              <Menu size={21} />
            </button>
            <div ref={profileRef} className="relative">
              {profileMenuButton}
              {profileOpen && profileMenu}
            </div>
          </div>
        </div>
      </header>

      {mobileMenu}

      <nav
        aria-label="Breadcrumb"
        className="shrink-0 border-b border-neutral-200/60 bg-white/60 backdrop-blur-sm px-4 sm:px-6 lg:px-8"
      >
        <div className="mx-auto w-full max-w-[1500px] py-2.5">
          <Breadcrumb segments={breadcrumbs} />
        </div>
      </nav>

      <main className="flex-1 overflow-y-auto overflow-x-hidden">
        <div className="mx-auto w-full max-w-[1500px] px-4 sm:px-6 lg:px-8 py-6 sm:py-8 relative z-10">
          <Outlet />
        </div>
      </main>
    </div>
  );
}