import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type RefObject } from 'react';
import { User, Lock, LogOut } from 'lucide-react';

interface AccountMenuProps {
  displayName: string;
  email?: string;
  roleLabel: string;
  onClose: () => void;
  onMyProfile: () => void;
  onChangePassword: () => void;
  onLogout: () => void;
  triggerRef: RefObject<HTMLButtonElement | null>;
}

export default function AccountMenu({
  displayName,
  email,
  roleLabel,
  onClose,
  onMyProfile,
  onChangePassword,
  onLogout,
  triggerRef,
}: AccountMenuProps) {
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const [focusIndex, setFocusIndex] = useState(0);

  const actions = [
    { label: 'My Profile', icon: User, onClick: onMyProfile, danger: false },
    { label: 'Change Password', icon: Lock, onClick: onChangePassword, danger: false },
    { label: 'Logout', icon: LogOut, onClick: onLogout, danger: true },
  ];

  useEffect(() => {
    itemRefs.current[focusIndex]?.focus();
  }, [focusIndex]);

  const handleKey = (e: ReactKeyboardEvent<HTMLButtonElement>, index: number) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
      triggerRef.current?.focus();
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setFocusIndex((index + 1) % actions.length);
      return;
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      setFocusIndex((index - 1 + actions.length) % actions.length);
      return;
    }
    if (e.key === 'Home') {
      e.preventDefault();
      setFocusIndex(0);
      return;
    }
    if (e.key === 'End') {
      e.preventDefault();
      setFocusIndex(actions.length - 1);
    }
  };

  return (
    <div
      className="absolute right-0 top-full mt-2 z-50 w-60 glass-strong rounded-[14px] border border-white/40 shadow-overlay py-1.5 animate-slideDown"
      role="menu"
      aria-label="Account menu"
    >
      <div className="px-3.5 py-2.5 border-b border-neutral-100/60 mb-1">
        <p className="text-[14px] font-semibold text-neutral-900 truncate">{displayName}</p>
        <p className="text-[12px] text-neutral-500 truncate mt-0.5">{email || roleLabel}</p>
      </div>
      {actions.map((action, index) => {
        const Icon = action.icon;
        return (
          <button
            key={action.label}
            ref={(el) => {
              itemRefs.current[index] = el;
            }}
            type="button"
            role="menuitem"
            tabIndex={-1}
            onClick={action.onClick}
            onKeyDown={(e) => handleKey(e, index)}
            className={`w-full flex items-center gap-2.5 px-3.5 py-2.5 my-0.5 text-[14px] text-left rounded-[10px] transition-colors outline-none focus-visible:ring-2 focus-visible:ring-primary-400/60 ${
              action.danger
                ? 'text-danger-600 hover:bg-danger-50/60'
                : 'text-neutral-700 hover:bg-neutral-100/70'
            }`}
          >
            <Icon size={15} className={`shrink-0 ${action.danger ? '' : 'text-neutral-500'}`} />
            {action.label}
          </button>
        );
      })}
    </div>
  );
}
