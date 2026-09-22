import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  Calendar,
  ChevronRight,
  Building2,
  IndianRupee,
  MessagesSquare,
  Contact,
  Briefcase,
  AlertCircle,
  RotateCcw,
  type LucideIcon,
} from 'lucide-react';
import { Avatar, Badge, formatStatus, formatInterviewStatus, interviewStatusVariant } from '../ui';
import type {
  PlacementDrive,
  ContactRequest,
  Message,
  StudentInterview,
} from '../../types';

/* ── Status → Badge variant ─────────────────────────────────── */

const driveStatusVariant = (s: string): 'warning' | 'success' | 'danger' | 'info' | 'neutral' => {
  switch (s) {
    case 'UPCOMING':
      return 'info';
    case 'REGISTRATION_OPEN':
      return 'success';
    case 'ONGOING':
      return 'warning';
    case 'CANCELLED':
      return 'danger';
    case 'COMPLETED':
    case 'REGISTRATION_CLOSED':
      return 'neutral';
    default:
      return 'neutral';
  }
};

const requestStatusVariant = (s: string): 'warning' | 'success' | 'neutral' => {
  switch (s) {
    case 'PENDING':
      return 'warning';
    case 'APPROVED':
      return 'success';
    default:
      return 'neutral';
  }
};

/* ── WelcomeHeader ──────────────────────────────────────────── */

export function WelcomeHeader({
  name,
  roleLabel,
  meta,
  subtitle,
}: {
  name: string;
  roleLabel: string;
  meta?: string;
  subtitle?: string;
}) {
  return (
    <div className="flex items-center gap-3.5 flex-wrap animate-fadeIn">
      <Avatar name={name} size="lg" className="shrink-0" />
      <div className="min-w-0">
        <h1 className="text-[22px] sm:text-[26px] font-bold tracking-[-0.02em] text-neutral-900 leading-tight">
          Welcome back, {name}
        </h1>
        <p className="text-[13px] text-text-secondary mt-0.5">
          <span className="font-semibold text-neutral-700">{roleLabel}</span>
          {meta ? <span> · {meta}</span> : null}
        </p>
        {subtitle && <p className="text-[13px] text-text-secondary mt-0.5">{subtitle}</p>}
      </div>
    </div>
  );
}

/* ── MetricCard ─────────────────────────────────────────────── */

interface MetricCardProps {
  label: string;
  value: string | number;
  icon: LucideIcon;
  sub?: ReactNode;
  href?: string;
}

export function MetricCard({ label, value, icon: Icon, sub, href }: MetricCardProps) {
  const inner = (
    <div className="h-full bg-white rounded-[14px] border border-neutral-200/80 p-4 shadow-soft transition-colors duration-150 group-hover:border-primary-200">
      <div className="flex items-center gap-2.5 min-w-0">
        <span className="shrink-0 flex items-center justify-center w-7 h-7 rounded-[8px] bg-primary-50 text-primary-600">
          <Icon size={15} strokeWidth={2} />
        </span>
        <p className="text-[12.5px] font-medium text-text-secondary truncate">{label}</p>
      </div>
      <p className="mt-2.5 text-[26px] font-bold leading-none text-neutral-900 tracking-tight">
        {value}
      </p>
      {sub && <div className="mt-1.5 text-[12px] text-text-secondary truncate">{sub}</div>}
    </div>
  );

  if (href) {
    return (
      <Link to={href} className="group block min-w-0 h-full">
        {inner}
      </Link>
    );
  }
  return inner;
}

/* ── DashboardSection ───────────────────────────────────────── */

interface DashboardSectionProps {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function DashboardSection({
  title,
  subtitle,
  action,
  children,
  className = '',
}: DashboardSectionProps) {
  return (
    <section
      className={`bg-white rounded-[14px] border border-neutral-200/80 shadow-soft overflow-hidden ${className}`}
    >
      <header className="flex items-center justify-between gap-3 px-4 py-3.5 border-b border-neutral-100">
        <div className="min-w-0">
          <h2 className="text-[15px] font-semibold text-neutral-900 tracking-tight">{title}</h2>
          {subtitle && <p className="text-[12.5px] text-text-secondary mt-0.5">{subtitle}</p>}
        </div>
        {action && <div className="shrink-0">{action}</div>}
      </header>
      <div>{children}</div>
    </section>
  );
}

export function SectionAction({ label, to }: { label: string; to: string }) {
  return (
    <Link
      to={to}
      className="inline-flex items-center gap-0.5 text-[13px] font-semibold text-primary-600 hover:text-primary-700 transition-colors"
    >
      {label}
      <ChevronRight size={14} />
    </Link>
  );
}

/* ── Row list items ─────────────────────────────────────────── */

export function DriveRow({ drive, to }: { drive: PlacementDrive; to?: string }) {
  const content = (
    <div className="flex items-center gap-3 px-4 py-3.5 border-b border-neutral-100 last:border-0">
      <span className="shrink-0 flex items-center justify-center w-9 h-9 rounded-[9px] bg-primary-50 text-primary-600">
        <Building2 size={18} />
      </span>
      <div className="flex-1 min-w-0">
        <p className="text-[14px] font-semibold text-neutral-900 leading-tight truncate">
          {drive.companyName} · {drive.jobRole}
        </p>
        <div className="mt-1 flex items-center gap-4 flex-wrap text-[13px] text-text-secondary">
          {drive.driveDate && (
            <span className="flex items-center gap-1">
              <Calendar size={13} className="text-neutral-500" />
              {new Date(drive.driveDate).toLocaleDateString('en-IN', {
                day: 'numeric',
                month: 'short',
                year: 'numeric',
              })}
            </span>
          )}
          {drive.packageLpa != null && (
            <span className="flex items-center gap-1">
              <IndianRupee size={13} className="text-neutral-500" />
              {drive.packageLpa} LPA
            </span>
          )}
        </div>
      </div>
      <Badge variant={driveStatusVariant(drive.status)} size="sm">
        {formatStatus(drive.status)}
      </Badge>
    </div>
  );

  if (to) {
    return (
      <Link to={to} className="block hover:bg-neutral-50/60 transition-colors">
        {content}
      </Link>
    );
  }
  return content;
}

export function ContactRequestRow({ request, to }: { request: ContactRequest; to?: string }) {
  const content = (
    <div className="flex items-center gap-3 px-4 py-3.5 border-b border-neutral-100 last:border-0">
      <span className="shrink-0 flex items-center justify-center w-9 h-9 rounded-[9px] bg-accent-50 text-accent-600">
        <Contact size={18} />
      </span>
      <div className="flex-1 min-w-0">
        <p className="text-[14px] font-semibold text-neutral-900 truncate">{request.subject}</p>
        <p className="text-[13px] text-text-secondary mt-0.5 truncate">
          {request.studentName} · {request.registerNumber}
        </p>
      </div>
      <Badge variant={requestStatusVariant(request.status)} size="sm">
        {formatStatus(request.status)}
      </Badge>
    </div>
  );

  if (to) {
    return (
      <Link to={to} className="block hover:bg-neutral-50/60 transition-colors">
        {content}
      </Link>
    );
  }
  return content;
}

export function MessageRow({ message, to }: { message: Message; to?: string }) {
  const content = (
    <div className="px-4 py-3.5 border-b border-neutral-100 last:border-0">
      <div className="flex items-center justify-between gap-3">
        <p className="text-[14px] font-semibold text-neutral-900 truncate min-w-0">{message.title}</p>
        <span className="text-[12px] text-neutral-500 shrink-0">
          {new Date(message.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
        </span>
      </div>
      <p className="text-[13px] text-text-secondary mt-1 line-clamp-2 leading-relaxed">{message.content}</p>
      <p className="text-[12.5px] text-neutral-500 mt-1.5 flex items-center gap-1.5">
        <MessagesSquare size={12} />
        {message.senderName} · {message.totalRecipients}{' '}
        {message.totalRecipients === 1 ? 'recipient' : 'recipients'}
      </p>
    </div>
  );

  if (to) {
    return (
      <Link to={to} className="block hover:bg-neutral-50/60 transition-colors">
        {content}
      </Link>
    );
  }
  return content;
}

export function InterviewRow({ interview, to }: { interview: StudentInterview; to?: string }) {
  const content = (
    <div className="flex items-center gap-3 px-4 py-3.5 border-b border-neutral-100 last:border-0">
      <span className="shrink-0 flex items-center justify-center w-9 h-9 rounded-[9px] bg-neutral-100 text-neutral-500">
        <Briefcase size={18} />
      </span>
      <div className="flex-1 min-w-0">
        <p className="text-[14px] font-semibold text-neutral-900 truncate">
          {interview.companyName} · {interview.driveJobRole}
        </p>
        <p className="text-[13px] text-text-secondary mt-0.5 truncate">
          Round: {interview.roundName}
          {interview.interviewDate
            ? ` · ${new Date(interview.interviewDate).toLocaleDateString('en-IN', {
                day: 'numeric',
                month: 'short',
                year: 'numeric',
              })}`
            : ''}
        </p>
      </div>
      <Badge variant={interviewStatusVariant(interview.status)} size="sm">
        {formatInterviewStatus(interview.status)}
      </Badge>
    </div>
  );

  if (to) {
    return (
      <Link to={to} className="block hover:bg-neutral-50/60 transition-colors">
        {content}
      </Link>
    );
  }
  return content;
}

/* ── States ─────────────────────────────────────────────────── */

export function DashboardEmpty({
  icon: Icon,
  title,
  description,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
}) {
  return (
    <div className="py-9 flex flex-col items-center justify-center text-center px-6">
      <span className="flex items-center justify-center w-9 h-9 rounded-full bg-neutral-100 text-neutral-500">
        <Icon size={17} />
      </span>
      <p className="text-[13.5px] font-medium text-neutral-700 mt-3">{title}</p>
      {description && (
        <p className="text-[12.5px] text-text-secondary mt-1 max-w-sm leading-relaxed">{description}</p>
      )}
    </div>
  );
}

export function DashboardError({
  title = 'Unable to load data',
  description,
  onRetry,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
}) {
  return (
    <div className="m-3 rounded-[10px] border border-danger-500/10 bg-danger-50/60 p-3.5 flex items-start gap-3">
      <span className="shrink-0 flex items-center justify-center w-8 h-8 rounded-[9px] bg-white text-danger-500 ring-1 ring-inset ring-danger-500/15">
        <AlertCircle size={15} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-[13px] font-semibold text-danger-700 leading-snug">{title}</p>
        {description && (
          <p className="text-[12.5px] text-danger-600/80 mt-1 leading-relaxed">{description}</p>
        )}
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="mt-2.5 inline-flex items-center gap-1.5 text-[12.5px] font-semibold text-danger-700 hover:text-danger-600 transition-colors"
          >
            <RotateCcw size={12} />
            Try Again
          </button>
        )}
      </div>
    </div>
  );
}

export function DashboardRowsSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="divide-y divide-neutral-100">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 px-4 py-3.5">
          <div className="w-9 h-9 rounded-[9px] bg-neutral-100 animate-pulse shrink-0" />
          <div className="flex-1 space-y-2">
            <div className="h-3.5 bg-neutral-100 animate-pulse rounded-full w-1/2" />
            <div className="h-2.5 bg-neutral-100 animate-pulse rounded-full w-2/5" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function MetricsSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="bg-white rounded-[14px] border border-neutral-200/80 p-4 shadow-soft">
          <div className="flex items-center gap-2.5">
            <span className="shrink-0 w-7 h-7 rounded-[8px] bg-neutral-100 animate-pulse" />
            <span className="h-3 bg-neutral-100 animate-pulse rounded-full w-24" />
          </div>
          <div className="mt-2.5 h-7 bg-neutral-100 animate-pulse rounded-lg w-16" />
          <div className="mt-1.5 h-3 bg-neutral-100 animate-pulse rounded-full w-28" />
        </div>
      ))}
    </div>
  );
}