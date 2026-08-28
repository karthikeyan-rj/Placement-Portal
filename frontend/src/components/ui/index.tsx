import { useState, useRef, useEffect, type ReactNode } from 'react';
import {
  X,
  ChevronLeft,
  ChevronRight,
  MoreHorizontal,
  Search,
  AlertCircle,
  RefreshCw,
  Eye,
  EyeOff,
} from 'lucide-react';
import toast from 'react-hot-toast';

/* ═══════════════════════════════════════════════════════════════
   Button
   ═══════════════════════════════════════════════════════════════ */

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline-danger';
type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-primary-500 text-white hover:bg-primary-600 active:bg-primary-700 shadow-soft',
  secondary:
    'bg-white text-neutral-700 border border-border hover:bg-neutral-50 hover:text-neutral-900 active:bg-neutral-100',
  ghost:
    'bg-transparent text-neutral-600 hover:bg-neutral-100 hover:text-neutral-800 active:bg-neutral-200',
  danger:
    'bg-danger-500 text-white hover:bg-danger-600 active:bg-danger-700 shadow-soft',
  'outline-danger':
    'bg-white text-danger-600 border border-danger-200 hover:bg-danger-50 active:bg-danger-100',
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'h-9 px-3.5 text-[13px] font-medium gap-1.5',
  md: 'h-[44px] px-5 text-[14px] font-semibold gap-2',
  lg: 'h-[48px] px-7 text-[15px] font-semibold gap-2',
};

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  className = '',
  disabled,
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      className={`inline-flex items-center justify-center rounded-[8px] transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed disabled:shadow-none whitespace-nowrap select-none focus-visible:ring-[3px] focus-visible:ring-primary/25 focus-visible:outline-none ${
        !disabled && !loading ? 'active:scale-[0.985]' : ''
      } ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
      disabled={disabled || loading}
      {...props}
    >
      {loading && (
        <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      )}
      {children}
    </button>
  );
}

/* ═══════════════════════════════════════════════════════════════
   IconButton
   ═══════════════════════════════════════════════════════════════ */

interface IconButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  label: string;
  children: ReactNode;
  active?: boolean;
}

export function IconButton({ label, children, active, className = '', ...props }: IconButtonProps) {
  return (
    <button
      title={label}
      aria-label={label}
      className={`inline-flex items-center justify-center h-9 w-9 rounded-[8px] transition-colors duration-150 ${
        active
          ? 'text-primary-600 bg-primary-50'
          : 'text-neutral-400 hover:text-neutral-700 hover:bg-neutral-100'
      } ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

/* ═══════════════════════════════════════════════════════════════
   Field wrapper + Input / Select / Textarea
   Shared: no tiny uppercase labels => 14px, 500. Controlled H-pad.
   ═══════════════════════════════════════════════════════════════ */

function FieldLabel({
  htmlFor,
  children,
  required,
}: {
  htmlFor?: string;
  children: ReactNode;
  required?: boolean;
}) {
  return (
    <label
      htmlFor={htmlFor}
      className="block text-[14px] font-medium text-neutral-800 mb-2"
    >
      {children}
      {required && <span className="text-danger-500 ml-0.5">*</span>}
    </label>
  );
}

const controlBase =
  'w-full h-[46px] px-4 text-[15px] border rounded-[8px] bg-white text-neutral-900 ' +
  'placeholder:text-neutral-400 focus:outline-none focus:border-primary-500 ' +
  'focus:ring-[3px] focus:ring-primary/15 hover:border-neutral-300 ' +
  'transition-colors duration-150 disabled:bg-neutral-50 disabled:text-neutral-500 disabled:cursor-not-allowed';

const controlError = 'border-danger-500 focus:border-danger-500 focus:ring-danger/15';

function FieldShell({
  label,
  error,
  required,
  controlId,
  children,
}: {
  label?: string;
  error?: string;
  required?: boolean;
  controlId?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col">
      {label && (
        <FieldLabel htmlFor={controlId} required={required}>
          {label}
        </FieldLabel>
      )}
      {children}
      {error && <p className="mt-1.5 text-[13px] text-danger-600">{error}</p>}
    </div>
  );
}

/* ── Input ───────────────────────────────────────────────────── */

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  icon?: ReactNode;
}

export function Input({
  label,
  error,
  icon,
  required,
  className = '',
  id,
  type = 'text',
  ...props
}: InputProps) {
  const [showPassword, setShowPassword] = useState(false);
  const isPassword = type === 'password';
  const inputType = isPassword ? (showPassword ? 'text' : 'password') : type;
  const inputId = id || label?.toLowerCase().replace(/\s+/g, '-');

  return (
    <FieldShell label={label} error={error} required={required} controlId={inputId}>
      <div className="relative">
        {icon && (
          <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-neutral-400">
            {icon}
          </div>
        )}
        <input
          id={inputId}
          required={required}
          type={inputType}
          className={`${controlBase} ${icon ? 'pl-11' : ''} ${isPassword ? 'pr-12' : ''} ${
            error ? controlError : ''
          } ${className}`}
          {...props}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            className="absolute inset-y-0 right-0 pr-4 flex items-center text-neutral-400 hover:text-neutral-600 transition-colors"
            tabIndex={-1}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
          >
            {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
        )}
      </div>
    </FieldShell>
  );
}

/* ── SearchInput ─────────────────────────────────────────────── */

interface SearchInputProps extends Omit<InputProps, 'icon'> {
  onSearch?: (value: string) => void;
}

export function SearchInput({ className = '', ...props }: SearchInputProps) {
  return (
    <Input
      icon={<Search size={18} className="text-neutral-400" />}
      placeholder="Search..."
      className={className}
      {...props}
    />
  );
}

/* ── Select ──────────────────────────────────────────────────── */

interface SelectOption {
  label: string;
  value: string | number;
}

interface SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'children'> {
  label?: string;
  options: SelectOption[];
  placeholder?: string;
  error?: string;
}

export function Select({
  label,
  options,
  placeholder,
  error,
  required,
  className = '',
  id,
  ...props
}: SelectProps) {
  const selectId = id || label?.toLowerCase().replace(/\s+/g, '-');
  return (
    <FieldShell label={label} error={error} required={required} controlId={selectId}>
      <select
        id={selectId}
        required={required}
        className={`${controlBase} appearance-none pr-10 bg-no-repeat bg-[right_14px_center] ${
          error ? controlError : ''
        } ${className}`}
        style={{
          backgroundImage:
            "url(\"data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%235B6470' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")",
        }}
        {...props}
      >
        {placeholder && (
          <option value="" disabled>
            {placeholder}
          </option>
        )}
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </FieldShell>
  );
}

/* ── Textarea ────────────────────────────────────────────────── */

interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
}

export function Textarea({ label, error, required, className = '', id, ...props }: TextareaProps) {
  const textareaId = id || label?.toLowerCase().replace(/\s+/g, '-');
  return (
    <FieldShell label={label} error={error} required={required} controlId={textareaId}>
      <textarea
        id={textareaId}
        required={required}
        className={`w-full px-4 py-3 text-[15px] border rounded-[8px] bg-white text-neutral-900 placeholder:text-neutral-400 focus:outline-none focus:border-primary-500 focus:ring-[3px] focus:ring-primary/15 hover:border-neutral-300 transition-colors duration-150 resize-y min-h-[120px] leading-[1.55] ${
          error ? controlError : ''
        } ${className}`}
        {...props}
      />
    </FieldShell>
  );
}

/* ═══════════════════════════════════════════════════════════════
   Badge
   ═══════════════════════════════════════════════════════════════ */

type BadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'teal' | 'department';
type BadgeSize = 'sm' | 'md';

interface BadgeProps {
  children: ReactNode;
  variant?: BadgeVariant;
  size?: BadgeSize;
  className?: string;
  dot?: boolean;
}

const badgeClasses: Record<BadgeVariant, string> = {
  success: 'bg-success-50 text-success-700 ring-success-600/15',
  warning: 'bg-warning-50 text-warning-700 ring-warning-600/20',
  danger: 'bg-danger-50 text-danger-700 ring-danger-600/20',
  info: 'bg-info-50 text-info-600 ring-info-600/20',
  neutral: 'bg-neutral-100 text-neutral-600 ring-neutral-500/15',
  teal: 'bg-primary-50 text-primary-700 ring-primary-600/20',
  department: 'bg-info-100 text-brand-navy ring-info-500/20',
};

const dotColors: Record<BadgeVariant, string> = {
  success: 'bg-success-500',
  warning: 'bg-warning-500',
  danger: 'bg-danger-500',
  info: 'bg-info-500',
  neutral: 'bg-neutral-400',
  teal: 'bg-primary-500',
  department: 'bg-brand-navy',
};

const badgeSizes: Record<BadgeSize, string> = {
  sm: 'px-2 py-0.5 text-[12px] gap-1',
  md: 'px-2.5 py-1 text-[13px] font-medium gap-1.5',
};

export function Badge({
  children,
  variant = 'neutral',
  size = 'md',
  className = '',
  dot = false,
}: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full font-medium ring-1 ring-inset ${badgeClasses[variant]} ${badgeSizes[size]} ${className}`}
    >
      {dot && <span className={`w-1.5 h-1.5 rounded-full ${dotColors[variant]}`} />}
      {children}
    </span>
  );
}

/* ═══════════════════════════════════════════════════════════════
   Card
   ═══════════════════════════════════════════════════════════════ */

interface CardProps {
  children: ReactNode;
  className?: string;
  padding?: 'none' | 'sm' | 'md' | 'lg';
  header?: ReactNode;
  footer?: ReactNode;
  title?: ReactNode;
  subtitle?: string;
  icon?: ReactNode;
  action?: ReactNode;
}

const paddingClasses = {
  none: '',
  sm: 'p-4',
  md: 'p-5',
  lg: 'p-6',
};

export function Card({
  children,
  className = '',
  padding = 'md',
  header,
  footer,
  title,
  subtitle,
  icon,
  action,
}: CardProps) {
  const hasHeader = header !== undefined || title !== undefined;
  return (
    <div className={`bg-white rounded-[12px] border border-border shadow-card ${className}`}>
      {hasHeader && (
        <div className="flex items-center justify-between gap-3 px-5 py-4 border-b border-neutral-100">
          {header}
          {title !== undefined && (
            <div className="flex items-center gap-3 min-w-0">
              {icon && <span className="shrink-0 text-primary-600">{icon}</span>}
              <div className="min-w-0">
                <h3 className="text-[16px] font-semibold text-neutral-900 truncate">
                  {title}
                </h3>
                {subtitle && (
                  <p className="text-[13px] text-neutral-500 mt-0.5 truncate">{subtitle}</p>
                )}
              </div>
            </div>
          )}
          {action && <div className="shrink-0">{action}</div>}
        </div>
      )}
      <div className={paddingClasses[padding]}>{children}</div>
      {footer && (
        <div className="px-5 py-3.5 border-t border-neutral-100 bg-neutral-50/60 rounded-b-[12px]">
          {footer}
        </div>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
   StatCard — neutral white, small brand indicator
   ═══════════════════════════════════════════════════════════════ */

interface StatCardProps {
  label: string;
  value: number | string;
  icon?: ReactNode;
  sub?: ReactNode;
  accent?: 'teal' | 'info' | 'navy' | 'none';
}

const statAccent: Record<string, string> = {
  teal: 'bg-primary-50 text-primary-600',
  info: 'bg-info-50 text-info-500',
  navy: 'bg-brand-navy-50 text-brand-navy',
  none: 'bg-neutral-100 text-neutral-600',
};

export function StatCard({ label, value, icon, sub, accent = 'none' }: StatCardProps) {
  const chip = statAccent[accent] || statAccent.none;
  return (
    <div className="bg-white rounded-[12px] border border-border shadow-card p-5 flex flex-col gap-4 transition-shadow duration-150 hover:shadow-raised">
      <div className="flex items-center justify-between gap-3">
        <p className="text-[13px] font-medium text-neutral-500 leading-tight">{label}</p>
        {icon && (
          <span className={`shrink-0 flex items-center justify-center w-9 h-9 rounded-[8px] ${chip}`}>
            {icon}
          </span>
        )}
      </div>
      <p className="text-[30px] font-bold leading-none text-neutral-900 tracking-tight">
        {typeof value === 'number' ? value.toLocaleString() : value}
      </p>
      {sub && <div className="text-[13px] text-neutral-500 flex items-center gap-1.5 -mt-2">{sub}</div>}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
   Modal
   ═══════════════════════════════════════════════════════════════ */

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  actions?: ReactNode;
}

const modalSizeClasses: Record<string, string> = {
  sm: 'max-w-md',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
};

export function Modal({
  isOpen,
  onClose,
  title,
  description,
  children,
  size = 'md',
  actions,
}: ModalProps) {
  useEffect(() => {
    if (isOpen) {
      const handleEsc = (e: KeyboardEvent) => {
        if (e.key === 'Escape') onClose();
      };
      document.addEventListener('keydown', handleEsc);
      document.body.style.overflow = 'hidden';
      return () => {
        document.removeEventListener('keydown', handleEsc);
        document.body.style.overflow = '';
      };
    }
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-brand-navy/50" onClick={onClose} />
      <div
        className={`relative bg-white rounded-[16px] border border-neutral-200/60 shadow-overlay w-full ${modalSizeClasses[size]} animate-fadeInScale`}
      >
        <div className="flex items-start justify-between px-6 py-5 border-b border-neutral-100">
          <div className="pr-4">
            <h2 className="text-[20px] font-semibold text-neutral-900 leading-tight">{title}</h2>
            {description && (
              <p className="mt-1 text-[14px] text-neutral-500 leading-relaxed">{description}</p>
            )}
          </div>
          <button
            onClick={onClose}
            className="p-1.5 -m-1 rounded-[8px] text-neutral-400 hover:text-neutral-700 hover:bg-neutral-100 transition-colors"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-6 max-h-[70vh] overflow-y-auto">{children}</div>
        {actions && (
          <div className="px-6 py-4 border-t border-neutral-100 flex items-center justify-end gap-3 bg-neutral-50/60 rounded-b-[16px]">
            {actions}
          </div>
        )}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
   DataTable
   ═══════════════════════════════════════════════════════════════ */

interface Column<T> {
  key: string;
  label: string;
  render?: (item: T, index: number) => ReactNode;
  className?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  rowKey: (item: T) => string | number;
  onRowClick?: (item: T) => void;
  emptyMessage?: string;
  emptyIcon?: ReactNode;
  loading?: boolean;
}

export function DataTable<T>({
  columns,
  data,
  rowKey,
  onRowClick,
  emptyMessage = 'No data found',
  emptyIcon,
  loading = false,
}: DataTableProps<T>) {
  if (loading) {
    return (
      <div className="bg-white rounded-[12px] border border-border overflow-hidden">
        <div className="px-5 py-4 border-b border-neutral-100 flex gap-4">
          {columns.map((col) => (
            <Skeleton key={col.key} className="h-3.5 flex-1" />
          ))}
        </div>
        {Array.from({ length: 5 }).map((_, rowIdx) => (
          <div
            key={rowIdx}
            className="px-5 py-6 flex gap-4 border-b border-neutral-100 last:border-0"
          >
            {columns.map((col) => (
              <Skeleton key={col.key} className="h-3.5 flex-1" />
            ))}
          </div>
        ))}
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="bg-white rounded-[12px] border border-border">
        <div className="py-16 flex flex-col items-center justify-center text-center px-4">
          {emptyIcon && <div className="mb-3 text-neutral-300">{emptyIcon}</div>}
          <p className="text-[15px] font-medium text-neutral-500">{emptyMessage}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-[12px] border border-border overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-[14px]">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-100/70">
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={`px-5 py-3.5 text-left text-[12.5px] font-semibold uppercase tracking-[0.04em] text-neutral-500 whitespace-nowrap ${col.className || ''}`}
                >
                  {col.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {data.map((item, index) => (
              <tr
                key={rowKey(item)}
                onClick={() => onRowClick?.(item)}
                className={`transition-colors duration-150 ${
                  onRowClick ? 'cursor-pointer hover:bg-primary-50/30' : 'hover:bg-neutral-50/60'
                } ${index % 2 === 1 ? 'bg-neutral-50/30' : 'bg-white'}`}
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={`px-5 py-[22px] align-middle text-neutral-700 ${col.className || ''}`}
                  >
                    {col.render
                      ? col.render(item, index)
                      : String((item as Record<string, unknown>)[col.key] ?? '')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
   Pagination
   ═══════════════════════════════════════════════════════════════ */

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  pageSize?: number;
  onPageChange: (page: number) => void;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize = 20,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1) return null;

  const start = page * pageSize + 1;
  const end = Math.min((page + 1) * pageSize, totalElements);

  return (
    <div className="flex items-center justify-between gap-4 flex-wrap bg-white rounded-[12px] border border-border px-4 py-2.5 text-[14px]">
      <span className="text-neutral-500">
        Showing <span className="font-medium text-neutral-800">{start}</span>
        {' – '}
        <span className="font-medium text-neutral-800">{end}</span> of{' '}
        <span className="font-medium text-neutral-800">{totalElements}</span>
      </span>
      <div className="flex items-center gap-0.5">
        <button
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          className="p-1.5 rounded-[8px] text-neutral-500 hover:bg-neutral-100 hover:text-neutral-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          aria-label="Previous page"
        >
          <ChevronLeft size={16} />
        </button>
        {Array.from({ length: totalPages }, (_, i) => i)
          .filter((i) => i === 0 || i === totalPages - 1 || Math.abs(i - page) <= 1)
          .reduce<(number | 'ellipsis')[]>((acc, i, idx, arr) => {
            if (idx > 0 && i - (arr[idx - 1] as number) > 1) acc.push('ellipsis');
            acc.push(i);
            return acc;
          }, [])
          .map((item, idx) =>
            item === 'ellipsis' ? (
              <span key={`e-${idx}`} className="px-1 text-neutral-300">
                <MoreHorizontal size={14} />
              </span>
            ) : (
              <button
                key={item}
                onClick={() => onPageChange(item)}
                className={`min-w-[32px] h-8 rounded-[8px] text-[14px] font-medium transition-colors duration-150 ${
                  item === page
                    ? 'bg-primary-500 text-white'
                    : 'text-neutral-600 hover:bg-neutral-100 hover:text-neutral-800'
                }`}
              >
                {item + 1}
              </button>
            )
          )}
        <button
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="p-1.5 rounded-[8px] text-neutral-500 hover:bg-neutral-100 hover:text-neutral-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          aria-label="Next page"
        >
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
   Skeleton
   ═══════════════════════════════════════════════════════════════ */

interface SkeletonProps {
  className?: string;
  style?: React.CSSProperties;
}

export function Skeleton({ className = '', style }: SkeletonProps) {
  return <div style={style} className={`skeleton rounded-[8px] ${className}`} />;
}

Skeleton.Text = function SkeletonText({ lines = 3 }: { lines?: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className={`h-3.5 ${i === lines - 1 ? 'w-2/3' : 'w-full'}`} />
      ))}
    </div>
  );
};

Skeleton.Circle = function SkeletonCircle({ size = 40 }: { size?: number }) {
  return <Skeleton className="rounded-full shrink-0" style={{ width: size, height: size }} />;
};

Skeleton.Card = function SkeletonCard() {
  return (
    <div className="bg-white rounded-[12px] border border-border p-5 space-y-3">
      <Skeleton className="h-4 w-1/3" />
      <Skeleton.Text lines={2} />
      <Skeleton className="h-8 w-24 mt-3" />
    </div>
  );
};

Skeleton.Table = function SkeletonTable({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="bg-white rounded-[12px] border border-border overflow-hidden">
      <div className="px-5 py-4 border-b border-neutral-100 flex gap-4">
        {Array.from({ length: cols }).map((_, i) => (
          <Skeleton key={i} className="h-3.5 flex-1" />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, rowIdx) => (
        <div key={rowIdx} className="px-5 py-6 flex gap-4 border-b border-neutral-100 last:border-0">
          {Array.from({ length: cols }).map((_, colIdx) => (
            <Skeleton key={colIdx} className="h-3.5 flex-1" />
          ))}
        </div>
      ))}
    </div>
  );
};

/* ═══════════════════════════════════════════════════════════════
   EmptyState / ErrorState
   ═══════════════════════════════════════════════════════════════ */

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}

export function EmptyState({ icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-14 px-4 text-center">
      {icon && (
        <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-[12px] bg-neutral-100 text-neutral-400">
          {icon}
        </div>
      )}
      <h3 className="text-[17px] font-semibold text-neutral-800">{title}</h3>
      {description && (
        <p className="mt-1.5 text-[14px] text-neutral-500 max-w-sm leading-relaxed">{description}</p>
      )}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
}

export function ErrorState({ title = 'Unable to load data', message, onRetry }: ErrorStateProps) {
  return (
    <div className="bg-white rounded-[12px] border border-border p-8">
      <div className="flex flex-col items-center justify-center text-center">
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-danger-50 text-danger-500 ring-1 ring-inset ring-danger-600/20">
          <AlertCircle size={24} />
        </div>
        <h3 className="text-[17px] font-semibold text-neutral-900">{title}</h3>
        {message && (
          <p className="mt-1.5 text-[14px] text-neutral-500 max-w-md leading-relaxed">{message}</p>
        )}
        {onRetry && (
          <Button variant="secondary" className="mt-5" onClick={onRetry}>
            <RefreshCw size={14} /> Try Again
          </Button>
        )}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
   Toast
   ═══════════════════════════════════════════════════════════════ */

export const notify = {
  success: (m: string) => toast.success(m, { duration: 3000 }),
  error: (m: string) => toast.error(m, { duration: 4000 }),
  info: (m: string) =>
    toast(m, { icon: 'ℹ️', duration: 3000 }),
};

/* ═══════════════════════════════════════════════════════════════
   ConfirmDialog
   ═══════════════════════════════════════════════════════════════ */

interface ConfirmDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'danger' | 'primary';
  loading?: boolean;
}

export function ConfirmDialog({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'danger',
  loading = false,
}: ConfirmDialogProps) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title} size="sm">
      {message && <p className="text-[14px] text-neutral-600 mb-5 leading-relaxed">{message}</p>}
      <div className="flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose} disabled={loading}>
          {cancelLabel}
        </Button>
        <Button variant={variant} onClick={onConfirm} loading={loading}>
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}

/* ═══════════════════════════════════════════════════════════════
   Dropdown
   ═══════════════════════════════════════════════════════════════ */

interface DropdownItem {
  label: string;
  onClick: () => void;
  icon?: ReactNode;
  danger?: boolean;
}

interface DropdownProps {
  items: DropdownItem[];
  trigger?: ReactNode;
}

export function Dropdown({ items, trigger }: DropdownProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const ref = useRef<HTMLDivElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (open) setActiveIndex(-1);
  }, [open]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!open) {
      if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setOpen(true);
      }
      return;
    }
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setActiveIndex((prev) => (prev < items.length - 1 ? prev + 1 : 0));
        break;
      case 'ArrowUp':
        e.preventDefault();
        setActiveIndex((prev) => (prev > 0 ? prev - 1 : items.length - 1));
        break;
      case 'Enter':
      case ' ':
        e.preventDefault();
        if (activeIndex >= 0) {
          items[activeIndex].onClick();
          setOpen(false);
        }
        break;
      case 'Escape':
        setOpen(false);
        break;
    }
  };

  useEffect(() => {
    if (activeIndex >= 0 && itemRefs.current[activeIndex]) {
      itemRefs.current[activeIndex]?.focus();
    }
  }, [activeIndex]);

  return (
    <div ref={ref} className="relative inline-block">
      <div
        onClick={() => setOpen(!open)}
        onKeyDown={handleKeyDown}
        tabIndex={0}
        role="button"
        aria-haspopup="true"
        aria-expanded={open}
        className="cursor-pointer outline-none"
      >
        {trigger || (
          <button className="p-1.5 rounded-[8px] text-neutral-400 hover:text-neutral-600 hover:bg-neutral-100 transition-colors">
            <MoreHorizontal size={17} />
          </button>
        )}
      </div>
      {open && (
        <div
          className="absolute right-0 z-40 mt-1 w-48 bg-white rounded-[10px] border border-border shadow-raised py-1.5 animate-fadeIn"
          role="menu"
        >
          {items.map((item, i) => (
            <button
              key={i}
              ref={(el) => {
                itemRefs.current[i] = el;
              }}
              onClick={() => {
                item.onClick();
                setOpen(false);
              }}
              role="menuitem"
              className={`w-full flex items-center gap-2.5 px-3 py-2.5 my-0.5 text-[14px] text-left transition-colors outline-none ${
                i === activeIndex ? 'bg-neutral-50' : ''
              } ${
                item.danger
                  ? 'text-danger-600 hover:bg-danger-50'
                  : 'text-neutral-700 hover:bg-neutral-50'
              }`}
            >
              {item.icon && (
                <span className={`shrink-0 ${item.danger ? 'text-danger-400' : 'text-neutral-400'}`}>
                  {item.icon}
                </span>
              )}
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
   PageHeader / PageContainer / FilterToolbar / Tabs
   ═══════════════════════════════════════════════════════════════ */

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
}

export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="flex items-start justify-between gap-4 mb-8 flex-wrap">
      <div className="min-w-0">
        <h1 className="text-[28px] font-bold tracking-[-0.02em] leading-[1.2] text-neutral-900">
          {title}
        </h1>
        {description && (
          <p className="mt-2 text-[15px] leading-relaxed text-neutral-500 max-w-2xl">
            {description}
          </p>
        )}
      </div>
      {actions && <div className="shrink-0 flex items-center gap-2.5 self-center">{actions}</div>}
    </div>
  );
}

interface PageContainerProps {
  children: ReactNode;
  size?: 'default' | 'narrow';
  className?: string;
}

export function PageContainer({ children, size = 'default', className = '' }: PageContainerProps) {
  const widthClass = size === 'narrow' ? 'max-w-[1040px]' : 'max-w-[1480px]';
  return <div className={`${widthClass} mx-auto w-full px-1 ${className}`}>{children}</div>;
}

interface FilterToolbarProps {
  search?: ReactNode;
  filters?: ReactNode;
  children?: ReactNode;
}

export function FilterToolbar({ search, filters, children }: FilterToolbarProps) {
  return (
    <div className="mb-6 flex flex-wrap items-end gap-3">
      {search && <div className="w-80 max-w-full">{search}</div>}
      {filters && <div className="flex flex-wrap items-end gap-3">{filters}</div>}
      {children && <div className="ml-auto flex items-center gap-3">{children}</div>}
    </div>
  );
}

interface TabsProps<T extends string> {
  tabs: { key: T; label: string }[];
  active: T;
  onChange: (tab: T) => void;
}

export function Tabs<T extends string>({ tabs, active, onChange }: TabsProps<T>) {
  return (
    <div className="flex items-center gap-1 mb-6 flex-wrap border-b border-neutral-200">
      {tabs.map((tab) => {
        const isActive = active === tab.key;
        return (
          <button
            key={tab.key}
            onClick={() => onChange(tab.key)}
            className={`px-4 py-2.5 -mb-px border-b-2 text-[14px] font-medium transition-colors ${
              isActive
                ? 'border-primary-500 text-primary-700'
                : 'border-transparent text-neutral-500 hover:text-neutral-700 hover:border-neutral-300'
            }`}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
