import { useCallback, useEffect, useRef, useState } from 'react';
import { studentImportApi } from '../../api/api';
import { getErrorMessage } from '../../api/axios';
import type {
  ImportPreviewResponse,
  ImportConfirmResponse,
  ImportRowPreview,
} from '../../types';
import { Badge, Button, Modal } from '../../components/ui';
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  FileDown,
  FileSpreadsheet,
  RotateCcw,
  UploadCloud,
  X,
} from 'lucide-react';
import toast from 'react-hot-toast';

const MAX_BYTES = 5 * 1024 * 1024;
const PAGE_SIZE = 50;

// Spreadsheet formula-injection protection: prefix any value that could be
// interpreted as a formula (= + - @ tab CR) with a single quote, then quote
// fields containing , " \n as CSV requires.
function csvCell(value: string): string {
  let cell = value ?? '';
  const first = cell.charAt(0);
  if (first === '=' || first === '+' || first === '-' || first === '@' || first === '\t' || first === '\r') {
    cell = "'" + cell;
  }
  if (/[",\r\n]/.test(cell)) {
    cell = '"' + cell.replace(/"/g, '""') + '"';
  }
  return cell;
}

function buildCsv(headers: string[], rows: (string | number | undefined | null)[][]): string {
  const esc = (v: string | number | undefined | null) => csvCell(String(v ?? ''));
  return [headers.map(esc).join(','), ...rows.map((r) => r.map(esc).join(','))].join('\r\n');
}

function triggerDownload(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

type Step = 'upload' | 'preview' | 'result';

const STATUS_VARIANT: Record<ImportRowPreview['status'], 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'teal'> = {
  READY: 'success',
  DUPLICATE: 'warning',
  ALREADY_REGISTERED: 'info',
  ALREADY_AUTHORIZED: 'neutral',
  INVALID: 'danger',
};

const STATUS_LABEL: Record<ImportRowPreview['status'], string> = {
  READY: 'Ready',
  DUPLICATE: 'Duplicate',
  ALREADY_REGISTERED: 'Already Registered',
  ALREADY_AUTHORIZED: 'Already Authorized',
  INVALID: 'Invalid',
};

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onImported?: () => void;
}

export default function StudentImportModal({ isOpen, onClose, onImported }: Props) {
  const [step, setStep] = useState<Step>('upload');
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ImportPreviewResponse | null>(null);
  const [result, setResult] = useState<ImportConfirmResponse | null>(null);
  const [page, setPage] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setStep('upload');
      setFile(null);
      setPreview(null);
      setResult(null);
      setError(null);
      setPage(0);
      setPreviewing(false);
      setConfirming(false);
    }
  }, [isOpen]);

  const validateFile = useCallback((f: File): string | null => {
    if (!f.name.toLowerCase().endsWith('.csv')) {
      return 'Please choose a .csv file.';
    }
    if (f.size > MAX_BYTES) {
      return 'File must be smaller than 5 MB.';
    }
    return null;
  }, []);

  const selectFile = (f: File | undefined | null) => {
    if (!f) return;
    const problem = validateFile(f);
    setError(problem);
    if (problem) return;
    setFile(f);
    setPreview(null);
    setStep('upload');
  };

  const handleDownloadTemplate = async () => {
    try {
      const res = await studentImportApi.template();
      triggerDownload('student-import-template.csv', res.data);
      toast.success('Template downloaded.');
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  const handlePreview = async () => {
    if (!file) return;
    setPreviewing(true);
    setError(null);
    try {
      const res = await studentImportApi.preview(file);
      setPreview(res.data);
      setPage(0);
      setStep('preview');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setPreviewing(false);
    }
  };

  const handleImport = async () => {
    if (!preview) return;
    const ready = preview.rows.filter((r) => r.status === 'READY');
    setConfirming(true);
    setError(null);
    try {
      const res = await studentImportApi.confirm(ready);
      setResult(res.data);
      setStep('result');
      onImported?.();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setConfirming(false);
    }
  };

  const downloadErrors = (rows: ImportRowPreview[]) => {
    const headers = ['row', 'email', 'name', 'registerNumber', 'department', 'error'];
    const data = rows.map((r) => [r.rowNumber, r.email, r.name, r.registerNumber, r.department, r.error || '']);
    triggerDownload('student-import-errors.csv', new Blob([buildCsv(headers, data)], { type: 'text/csv' }));
  };


  const statusBadge = (s: ImportRowPreview['status']) => (
    <Badge variant={STATUS_VARIANT[s]} size="sm" dot>
      {STATUS_LABEL[s]}
    </Badge>
  );

  const readyRows = preview?.rows.filter((r) => r.status === 'READY') ?? [];
  const nonReadyRows = preview?.rows.filter((r) => r.status !== 'READY') ?? [];
  const pageCount = Math.max(1, Math.ceil((preview?.rows.length ?? 0) / PAGE_SIZE));
  const visible = (preview?.rows ?? []).slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

  const actions = (
    <div className="flex flex-wrap items-center gap-3">
      {step === 'upload' && (
        <>
          <Button variant="secondary" onClick={onClose} disabled={previewing}>
            Cancel
          </Button>
          <Button onClick={handlePreview} loading={previewing} disabled={!file}>
            Preview Import
          </Button>
        </>
      )}
      {step === 'preview' && (
        <>
          <Button variant="secondary" onClick={() => setStep('upload')} disabled={confirming}>
            <RotateCcw size={14} /> Back
          </Button>
          <Button
            variant="secondary"
            onClick={() => downloadErrors(nonReadyRows)}
            disabled={nonReadyRows.length === 0}
          >
            <FileDown size={14} /> Errors
          </Button>
          <Button onClick={handleImport} loading={confirming} disabled={readyRows.length === 0}>
            Import {readyRows.length} {readyRows.length === 1 ? 'Student' : 'Students'}
            <ArrowRight size={14} />
          </Button>
        </>
      )}
      {step === 'result' && (
        <>
          <Button
            variant="secondary"
            onClick={() => downloadErrors(result?.errors ?? [])}
            disabled={!result || result.errors.length === 0}
          >
            <FileDown size={14} /> Errors
          </Button>
          <Button variant="primary" onClick={onClose}>
            Done
          </Button>
        </>
      )}
    </div>
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Import Students"
      description="Bulk-authorize students from a roster CSV. Authorized students can self-register using their college email and register number via email verification code."
      size="lg"
      actions={actions}
    >
      {error && (
        <div className="mb-4 flex items-start gap-2.5 rounded-[12px] border border-danger-200/70 bg-danger-50/60 px-3.5 py-2.5 text-[13.5px] text-danger-700">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" />
          <span className="min-w-0">{error}</span>
        </div>
      )}

      {step === 'upload' && (
        <div className="space-y-4">
          <div className="rounded-[12px] border border-neutral-200/70 bg-neutral-50/50 px-4 py-3.5">
            <p className="text-[13px] font-semibold text-neutral-800 mb-2">Required columns</p>
            <div className="flex flex-wrap gap-2">
              {['email', 'name', 'registerNumber', 'department'].map((c) => (
                <span
                  key={c}
                  className="rounded-[8px] bg-white border border-neutral-200/80 px-2.5 py-1 font-mono text-[12px] text-neutral-700"
                >
                  {c}
                </span>
              ))}
            </div>
            <div className="mt-3 flex items-center gap-2 text-[12.5px] text-text-secondary">
              <AlertTriangle size={14} className="shrink-0 text-warning-600" />
              Emails must be TCE addresses. Departments must match the official list.
            </div>
          </div>

          <button
            type="button"
            onClick={handleDownloadTemplate}
            className="inline-flex items-center gap-2 text-[13.5px] font-semibold text-primary-600 hover:text-primary-700 transition-colors"
          >
            <FileSpreadsheet size={15} />
            Download CSV Template
          </button>

          <input
            ref={inputRef}
            type="file"
            accept=".csv"
            className="hidden"
            onChange={(e) => selectFile(e.target.files?.[0])}
          />
          <div
            role="button"
            tabIndex={0}
            aria-label="Choose roster CSV"
            onClick={() => inputRef.current?.click()}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                inputRef.current?.click();
              }
            }}
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(false);
              selectFile(e.dataTransfer.files?.[0]);
            }}
            className={`flex flex-col items-center justify-center gap-2.5 rounded-[14px] border-2 border-dashed px-6 py-10 text-center transition-colors cursor-pointer ${
              dragOver
                ? 'border-primary-400 bg-primary-50/60'
                : 'border-neutral-300 bg-white hover:border-primary-300 hover:bg-primary-50/30'
            }`}
          >
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-primary-50 text-primary-600">
              <UploadCloud size={22} />
            </span>
            <p className="text-[14px] font-medium text-neutral-800">
              {file ? file.name : 'Drag & drop your roster CSV here'}
            </p>
            <p className="text-[12.5px] text-text-secondary">
              {file ? `${formatBytes(file.size)} · .csv up to 5 MB` : 'or click to browse · .csv up to 5 MB'}
            </p>
          </div>

          {file && (
            <div className="flex items-center justify-between gap-3 rounded-[12px] border border-primary-200/70 bg-primary-50/40 px-3.5 py-2.5">
              <div className="flex min-w-0 items-center gap-2.5">
                <FileSpreadsheet size={16} className="shrink-0 text-primary-600" />
                <div className="min-w-0">
                  <p className="truncate text-[13.5px] font-medium text-neutral-800">{file.name}</p>
                  <p className="text-[12px] text-text-secondary">{formatBytes(file.size)}</p>
                </div>
              </div>
              <button
                type="button"
                aria-label="Remove file"
                onClick={() => {
                  setFile(null);
                  setPreview(null);
                  setError(null);
                }}
                className="p-1.5 rounded-[8px] text-neutral-500 hover:text-danger-600 hover:bg-danger-50/60 transition-colors"
              >
                <X size={16} />
              </button>
            </div>
          )}
        </div>
      )}

      {step === 'preview' && preview && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="rounded-[12px] border border-success-200/70 bg-success-50/40 px-3.5 py-3">
              <p className="text-[12px] font-medium text-success-700">Ready</p>
              <p className="mt-0.5 text-[22px] font-bold leading-none text-neutral-900 tabular-nums">
                {preview.validRows}
              </p>
            </div>
            <div className="rounded-[12px] border border-neutral-200/70 bg-neutral-50/60 px-3.5 py-3">
              <p className="text-[12px] font-medium text-neutral-600">Already Authorized</p>
              <p className="mt-0.5 text-[22px] font-bold leading-none text-neutral-900 tabular-nums">
                {preview.alreadyAuthorized}
              </p>
            </div>
            <div className="rounded-[12px] border border-warning-200/70 bg-warning-50/40 px-3.5 py-3">
              <p className="text-[12px] font-medium text-warning-700">Duplicates</p>
              <p className="mt-0.5 text-[22px] font-bold leading-none text-neutral-900 tabular-nums">
                {preview.duplicateRows}
              </p>
            </div>
            <div className="rounded-[12px] border border-danger-200/70 bg-danger-50/40 px-3.5 py-3">
              <p className="text-[12px] font-medium text-danger-700">Invalid</p>
              <p className="mt-0.5 text-[22px] font-bold leading-none text-neutral-900 tabular-nums">
                {preview.invalidRows}
              </p>
            </div>
          </div>
          {(preview.alreadyRegistered > 0 || preview.duplicateRows > 0) && (
            <p className="text-[12.5px] text-text-secondary">
              {preview.alreadyRegistered > 0 && `${preview.alreadyRegistered} already registered`}
              {preview.alreadyRegistered > 0 && preview.duplicateRows > 0 && ' · '}
              {preview.duplicateRows > 0 && `${preview.duplicateRows} duplicated in file`}
              {' — these are not imported.'}
            </p>
          )}

          <div className="overflow-x-auto -mx-1">
            <table className="w-full min-w-[640px] border-collapse text-left">
              <thead>
                <tr className="border-b border-neutral-200/80">
                  {['Row', 'Name', 'Email', 'Reg No', 'Department', 'Status'].map((h) => (
                    <th
                      key={h}
                      className="px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.06em] text-neutral-500 whitespace-nowrap"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {visible.map((r) => (
                  <tr key={r.rowNumber} className="border-b border-neutral-100/70 last:border-0">
                    <td className="px-3 py-2 font-mono text-[12.5px] text-neutral-500">{r.rowNumber}</td>
                    <td className="px-3 py-2 text-[13.5px] font-medium text-neutral-800 max-w-[180px] truncate">
                      {r.name || '—'}
                    </td>
                    <td className="px-3 py-2 text-[13px] text-neutral-600 max-w-[220px] truncate">
                      {r.email || '—'}
                    </td>
                    <td className="px-3 py-2 font-mono text-[12.5px] text-neutral-600 whitespace-nowrap">
                      {r.registerNumber || '—'}
                    </td>
                    <td className="px-3 py-2 text-[13px] text-neutral-600 whitespace-nowrap">
                      {r.department || '—'}
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">{statusBadge(r.status)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {pageCount > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-[12.5px] text-text-secondary">
                Showing {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, preview.rows.length)} of{' '}
                {preview.rows.length}
              </p>
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                  className="rounded-[8px] border border-neutral-200/80 px-3 py-1.5 text-[13px] font-medium text-neutral-700 hover:bg-neutral-50 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
                >
                  Prev
                </button>
                <button
                  type="button"
                  disabled={page >= pageCount - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="rounded-[8px] border border-neutral-200/80 px-3 py-1.5 text-[13px] font-medium text-neutral-700 hover:bg-neutral-50 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {step === 'result' && result && (
        <div className="space-y-4">
          <div className="flex items-start gap-3 rounded-[12px] border border-success-200/70 bg-success-50/40 px-4 py-3.5">
            <CheckCircle2 size={18} className="mt-0.5 shrink-0 text-success-600" />
            <div>
              <p className="text-[14.5px] font-semibold text-neutral-900">
                {result.imported} {result.imported === 1 ? 'student was' : 'students were'} authorized for registration.
              </p>
              <p className="mt-1 text-[13.5px] text-neutral-700">
                Students can now register directly using their college email and register number. A one-time verification code will be sent to their email when they sign up.
              </p>
              <p className="mt-2 text-[12.5px] text-text-secondary">
                {result.alreadyAuthorized} already authorized · {result.alreadyRegistered} already registered · {result.skipped} invalid or duplicated.
              </p>
            </div>
          </div>

          {result.errors.length > 0 && (
            <p className="text-[12.5px] text-text-secondary">
              {result.errors.length} row{result.errors.length === 1 ? '' : 's'} were not imported — see the errors file.
            </p>
          )}
        </div>
      )}
    </Modal>
  );
}