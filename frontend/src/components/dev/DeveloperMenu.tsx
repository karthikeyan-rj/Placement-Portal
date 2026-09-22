import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useDevMode, type PreviewRole } from '../../context/DevModeContext';
import { LayoutDashboard, Users, Building2, ShieldCheck, GraduationCap, Briefcase, ClipboardList, MessageSquare, BarChart3, FileText, Wrench, X, LogOut } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

interface DevGroup {
  group: string;
  items: DevItem[];
}

interface DevItem {
  label: string;
  path: string;
  role: PreviewRole;
  icon: LucideIcon;
}

const DEV_ITEMS: DevGroup[] = [
  {
    group: 'Dashboards',
    items: [
      { label: 'PO Dashboard', path: '/dashboard', role: 'PO', icon: LayoutDashboard },
      { label: 'PC Dashboard', path: '/dashboard', role: 'PC', icon: LayoutDashboard },
      { label: 'PR Dashboard', path: '/dashboard', role: 'PR', icon: LayoutDashboard },
      { label: 'Student Dashboard', path: '/dashboard', role: 'STUDENT', icon: LayoutDashboard },
    ],
  },
  {
    group: 'Pages',
    items: [
      { label: 'Students', path: '/students', role: 'PC', icon: Users },
      { label: 'Departments', path: '/departments', role: 'PO', icon: Building2 },
      { label: 'PC Management', path: '/pc-management', role: 'PO', icon: ShieldCheck },
      { label: 'PR Management', path: '/pr-management', role: 'PO', icon: GraduationCap },
      { label: 'Companies', path: '/companies', role: 'PC', icon: Briefcase },
      { label: 'Placement Drives', path: '/placement-drives', role: 'PC', icon: ClipboardList },
      { label: 'Messages', path: '/messages', role: 'PR', icon: MessageSquare },
      { label: 'Reports', path: '/reports', role: 'PO', icon: BarChart3 },
      { label: 'Audit Logs', path: '/audit-logs', role: 'PO', icon: FileText },
      { label: 'Profiles', path: '/profile', role: 'STUDENT', icon: Users },
    ],
  },
];

const ROLE_TABS: { label: string; value: PreviewRole | null }[] = [
  { label: 'None', value: null },
  { label: 'PO', value: 'PO' },
  { label: 'PC', value: 'PC' },
  { label: 'PR', value: 'PR' },
  { label: 'Student', value: 'STUDENT' },
];

export default function DeveloperMenu() {
  const { isAuthenticated } = useAuth();
  const { devMode, previewRole, setPreviewRole, exitPreview, isPreviewing } = useDevMode();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, []);

  if (!devMode || isAuthenticated) return null;

  const grouped = DEV_ITEMS;

  const handleItem = (item: DevItem) => {
    setPreviewRole(item.role);
    setOpen(false);
    navigate(item.path);
  };

  return (
    <div ref={ref} className="fixed bottom-5 right-5 z-50">
      {/* Floating trigger */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="Developer Mode"
        aria-expanded={open}
        className={`flex items-center gap-2 rounded-full px-4 py-2.5 text-[13px] font-semibold shadow-overlay transition-colors ${
          isPreviewing
            ? 'bg-emerald-600 text-white hover:bg-emerald-700'
            : 'bg-neutral-900 text-white hover:bg-neutral-800'
        }`}
      >
        <Wrench size={16} />
        Dev Mode
        {isPreviewing && <span className="rounded-full bg-white/20 px-2 py-0.5 text-[11px]">{previewRole}</span>}
        {open ? <X size={15} /> : null}
      </button>

      {open && (
        <div className="absolute bottom-14 right-0 w-[300px] max-h-[70vh] overflow-y-auto bg-white rounded-[14px] border border-neutral-200 shadow-overlay p-4 animate-fadeIn">
          <div className="flex items-center justify-between mb-3">
            <p className="text-[13px] font-semibold text-neutral-900">Developer Preview</p>
            {isPreviewing && (
              <button
                type="button"
                onClick={() => {
                  exitPreview();
                  setOpen(false);
                  navigate('/');
                }}
                className="inline-flex items-center gap-1 text-[12px] font-medium text-danger-600 hover:text-danger-700 transition-colors"
              >
                <LogOut size={13} /> Exit preview
              </button>
            )}
          </div>

          {/* Role selector */}
          <p className="text-[11px] font-semibold uppercase tracking-[0.1em] text-neutral-500 mb-1.5">
            Preview role
          </p>
          <div className="flex flex-wrap gap-1.5 mb-4">
            {ROLE_TABS.map((tab) => (
              <button
                key={tab.label}
                type="button"
                onClick={() => setPreviewRole(tab.value)}
                className={`rounded-full px-2.5 py-1 text-[12px] font-medium transition-colors ${
                  previewRole === tab.value
                    ? 'bg-primary-600 text-white'
                    : 'bg-neutral-100 text-neutral-600 hover:bg-neutral-200'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Page links */}
          <p className="text-[11px] font-semibold uppercase tracking-[0.1em] text-neutral-500 mb-1.5">
            Preview pages
          </p>
          {grouped.map((group) => (
            <div key={group.group} className="mb-3 last:mb-0">
              <p className="text-[11px] font-medium text-neutral-500 mb-1">{group.group}</p>
              <div className="space-y-0.5">
                {group.items.map((item) => {
                  const Icon = item.icon;
                  const isActive = previewRole === item.role;
                  return (
                    <button
                      key={`${item.label}-${item.role}`}
                      type="button"
                      onClick={() => handleItem(item)}
                      className="w-full flex items-center gap-2.5 px-2 py-1.5 rounded-[8px] text-[13px] text-left text-neutral-700 hover:bg-neutral-100 transition-colors"
                    >
                      <Icon size={15} className="shrink-0 text-neutral-500" />
                      <span className="flex-1">{item.label}</span>
                      <span
                        className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
                          isActive ? 'bg-emerald-100 text-emerald-700' : 'bg-neutral-100 text-neutral-500'
                        }`}
                      >
                        {item.role}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          ))}

          <p className="mt-3 text-[11px] leading-relaxed text-neutral-500 border-t border-neutral-100 pt-3">
            Frontend-only preview. No backend auth is bypassed — pages load with their normal
            loading/error/empty states when no valid session exists.
          </p>
        </div>
      )}
    </div>
  );
}
