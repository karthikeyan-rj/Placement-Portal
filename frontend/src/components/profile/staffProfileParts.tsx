import type { ReactNode } from 'react';
import { AlertCircle } from 'lucide-react';

/**
 * Presentation helpers shared by the PC and PO profile pages.
 *
 * These are deliberately presentation-only: no data fetching, no saving and no
 * knowledge of which role is viewing, so the same pieces serve both pages.
 */

export function isBlank(v: unknown): boolean {
  return v === null || v === undefined || v === '';
}

/** Renders a readable empty value instead of a blank space or a fake placeholder. */
export function ValueOrEmpty({ value, empty = 'Not added' }: { value: string | null | undefined; empty?: string }) {
  if (isBlank(value)) {
    return <span className="text-neutral-400 text-[13.5px] font-normal">{empty}</span>;
  }
  return <>{value}</>;
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <p className="text-[11.5px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">{label}</p>
      <div className="min-h-[20px] break-words text-[14.5px] font-medium text-neutral-800">
        {isBlank(children) ? <span className="text-neutral-400 font-normal text-[13.5px]">Not added</span> : children}
      </div>
    </div>
  );
}

export function SectionCard({
  title,
  icon,
  children,
  className = '',
  action,
}: {
  title: string;
  icon: ReactNode;
  children: ReactNode;
  className?: string;
  action?: ReactNode;
}) {
  return (
    <div className={`bg-white rounded-[14px] border border-neutral-200/80 shadow-soft p-4 sm:p-5 ${className}`}>
      <div className="flex items-center gap-2.5 mb-3.5">
        <span className="shrink-0 flex items-center justify-center w-8 h-8 rounded-[9px] bg-primary-50 text-primary-500">
          {icon}
        </span>
        <h3 className="text-[15px] font-semibold text-neutral-900 truncate">{title}</h3>
        {action ? <div className="ml-auto shrink-0">{action}</div> : null}
      </div>
      {children}
    </div>
  );
}

/** Read-only row of account facts. Nothing here is editable on this page. */
export function AccountField({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="min-w-0">
      <p className="text-[11.5px] font-semibold uppercase tracking-wider text-neutral-500 mb-1">{label}</p>
      <p className="text-[14.5px] font-medium text-neutral-800 break-words">
        {isBlank(value) ? <span className="text-neutral-400 font-normal text-[13.5px]">Not added</span> : value}
      </p>
    </div>
  );
}

export function ErrorPanel({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft p-10 text-center animate-fadeIn">
      <span className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-danger-50 text-danger-500 mb-3">
        <AlertCircle size={22} />
      </span>
      <p className="text-[15px] text-neutral-500">{message}</p>
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="mt-4 px-4 py-2 rounded-[10px] border border-neutral-300 text-[14px] font-medium text-neutral-700 hover:bg-neutral-50"
        >
          Retry
        </button>
      ) : null}
    </div>
  );
}
