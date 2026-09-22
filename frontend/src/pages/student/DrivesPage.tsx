import { useState, useEffect, useCallback, useMemo } from 'react';
import { Badge, EmptyState, PageHeader, PageContainer, SearchInput, ErrorState } from '../../components/ui';
import { placementDriveApi } from '../../api/api';
import type { PlacementDrive } from '../../types';
import { Calendar, MapPin, Building2, Briefcase, Clock, IndianRupee, GraduationCap } from 'lucide-react';

const STATUS_META: Record<string, { label: string; variant: 'warning' | 'success' | 'danger' | 'info' | 'neutral' }> = {
  UPCOMING: { label: 'Upcoming', variant: 'info' },
  REGISTRATION_OPEN: { label: 'Open for Registration', variant: 'success' },
  REGISTRATION_CLOSED: { label: 'Registration Closed', variant: 'neutral' },
  ONGOING: { label: 'Ongoing', variant: 'warning' },
  COMPLETED: { label: 'Completed', variant: 'neutral' },
  CANCELLED: { label: 'Cancelled', variant: 'danger' },
};

const statusMeta = (s: string) =>
  STATUS_META[s] ?? {
    label: s.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()),
    variant: 'neutral' as const,
  };

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const fmt = (iso: string | null) => {
  if (!iso) return '—';
  const [y, mo, d] = iso.split('-');
  if (!y || !mo || !d || Number.isNaN(Number(mo))) return iso;
  return `${d} ${MONTHS[Number(mo) - 1]} ${y}`;
};

const cleanNum = (n: number | null | undefined): string => {
  if (n == null) return '';
  const s = String(n);
  return s.includes('.') ? s.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '') : s;
};

const hasCriteria = (d: PlacementDrive) =>
  !!d.eligibilityCriteria &&
  (d.eligibilityCriteria.minCgpa != null ||
    d.eligibilityCriteria.maxActiveBacklogs != null ||
    d.eligibilityCriteria.minTenthPct != null ||
    d.eligibilityCriteria.minTwelfthPct != null ||
    d.eligibilityCriteria.minDiplomaPct != null ||
    (d.eligibilityCriteria.allowedDepartmentNames?.length ?? 0) > 0);

export default function DrivesPage() {
  const [search, setSearch] = useState('');
  const [drives, setDrives] = useState<PlacementDrive[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  const fetchDrives = useCallback(async () => {
    try {
      const res = await placementDriveApi.getAll({ size: 1000 });
      setDrives(res.data?.data?.content ?? []);
      setLoadError(false);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDrives();
  }, [fetchDrives]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return drives;
    return drives.filter((d) =>
      `${d.companyName} ${d.jobRole} ${d.companyType ?? ''}`.toLowerCase().includes(q)
    );
  }, [drives, search]);

  if (loadError) {
    return (
      <PageContainer className="space-y-6">
        <PageHeader
          title="Placement Drives"
          description="View available campus recruitment opportunities."
        />
        <ErrorState
          title="Unable to load placement drives"
          message="We couldn't retrieve placement drives right now."
          onRetry={() => {
            setLoading(true);
            fetchDrives();
          }}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="space-y-6">
      <PageHeader
        title="Placement Drives"
        description="View available campus recruitment opportunities."
      />

      <div className="flex flex-wrap items-center gap-4">
        <div className="w-full max-w-80">
          <SearchInput
            placeholder="Search company / role..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        {!loading && filtered.length > 0 && (
          <span className="text-[15px] text-neutral-500">
            {filtered.length} {filtered.length === 1 ? 'drive' : 'drives'}
          </span>
        )}
        {search && filtered.length === 0 && !loading && (
          <button
            type="button"
            className="text-[13.5px] font-medium text-primary-600 hover:underline"
            onClick={() => setSearch('')}
          >
            Clear search
          </button>
        )}
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="bg-white rounded-[14px] border border-neutral-200/80 shadow-soft p-4 space-y-3 animate-pulse">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-[9px] bg-neutral-100" />
                <div className="space-y-1.5 flex-1">
                  <div className="h-3.5 bg-neutral-100 rounded-full w-2/3" />
                  <div className="h-2.5 bg-neutral-100 rounded-full w-1/2" />
                </div>
              </div>
              <div className="h-5 bg-neutral-100 rounded-full w-1/3" />
              <div className="space-y-1.5">
                <div className="h-2.5 bg-neutral-100 rounded-full w-3/4" />
                <div className="h-2.5 bg-neutral-100 rounded-full w-1/2" />
              </div>
              <div className="border-t border-neutral-100 pt-3">
                <div className="flex gap-1.5">
                  <div className="h-5 bg-neutral-100 rounded-full w-16" />
                  <div className="h-5 bg-neutral-100 rounded-full w-20" />
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<Briefcase size={48} />}
          title={search ? 'No placement drives match your search' : 'No placement drives available'}
          description={
            search
              ? 'Try a different company or role.'
              : 'There are no placement drives at the moment. Check back later for new opportunities.'
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map((drive) => {
            const meta = statusMeta(drive.status);
            const c = drive.eligibilityCriteria;
            return (
              <div
                key={drive.id}
                className="bg-white rounded-[14px] border border-neutral-200/80 shadow-soft p-4 flex flex-col hover:border-primary-200 hover:shadow-raised transition-colors duration-150 animate-fadeIn"
              >
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-2.5 min-w-0">
                    <div className="w-9 h-9 rounded-[9px] bg-brand-navy flex items-center justify-center shrink-0">
                      <Building2 size={16} className="text-white" />
                    </div>
                    <div className="min-w-0">
                      <p className="text-[15px] font-semibold text-neutral-900 leading-tight truncate">
                        {drive.companyName}
                      </p>
                      <p className="text-[15px] text-neutral-500 mt-0.5 truncate">
                        {drive.jobRole}
                      </p>
                    </div>
                  </div>
                  <Badge variant={meta.variant} dot size="sm">
                    {meta.label}
                  </Badge>
                </div>

                {drive.packageLpa != null && (
                  <div className="flex items-center gap-1.5 mb-3">
                    <IndianRupee size={14} className="text-primary-600" />
                    <span className="text-[15px] font-bold text-primary-600">
                      {cleanNum(drive.packageLpa)} LPA
                    </span>
                  </div>
                )}

                {drive.jobDescription && (
                  <p className="text-[13.5px] text-neutral-500 mb-3 line-clamp-2 leading-relaxed">
                    {drive.jobDescription}
                  </p>
                )}

                <div className="space-y-1.5 text-[13.5px] text-neutral-500 mt-auto">
                  {drive.driveDate && (
                    <div className="flex items-center gap-2">
                      <Calendar size={13} className="text-neutral-500 shrink-0" />
                      <span>Drive: {fmt(drive.driveDate)}</span>
                    </div>
                  )}
                  {drive.registrationDeadline && (
                    <div className="flex items-center gap-2">
                      <Clock size={13} className="text-neutral-500 shrink-0" />
                      <span>
                        Apply by:{' '}
                        <span className={drive.status === 'REGISTRATION_OPEN' ? 'text-primary-600 font-medium' : ''}>
                          {fmt(drive.registrationDeadline)}
                        </span>
                      </span>
                    </div>
                  )}
                  {drive.location && (
                    <div className="flex items-center gap-2">
                      <MapPin size={13} className="text-neutral-500 shrink-0" />
                      <span>{drive.location}</span>
                    </div>
                  )}
                </div>

                {hasCriteria(drive) && (
                  <div className="mt-3 pt-3 border-t border-neutral-100">
                    <p className="text-[12px] font-semibold uppercase tracking-wider text-neutral-500 mb-2">
                      Eligibility
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {c!.minCgpa != null && (
                        <Badge variant="info" size="sm">CGPA ≥ {cleanNum(c!.minCgpa)}</Badge>
                      )}
                      {c!.maxActiveBacklogs != null && (
                        <Badge variant="warning" size="sm">Backlogs ≤ {c!.maxActiveBacklogs}</Badge>
                      )}
                      {c!.minTenthPct != null && (
                        <Badge variant="info" size="sm">10th ≥ {cleanNum(c!.minTenthPct)}%</Badge>
                      )}
                      {c!.minTwelfthPct != null && (
                        <Badge variant="info" size="sm">12th ≥ {cleanNum(c!.minTwelfthPct)}%</Badge>
                      )}
                      {c!.minDiplomaPct != null && (
                        <Badge variant="info" size="sm">Diploma ≥ {cleanNum(c!.minDiplomaPct)}%</Badge>
                      )}
                      {c!.allowedDepartmentNames && c!.allowedDepartmentNames.length > 0 && (
                        <Badge variant="neutral" size="sm">
                          {c!.allowedDepartmentNames.join(', ')}
                        </Badge>
                      )}
                    </div>
                  </div>
                )}

                {drive.status === 'REGISTRATION_OPEN' && (
                  <div className="mt-3 pt-3 border-t border-neutral-100">
                    <div className="flex items-center gap-2 text-[14px] text-primary-600 font-medium">
                      <span className="w-1.5 h-1.5 rounded-full bg-primary-500 animate-pulse" />
                      Registration Open
                    </div>
                  </div>
                )}

                {drive.status === 'CANCELLED' && (
                  <div className="mt-3 pt-3 border-t border-neutral-100">
                    <div className="flex items-center gap-2 text-[14px] text-danger-600 font-medium">
                      <GraduationCap size={14} />
                      This drive has been cancelled by the placement office.
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </PageContainer>
  );
}