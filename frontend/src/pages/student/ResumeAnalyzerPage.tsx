import { useCallback, useEffect, useRef, useState } from 'react';
import { resumeAnalyzerApi } from '../../api/api';
import type {
  ResumeAnalysis,
  ResumeAnalysisSummary,
  ResumeContactChecks,
  ResumeRecommendation,
  ResumeSectionCheck,
} from '../../types';
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  PageContainer,
  PageHeader,
  Skeleton,
} from '../../components/ui';
import {
  AlertTriangle,
  Check,
  Circle,
  FileText,
  FolderOpen,
  GitBranch,
  Link2,
  Mail,
  MapPin,
  Phone,
  Sparkles,
  Trash2,
  UploadCloud,
} from 'lucide-react';

const MAX_MB = 5;
const MAX_SIZE = MAX_MB * 1024 * 1024;

const LEVEL_VARIANT: Record<ResumeRecommendation['level'], 'danger' | 'warning' | 'neutral'> = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'neutral',
};

function fmtBytes(bytes: number | null | undefined): string {
  if (!bytes) return '0 KB';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function fmtDate(d: string): string {
  const dt = new Date(d);
  if (Number.isNaN(dt.getTime())) return d;
  return dt.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

function Bar({ value, tone = 'brand' }: { value: number; tone?: 'brand' | 'success' | 'warning' | 'danger' }) {
  const toneClasses: Record<string, string> = {
    brand: 'bg-brand-navy',
    success: 'bg-success-500',
    warning: 'bg-warning-500',
    danger: 'bg-danger-500',
  };
  return (
    <div
      role="progressbar"
      aria-valuenow={Math.round(value)}
      aria-valuemin={0}
      aria-valuemax={100}
      className="h-2 w-full overflow-hidden rounded-full bg-neutral-200/70"
    >
      <div
        className={`h-full rounded-full transition-all duration-500 ${toneClasses[tone]}`}
        style={{ width: `${Math.max(0, Math.min(100, value))}%` }}
      />
    </div>
  );
}

function toneFor(value: number): 'success' | 'warning' | 'danger' {
  if (value >= 70) return 'success';
  if (value >= 45) return 'warning';
  return 'danger';
}

function ContactChip({
  label,
  present,
  icon,
}: {
  label: string;
  present: boolean;
  icon: React.ReactNode;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[12.5px] font-medium ring-1 ring-inset ${
        present
          ? 'bg-success-50 text-success-700 ring-success-200'
          : 'bg-neutral-100 text-neutral-500 ring-neutral-200'
      }`}
    >
      {icon}
      {label}
      <span className={present ? 'text-success-600' : 'text-neutral-400'}>
        {present ? 'present' : 'missing'}
      </span>
    </span>
  );
}

function SectionPill({ section }: { section: ResumeSectionCheck }) {
  return (
    <li className="flex items-center gap-2 text-[13.5px]">
      {section.found ? (
        <span className="flex h-[18px] w-[18px] items-center justify-center rounded-full bg-success-100 text-success-600">
          <Check size={12} strokeWidth={3} />
        </span>
      ) : (
        <span className="flex h-[18px] w-[18px] items-center justify-center rounded-full bg-neutral-100 text-neutral-400">
          <Circle size={12} />
        </span>
      )}
      <span className={section.found ? 'font-medium text-neutral-800' : 'text-neutral-500'}>
        {section.name}
      </span>
    </li>
  );
}

function RecItem({ index, rec }: { index: number; rec: ResumeRecommendation }) {
  return (
    <li className="flex gap-3 rounded-[10px] border border-neutral-200/60 bg-white px-3.5 py-3">
      <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-navy text-[12px] font-bold text-white">
        {index}
      </span>
      <div className="min-w-0">
        <div className="mb-1">
          <Badge variant={LEVEL_VARIANT[rec.level]} size="sm">
            {rec.level}
          </Badge>
        </div>
        <p className="text-[13.5px] leading-relaxed text-neutral-700">{rec.text}</p>
      </div>
    </li>
  );
}

const CONTACT_ITEMS: {
  key: keyof ResumeContactChecks;
  label: string;
  icon: React.ReactNode;
}[] = [
  { key: 'emailPresent', label: 'Email', icon: <Mail size={13} /> },
  { key: 'phonePresent', label: 'Phone', icon: <Phone size={13} /> },
  { key: 'linkedinPresent', label: 'LinkedIn', icon: <Link2 size={13} /> },
  { key: 'githubPresent', label: 'GitHub', icon: <GitBranch size={13} /> },
  { key: 'portfolioPresent', label: 'Portfolio', icon: <MapPin size={13} /> },
];

export default function ResumeAnalyzerPage() {
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [active, setActive] = useState<ResumeAnalysis | null>(null);
  const [history, setHistory] = useState<ResumeAnalysisSummary[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const loadHistory = useCallback(async () => {
    try {
      const res = await resumeAnalyzerApi.history();
      const data = res.data?.data;
      setHistory(Array.isArray(data) ? data : []);
      setHistoryError(false);
    } catch {
      setHistoryError(true);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  const pickFile = (candidate: File | undefined | null) => {
    setUploadError(null);
    if (!candidate) return;
    if (candidate.type !== 'application/pdf' && !candidate.name.toLowerCase().endsWith('.pdf')) {
      setUploadError('Only PDF files are supported.');
      return;
    }
    if (candidate.size > MAX_SIZE) {
      setUploadError(`File must be smaller than 5 MB (this one is ${fmtBytes(candidate.size)}).`);
      return;
    }
    setFile(candidate);
    setActive(null);
  };

  const openPicker = () => inputRef.current?.click();

  const removeFile = () => {
    setFile(null);
    setUploadError(null);
    if (inputRef.current) inputRef.current.value = '';
  };

  const analyze = async () => {
    if (!file) return;
    setAnalyzing(true);
    setUploadError(null);
    try {
      const res = await resumeAnalyzerApi.analyze(file);
      const data = res.data?.data as ResumeAnalysis | undefined;
      if (data) setActive(data);
      removeFile();
      await loadHistory();
    } catch (err: unknown) {
      const anyErr = err as { response?: { data?: { message?: string } } };
      setUploadError(anyErr?.response?.data?.message || 'We could not analyze this resume. Please try again.');
    } finally {
      setAnalyzing(false);
    }
  };

  const viewDetail = async (id: number) => {
    setDetailId(id);
    setUploadError(null);
    try {
      const res = await resumeAnalyzerApi.detail(id);
      const data = res.data?.data as ResumeAnalysis | undefined;
      if (data) setActive(data);
    } catch {
      setUploadError('We could not load this analysis. Please try again.');
    } finally {
      setDetailId(null);
    }
  };

  return (
    <PageContainer>
      <PageHeader
        title="Resume Analyzer"
        description="Improve your resume before applying to placement opportunities."
      />

      {/* Upload */}
      <div className="mb-5 rounded-[14px] border border-neutral-200/60 bg-white p-5 shadow-soft">
        <div className="mb-3 flex items-center gap-2 text-[13px] text-neutral-500">
          <Sparkles size={14} className="text-brand-navy" />
          <span>
            A preparation aid built for students — scores are structured estimates, never a
            guaranteed ATS approval. Different employers use different systems.
          </span>
        </div>

        <button
          type="button"
          onClick={openPicker}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              openPicker();
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
            pickFile(e.dataTransfer.files?.[0]);
          }}
          className={`flex w-full flex-col items-center justify-center gap-2 rounded-[12px] border-2 border-dashed px-6 py-9 transition-colors focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-primary-500/20 ${
            dragOver
              ? 'border-primary-400 bg-primary-50/50'
              : 'border-neutral-300 bg-neutral-50/50 hover:border-neutral-400 hover:bg-neutral-50'
          }`}
        >
          <span className="flex h-11 w-11 items-center justify-center rounded-[12px] bg-brand-navy text-white">
            <UploadCloud size={20} />
          </span>
          <span className="text-[15px] font-semibold text-neutral-800">
            Drop PDF here or browse
          </span>
          <span className="text-[13px] text-neutral-500">PDF · Maximum 5 MB</span>
        </button>
        <label htmlFor="resume-file" className="sr-only">
          Upload resume PDF
        </label>
        <input
          ref={inputRef}
          id="resume-file"
          type="file"
          accept="application/pdf,.pdf"
          className="sr-only"
          onChange={(e) => pickFile(e.target.files?.[0])}
        />

        {file && (
          <div className="mt-4 flex flex-wrap items-center gap-3 rounded-[12px] border border-neutral-200/70 bg-neutral-50/60 px-4 py-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-danger-50 text-danger-600">
              <FileText size={16} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[14px] font-semibold text-neutral-800">{file.name}</p>
              <p className="text-[12.5px] text-neutral-500">{fmtBytes(file.size)}</p>
            </div>
            <Button variant="secondary" size="sm" onClick={removeFile}>
              <Trash2 size={14} />
              Remove
            </Button>
            <Button size="md" loading={analyzing} onClick={analyze} disabled={analyzing}>
              {analyzing ? 'Analyzing your resume…' : 'Analyze Resume'}
            </Button>
          </div>
        )}

        {uploadError && (
          <div className="mt-3 flex items-start gap-2 rounded-[10px] border border-danger-200 bg-danger-50 px-4 py-3 text-[13.5px] text-danger-700">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" />
            <span>{uploadError}</span>
          </div>
        )}

        <div aria-live="polite" className="sr-only">
          {analyzing ? 'Analyzing your resume' : ''}
        </div>
      </div>

      {/* Result */}
      {active && (
        <div className="mb-6 animate-fadeIn">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-[16px] font-bold text-neutral-900">Resume Analysis</h2>
            <span className="text-[13px] text-neutral-500">
              {active.fileName} · {active.pageCount} page{active.pageCount === 1 ? '' : 's'} ·{' '}
              {fmtDate(active.createdAt)}
            </span>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* Readiness + ATS */}
            <div className="lg:col-span-1 rounded-[14px] border border-neutral-200/60 bg-white p-5 shadow-soft">
              <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
                Resume Readiness
              </p>
              <div className="mt-1 flex items-baseline gap-2">
                <span className="text-[34px] font-bold leading-none text-neutral-900">
                  {active.readinessScore}
                </span>
                <span className="text-[14px] font-semibold text-neutral-400">/ 100</span>
              </div>
              <div className="mt-3">
                <Bar value={active.readinessScore} tone={toneFor(active.readinessScore)} />
                <p className="mt-1.5 text-[12.5px] text-neutral-500">
                  {active.readinessScore >= 70
                    ? 'Ready — keep refinements targeted.'
                    : active.readinessScore >= 45
                      ? 'Getting there — the priority list will help.'
                      : 'Needs attention — start with the high-priority items.'}
                </p>
              </div>
              <div className="mt-4 rounded-[10px] border border-neutral-200/60 bg-neutral-50/60 px-4 py-3">
                <div className="flex items-center justify-between">
                  <span className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
                    ATS Compatibility Estimate
                  </span>
                  <span
                    className={`text-[18px] font-bold ${toneFor(active.atsCompatibility) === 'success' ? 'text-success-600' : toneFor(active.atsCompatibility) === 'warning' ? 'text-warning-600' : 'text-danger-600'}`}
                  >
                    {active.atsCompatibility}
                  </span>
                </div>
                <p className="mt-1 text-[12px] leading-relaxed text-neutral-500">
                  Heuristic estimate based on formatting, sections and contact details — not a
                  promise about any employer&apos;s parser.
                </p>
              </div>
            </div>

            {/* Category cards */}
            <div className="lg:col-span-2 rounded-[14px] border border-neutral-200/60 bg-white p-5 shadow-soft">
              <p className="mb-3 text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
                Category Scores
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
                {[
                  { label: 'Profile Completeness', value: active.categoryScores.profileCompleteness },
                  { label: 'Content Quality', value: active.categoryScores.contentQuality },
                  { label: 'Impact', value: active.categoryScores.impact },
                  { label: 'Formatting', value: active.categoryScores.formatting },
                  { label: 'Professional Links', value: active.categoryScores.professionalLinks },
                ].map((cat) => (
                  <div key={cat.label} className="rounded-[12px] border border-neutral-200/60 bg-neutral-50/50 p-3.5">
                    <div className="flex items-center justify-between">
                      <span className="text-[12.5px] font-semibold text-neutral-700">{cat.label}</span>
                      <span className="text-[15px] font-bold text-neutral-900">{cat.value}</span>
                    </div>
                    <div className="mt-2">
                      <Bar value={cat.value} tone={toneFor(cat.value)} />
                    </div>
                  </div>
                ))}
              </div>

              <div className="mt-4 flex flex-wrap items-center gap-1.5">
                {CONTACT_ITEMS.map((item) => (
                  <ContactChip
                    key={item.key}
                    label={item.label}
                    present={!!active.contactChecks[item.key]}
                    icon={item.icon}
                  />
                ))}
              </div>
            </div>
          </div>

          {/* Sections + skills / recommendations */}
          <div className="mt-4 grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="rounded-[14px] border border-neutral-200/60 bg-white p-5 shadow-soft">
              <p className="mb-3 text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
                Resume Sections
              </p>
              <ul className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2.5">
                {active.sections.map((section) => (
                  <SectionPill key={section.name} section={section} />
                ))}
              </ul>

              {active.detectedSkills.length > 0 && (
                <>
                  <p className="mb-3 mt-5 text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
                    Technical Skills Detected
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {active.detectedSkills.map((skill) => (
                      <span
                        key={skill}
                        className="rounded-full bg-brand-navy px-3 py-1 text-[12px] font-medium text-white"
                      >
                        {skill}
                      </span>
                    ))}
                  </div>
                </>
              )}

              {active.warnings.length > 0 && (
                <div className="mt-5 space-y-2">
                  <p className="text-[12px] font-semibold uppercase tracking-wider text-warning-600">
                    Warnings
                  </p>
                  {active.warnings.map((warning, idx) => (
                    <div
                      key={idx}
                      className="flex items-start gap-2 rounded-[10px] border border-warning-200 bg-warning-50 px-3.5 py-2.5 text-[13px] text-warning-800"
                    >
                      <AlertTriangle size={14} className="mt-0.5 shrink-0" />
                      <span>{warning}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="rounded-[14px] border border-neutral-200/60 bg-white p-5 shadow-soft">
              <p className="mb-3 text-[12px] font-semibold uppercase tracking-wider text-neutral-500">
                Priority Improvements
              </p>
              {active.recommendations.length === 0 ? (
                <p className="text-[13.5px] text-neutral-500">
                  No urgent improvements — nice work. Keep measuring impact where you can.
                </p>
              ) : (
                <ol className="space-y-2.5">
                  {active.recommendations.map((rec, idx) => (
                    <RecItem key={`${rec.level}-${idx}`} index={idx + 1} rec={rec} />
                  ))}
                </ol>
              )}
            </div>
          </div>
        </div>
      )}

      {/* History */}
      <div className="rounded-[14px] border border-neutral-200/60 bg-white shadow-soft">
        <div className="flex items-center justify-between border-b border-neutral-100 px-5 py-4">
          <h2 className="text-[15px] font-bold text-neutral-900">Recent Analyses</h2>
        </div>
        {historyLoading ? (
          <div className="divide-y divide-neutral-100/70 p-2">
            {Array.from({ length: 3 }).map((_, idx) => (
              <div key={idx} className="flex items-center gap-4 px-3 py-3.5">
                <Skeleton className="h-9 w-9 rounded-[10px]" />
                <div className="flex-1 space-y-2">
                  <Skeleton className="h-3.5 w-2/5" />
                  <Skeleton className="h-3 w-1/4" />
                </div>
                <Skeleton className="h-6 w-16 rounded-full" />
              </div>
            ))}
          </div>
        ) : historyError ? (
          <div className="p-4">
            <ErrorState
              title="Unable to load analyses"
              message="We could not load your recent analyses right now."
              onRetry={loadHistory}
            />
          </div>
        ) : history.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={<FolderOpen size={40} />}
              title="No resume analyses yet"
              description="Upload your resume to receive structured feedback."
            />
          </div>
        ) : (
          <ul className="divide-y divide-neutral-100/70">
            {history.map((item) => (
              <li
                key={item.id}
                className="flex flex-wrap items-center gap-3 px-5 py-3.5 transition-colors hover:bg-neutral-50/60"
              >
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-danger-50 text-danger-600">
                  <FileText size={16} />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[14px] font-semibold text-neutral-800">{item.fileName}</p>
                  <p className="text-[12.5px] text-neutral-500">
                    {item.pageCount} page{item.pageCount === 1 ? '' : 's'} · {fmtDate(item.createdAt)}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <Badge variant={toneFor(item.readinessScore) === 'success' ? 'success' : toneFor(item.readinessScore) === 'warning' ? 'warning' : 'danger'} size="sm">
                    {item.readinessScore}%
                  </Badge>
                  <Button size="sm" variant="secondary" loading={detailId === item.id} onClick={() => viewDetail(item.id)}>
                    {detailId === item.id ? '' : 'View'}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </PageContainer>
  );
}