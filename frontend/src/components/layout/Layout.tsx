import { useState, useEffect, useLayoutEffect, useRef, type KeyboardEvent as ReactKeyboardEvent } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useDevMode } from '../../context/DevModeContext';
import { useNotifications } from '../../context/NotificationsContext';
import { useEffectiveRole } from '../../hooks/useEffectiveRole';
import { Avatar, Breadcrumb, type BreadcrumbSegment } from '../ui';
import {
  NAV_GROUPS,
  roleLabels,
  getBreadcrumbTitle,
  isGroupActive,
  isPathActive,
  visibleGroups,
  type NavGroup,
  type NavItem,
} from '../../config/navigation';
import { Menu, X, ChevronDown, LogOut, User, Lock, Mail } from 'lucide-react';
import AccountMenu from './AccountMenu';

const TOPBAR_H = 64;

function getBreadcrumbs(pathname: string): BreadcrumbSegment[] {
  const segments: BreadcrumbSegment[] = [{ label: 'Dashboard', path: '/dashboard' }];
  if (pathname !== '/dashboard') {
    segments.push({ label: getBreadcrumbTitle(pathname) });
  }
  return segments;
}

function NavUnderline() {
  return (
    <span className="absolute left-1/2 bottom-[1px] -translate-x-1/2 h-[2px] w-4 rounded-full bg-primary-500" />
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
        className={`relative h-[36px] px-3 rounded-[8px] text-[13.5px] font-medium flex items-center gap-1.5 transition-all duration-150 ${
          active || open
            ? 'text-primary-700 bg-primary-50/60'
            : 'text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100/70'
        }`}
      >
        {group.label}
        <ChevronDown
          size={15}
          className={`transition-transform duration-150 ${open ? 'rotate-180' : ''} ${
            active || open ? 'text-primary-500' : 'text-neutral-500'
          }`}
        />
        {(active || open) && <NavUnderline />}
      </button>

      {open && (
        <div
          className="absolute left-0 top-full mt-2 z-50 w-60 min-w-[240px] glass-strong rounded-[14px] border border-white/40 shadow-overlay py-1.5 animate-slideDown"
          role="menu"
          aria-label={group.label}
        >
          {group.items.map((item, index) => {
            const itemActive = isPathActive(pathname, item);
            const prev = index > 0 ? group.items[index - 1] : undefined;
            const showSection = !!item.section && index > 0 && item.section !== prev?.section;
            return (
              <div key={item.path}>
                {showSection && (
                  <p className="px-3.5 pt-2 pb-0.5 text-[10px] font-semibold uppercase tracking-[0.14em] text-neutral-400">
                    {item.section}
                  </p>
                )}
                <button
                  ref={(el) => {
                    itemRefs.current[index] = el;
                  }}
                  type="button"
                  role="menuitem"
                  onClick={() => onNavigate(item.path)}
                  onKeyDown={(e) => handleItemKey(e, index)}
                  className={`w-full flex items-center gap-3 px-3.5 py-2 my-0.5 text-[13.5px] text-left rounded-[9px] transition-colors outline-none ${
                    itemActive
                      ? 'bg-primary-50/80 text-primary-700 font-medium'
                      : 'text-neutral-700 hover:bg-neutral-100/70 hover:text-neutral-900'
                  }`}
                >
                  <span className={`shrink-0 ${itemActive ? 'text-primary-500' : 'text-neutral-500'}`}>
                    <item.icon size={16} />
                  </span>
                  <span className="flex flex-col min-w-0">
                    <span className="truncate">{item.label}</span>
                    {item.description && (
                      <span className="text-[11.5px] text-neutral-400 truncate">{item.description}</span>
                    )}
                  </span>
                </button>
              </div>
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
      className={`relative h-[36px] px-3 rounded-[8px] text-[13.5px] font-medium flex items-center transition-all duration-150 ${
        active ? 'text-primary-700 bg-primary-50/60' : 'text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100/70'
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
  const { unreadCount } = useNotifications();
  const effectiveRole = useEffectiveRole();
  const navigate = useNavigate();
  const location = useLocation();

  const [openGroup, setOpenGroup] = useState<string | null>(null);
  const [profileOpen, setProfileOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [mobileCollapsed, setMobileCollapsed] = useState<Record<string, boolean>>({});
  const [collapse, setCollapse] = useState(0);

  const topNavRef = useRef<HTMLDivElement>(null);
  const profileRef = useRef<HTMLDivElement>(null);
  const profileButtonRef = useRef<HTMLButtonElement>(null);

  const role = (effectiveRole || 'STUDENT') as keyof typeof NAV_GROUPS;
  const groups = visibleGroups(NAV_GROUPS[role] || []);
  const breadcrumbs = getBreadcrumbs(location.pathname);
  const displayName = user?.name || (isPreviewing ? 'Preview' : 'User');

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

  useEffect(() => {
    if (!mobileOpen) return;
    const init: Record<string, boolean> = {};
    for (const group of groups) {
      if (!isGroupActive(location.pathname, group)) init[group.label] = true;
    }
    setMobileCollapsed(init);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mobileOpen]);

  useLayoutEffect(() => {
    const el = topNavRef.current;
    if (!el) return;
    const check = () => {
      const over = el.scrollWidth > el.clientWidth + 2;
      const roomy = el.scrollWidth <= el.clientWidth - 48;
      setCollapse((c) => {
        const maxC = Math.max(0, groups.length - 1);
        if (over && c < maxC) return c + 1;
        if (roomy && c > 0) return 0;
        return c;
      });
    };
    check();
    const ro = new ResizeObserver(check);
    ro.observe(el);
    window.addEventListener('resize', check);
    return () => {
      ro.disconnect();
      window.removeEventListener('resize', check);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groups.length]);

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

  const fit = Math.max(1, groups.length - collapse);
  const primaryGroups = groups.slice(0, fit);
  const moreGroups = groups.slice(fit);
  const moreItems: NavItem[] = moreGroups.flatMap((g) =>
    g.items.map((i) => ({
      ...i,
      section: g.items.length > 1 ? i.section ?? g.label : i.section,
    })),
  );
  const moreActive = moreItems.some((i) => isPathActive(location.pathname, i));

  const desktopNav =
    groups.length > 0 && (
      <nav
        ref={topNavRef}
        aria-label="Primary"
        className="hidden lg:flex items-center gap-0.5 ml-6 min-w-0"
      >
        {primaryGroups.map(renderGroup)}
        {moreGroups.length > 0 && (
          <GroupDropdown
            group={{ label: 'More', items: moreItems }}
            pathname={location.pathname}
            open={openGroup === 'More'}
            active={moreActive}
            onToggle={() => setOpenGroup(openGroup === 'More' ? null : 'More')}
            onClose={() => setOpenGroup(null)}
            onNavigate={handleNavigate}
          />
        )}
      </nav>
    );

  const profileMenuButton = (
    <button
      ref={profileButtonRef}
      type="button"
      onClick={() => {
        setProfileOpen((prev) => !prev);
        setOpenGroup(null);
      }}
      onKeyDown={(e) => {
        if (!profileOpen && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
          e.preventDefault();
          setProfileOpen(true);
          setOpenGroup(null);
        }
      }}
      aria-haspopup="true"
      aria-expanded={profileOpen}
      className={`relative flex items-center gap-2.5 rounded-[12px] px-2 py-1.5 transition-all duration-150 outline-none ${
        profileOpen ? 'bg-neutral-100/80' : 'hover:bg-neutral-100/70'
      }`}
    >
      <span className="relative">
        <Avatar name={displayName} size="sm" />
      </span>
      <div className="hidden sm:block text-left">
        <p className="text-[14px] font-semibold text-neutral-900 leading-tight max-w-[160px] truncate">{displayName}</p>
        <p className="text-[12px] text-neutral-500 leading-tight mt-0.5">{roleLabels[role]}</p>
      </div>
      <ChevronDown
        size={15}
        className={`hidden sm:block text-neutral-500 transition-transform duration-150 ${profileOpen ? 'rotate-180' : ''}`}
      />
    </button>
  );

  const profileMenu = (
    <AccountMenu
      displayName={displayName}
      email={user?.email}
      roleLabel={roleLabels[role]}
      onClose={() => {
        setProfileOpen(false);
        profileButtonRef.current?.focus();
      }}
      onMyProfile={() => handleNavigate('/profile')}
      onChangePassword={() => {
        closeMenus();
        navigate('/profile', { state: { security: true } });
      }}
      onLogout={handleLogout}
      triggerRef={profileButtonRef}
    />
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
            className="p-2 -mr-1 rounded-[10px] text-neutral-500 hover:text-neutral-700 hover:bg-neutral-100/80 transition-colors"
            aria-label="Close menu"
          >
            <X size={20} />
          </button>
        </div>

        <nav aria-label="Primary" className="flex-1 overflow-y-auto overflow-x-hidden px-3 py-4">
          {groups.map((group) => {
            const groupActive = isGroupActive(location.pathname, group);
            if (group.items.length === 1) {
              const item = group.items[0];
              const itemActive = isPathActive(location.pathname, item);
              return (
                <div key={group.label} className="mb-1.5">
                  <button
                    type="button"
                    onClick={() => handleNavigate(item.path)}
                    className={`w-full flex items-center gap-3 h-11 px-3.5 rounded-[10px] text-[14px] font-medium transition-colors ${
                      itemActive ? 'bg-primary-50/80 text-primary-700' : 'text-neutral-700 hover:bg-neutral-100/70'
                    }`}
                  >
                    <item.icon size={17} className={itemActive ? 'text-primary-500' : 'text-neutral-500'} />
                    {item.label}
                  </button>
                </div>
              );
            }
            const collapsed = !!mobileCollapsed[group.label];
            return (
              <div key={group.label} className="mb-1.5">
                <button
                  type="button"
                  onClick={() => setMobileCollapsed((prev) => ({ ...prev, [group.label]: !prev[group.label] }))}
                  aria-expanded={!collapsed}
                  className="w-full flex items-center justify-between h-11 px-3.5 rounded-[10px] text-[14px] font-semibold text-neutral-700 hover:bg-neutral-100/70 transition-colors"
                >
                  <span className={`${groupActive ? 'text-primary-700' : ''}`}>{group.label}</span>
                  <ChevronDown
                    size={16}
                    className={`text-neutral-400 transition-transform duration-150 ${collapsed ? '-rotate-90' : ''}`}
                  />
                </button>
                {!collapsed && (
                  <div className="space-y-0.5 mt-0.5 ml-3 pl-3 border-l border-neutral-200/70" role="group" aria-label={group.label}>
                    {group.items.map((item) => {
                      const itemActive = isPathActive(location.pathname, item);
                      return (
                        <button
                          key={item.path}
                          type="button"
                          onClick={() => handleNavigate(item.path)}
                          className={`w-full flex items-center gap-3 h-10 px-3.5 rounded-[10px] text-[14px] font-medium transition-colors ${
                            groupActive && itemActive
                              ? 'bg-primary-50/80 text-primary-700'
                              : 'text-neutral-700 hover:bg-neutral-100/70'
                          }`}
                        >
                          <item.icon size={16} className={groupActive && itemActive ? 'text-primary-500' : 'text-neutral-500'} />
                          {item.label}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </nav>

        <div className="border-t border-neutral-200/60 px-3 py-3 shrink-0">
          <button
            type="button"
            onClick={() => handleNavigate('/messages')}
            className="w-full relative flex items-center gap-3 h-11 px-3.5 rounded-[10px] text-[14px] font-medium text-neutral-700 hover:bg-neutral-100/70 transition-colors"
          >
            <span className="relative">
              <Mail size={17} className="text-neutral-500" />
              {unreadCount > 0 && (
                <span className="absolute top-0 -right-1 h-2 w-2 rounded-full bg-danger-500 ring-2 ring-white" />
              )}
            </span>
            Messages
            {unreadCount > 0 && (
              <span className="ml-auto h-5 min-w-5 px-1.5 rounded-full bg-danger-500 text-[11px] font-semibold text-white flex items-center justify-center">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>
          <button
            type="button"
            onClick={() => handleNavigate('/profile')}
            className="w-full flex items-center gap-3 h-11 px-3.5 rounded-[10px] text-[14px] font-medium text-neutral-700 hover:bg-neutral-100/70 transition-colors"
          >
            <User size={17} className="text-neutral-500" />
            My Profile
          </button>
          <button
            type="button"
            onClick={() => {
              closeMenus();
              setMobileOpen(false);
              navigate('/profile', { state: { security: true } });
            }}
            className="w-full flex items-center gap-3 h-11 px-3.5 rounded-[10px] text-[14px] font-medium text-neutral-700 hover:bg-neutral-100/70 transition-colors"
          >
            <Lock size={17} className="text-neutral-500" />
            Change Password
          </button>
          <div className="flex items-center gap-3 px-2 py-1.5 mt-1 border-t border-neutral-200/60 pt-3">
            <Avatar name={displayName} size="sm" />
            <div className="min-w-0 flex-1">
              <p className="text-[14px] font-semibold text-neutral-900 truncate leading-tight">{displayName}</p>
              <p className="text-[12px] text-neutral-500 leading-tight mt-0.5">{roleLabels[role]}</p>
            </div>
            <button
              type="button"
              onClick={handleLogout}
              className="p-2.5 rounded-[10px] text-neutral-500 hover:text-danger-600 hover:bg-danger-50/60 transition-colors"
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
        <div className="mx-auto w-full max-w-[1560px] h-full flex items-center justify-between px-4 sm:px-6 lg:px-10 gap-4">
          <div className="flex items-center min-w-0">{brand}</div>

          {desktopNav}

          <div className="flex items-center gap-1 shrink-0">
            {isPreviewing && (
              <span className="hidden sm:inline-flex rounded-full bg-accent-50 text-accent-600 px-2.5 py-0.5 text-[11px] font-semibold ring-1 ring-inset ring-accent-300/30 mr-1">
                Preview
              </span>
            )}
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              className="lg:hidden flex items-center justify-center h-9 w-9 rounded-[8px] text-neutral-500 hover:bg-neutral-100 hover:text-neutral-800 transition-colors"
              aria-label="Open menu"
            >
              <Menu size={21} />
            </button>
            <button
              type="button"
              onClick={() => handleNavigate('/messages')}
              aria-label={unreadCount > 0 ? `Messages, ${unreadCount} unread` : 'Messages'}
              title="Messages"
              className="relative flex items-center justify-center h-9 w-9 rounded-[8px] text-neutral-500 hover:bg-neutral-100 hover:text-neutral-800 transition-colors"
            >
              <Mail size={20} />
              {unreadCount > 0 && (
                <span
                  aria-hidden="true"
                  className="absolute top-1.5 right-1.5 h-2.5 w-2.5 rounded-full bg-danger-500 ring-2 ring-white"
                />
              )}
            </button>
            <div ref={profileRef} className="relative">
              {profileMenuButton}
              {profileOpen && profileMenu}
            </div>
          </div>
        </div>
      </header>

      {mobileMenu}

      <main className="flex-1 overflow-y-auto overflow-x-hidden">
        <div className="mx-auto w-full max-w-[1560px] px-4 sm:px-6 lg:px-10 py-5 sm:py-6 relative z-10">
          <div className="mb-4">
            <Breadcrumb segments={breadcrumbs} />
          </div>
          <Outlet />
        </div>
      </main>
    </div>
  );
}