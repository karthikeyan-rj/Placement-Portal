import { useState } from 'react';
import { Badge, EmptyState, PageHeader, PageContainer, SearchInput, Pagination } from '../../components/ui';
import { usePaginatedData } from '../../hooks/useApi';
import type { PlacementDrive } from '../../types';
import { Calendar, MapPin, Building2, Briefcase, Clock, IndianRupee } from 'lucide-react';

const statusVariant = (s: string): 'warning' | 'success' | 'danger' | 'info' | 'neutral' => {
  switch (s) {
    case 'UPCOMING': return 'info';
    case 'OPEN': return 'success';
    case 'IN_PROGRESS': return 'warning';
    case 'COMPLETED': return 'neutral';
    case 'CLOSED': return 'neutral';
    case 'CANCELLED': return 'danger';
    default: return 'neutral';
  }
};

const statusLabel = (s: string): string => {
  switch (s) {
    case 'OPEN': return 'Registration Open';
    case 'UPCOMING': return 'Upcoming';
    case 'IN_PROGRESS': return 'In Progress';
    case 'COMPLETED': return 'Completed';
    case 'CLOSED': return 'Closed';
    case 'CANCELLED': return 'Cancelled';
    default: return s;
  }
};

export default function DrivesPage() {
  const [search, setSearch] = useState('');

  const {
    data: drives,
    loading,
    page,
    totalPages,
    totalElements,
    setPage,
  } = usePaginatedData<PlacementDrive>({
    url: '/placement-drives',
    params: search ? { search } : {},
  });

  return (
    <PageContainer className="space-y-6">
      <PageHeader
        title="Placement Drives"
        description="View available campus recruitment opportunities."
      />

      <div className="flex items-center gap-4">
        <div className="w-80">
          <SearchInput
            placeholder="Search drives..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        {!loading && drives.length > 0 && (
          <span className="text-[15px] text-neutral-500">
            {totalElements} {totalElements === 1 ? 'drive' : 'drives'} found
          </span>
        )}
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              key={i}
              className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-5 space-y-3.5"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-lg bg-neutral-100 animate-pulse" />
                <div className="space-y-1.5 flex-1">
                  <div className="h-3.5 bg-neutral-100 rounded animate-pulse w-2/3" />
                  <div className="h-2.5 bg-neutral-100 rounded animate-pulse w-1/2" />
                </div>
              </div>
              <div className="h-5 bg-neutral-100 rounded animate-pulse w-1/3" />
              <div className="space-y-1.5">
                <div className="h-2.5 bg-neutral-100 rounded animate-pulse w-3/4" />
                <div className="h-2.5 bg-neutral-100 rounded animate-pulse w-1/2" />
              </div>
              <div className="border-t border-neutral-100 pt-3">
                <div className="flex gap-1.5">
                  <div className="h-5 bg-neutral-100 rounded-full animate-pulse w-16" />
                  <div className="h-5 bg-neutral-100 rounded-full animate-pulse w-20" />
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : drives.length === 0 ? (
        <EmptyState
          icon={<Briefcase size={48} />}
          title="No placement drives available"
          description="There are no placement drives at the moment. Check back later for new opportunities."
        />
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {drives.map((drive) => (
              <div
                key={drive.id}
                className="bg-white rounded-xl border border-neutral-200/70 shadow-card p-5 flex flex-col hover:shadow-raised hover:-translate-y-0.5 hover:border-primary-200/70 transition-all duration-150"
              >
                {/* Header */}
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-2.5">
                    <div className="w-9 h-9 rounded-lg bg-neutral-100 flex items-center justify-center shrink-0">
                      <Building2 size={16} className="text-neutral-600" />
                    </div>
                    <div>
                      <p className="text-[15px] font-semibold text-neutral-900 leading-tight">
                        {drive.companyName}
                      </p>
                      <p className="text-[15px] text-neutral-500 mt-0.5">
                        {drive.jobRole}
                      </p>
                    </div>
                  </div>
                  <Badge variant={statusVariant(drive.status)} dot size="sm">
                    {statusLabel(drive.status)}
                  </Badge>
                </div>

                {/* Package */}
                {drive.packageLpa != null && (
                  <div className="flex items-center gap-1.5 mb-3">
                    <IndianRupee size={14} className="text-primary-600" />
                    <span className="text-[15px] font-bold text-primary-600">
                      {drive.packageLpa} LPA
                    </span>
                  </div>
                )}

                {/* Description */}
                {drive.jobDescription && (
                  <p className="text-[15px] text-neutral-500 mb-3 line-clamp-2 leading-relaxed">
                    {drive.jobDescription}
                  </p>
                )}

                {/* Dates & Location */}
                <div className="space-y-1.5 text-[15px] text-neutral-500 mt-auto">
                  {drive.driveDate && (
                    <div className="flex items-center gap-2">
                      <Calendar size={13} className="text-neutral-400 shrink-0" />
                      <span>Drive: {new Date(drive.driveDate).toLocaleDateString()}</span>
                    </div>
                  )}
                  {drive.registrationDeadline && (
                    <div className="flex items-center gap-2">
                      <Clock size={13} className="text-neutral-400 shrink-0" />
                      <span>Apply by: {new Date(drive.registrationDeadline).toLocaleDateString()}</span>
                    </div>
                  )}
                  {drive.location && (
                    <div className="flex items-center gap-2">
                      <MapPin size={13} className="text-neutral-400 shrink-0" />
                      <span>{drive.location}</span>
                    </div>
                  )}
                </div>

                {/* Eligibility */}
                {drive.eligibilityCriteria && (
                  <div className="mt-3 pt-3 border-t border-neutral-100">
                    <p className="text-[15px] text-neutral-500 uppercase tracking-wide mb-2 font-medium">Eligibility</p>
                    <div className="flex flex-wrap gap-1.5">
                      {drive.eligibilityCriteria.minCgpa != null && (
                        <Badge variant="info" size="sm">CGPA &ge; {drive.eligibilityCriteria.minCgpa}</Badge>
                      )}
                      {drive.eligibilityCriteria.maxActiveBacklogs != null && (
                        <Badge variant="warning" size="sm">Backlogs &le; {drive.eligibilityCriteria.maxActiveBacklogs}</Badge>
                      )}
                      {drive.eligibilityCriteria.minTenthPct != null && (
                        <Badge variant="info" size="sm">10th &ge; {drive.eligibilityCriteria.minTenthPct}%</Badge>
                      )}
                      {drive.eligibilityCriteria.minTwelfthPct != null && (
                        <Badge variant="info" size="sm">12th &ge; {drive.eligibilityCriteria.minTwelfthPct}%</Badge>
                      )}
                      {drive.eligibilityCriteria.minDiplomaPct != null && (
                        <Badge variant="info" size="sm">Diploma &ge; {drive.eligibilityCriteria.minDiplomaPct}%</Badge>
                      )}
                    </div>
                  </div>
                )}

                {/* Open indicator */}
                {drive.status === 'OPEN' && (
                  <div className="mt-3 pt-3 border-t border-neutral-100">
                    <div className="flex items-center gap-2 text-[14px] text-primary-600 font-medium">
                      <span className="w-1.5 h-1.5 rounded-full bg-primary-500 animate-pulse" />
                      Registration Open
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="mt-2">
              <Pagination
                page={page}
                totalPages={totalPages}
                totalElements={totalElements}
                onPageChange={setPage}
              />
            </div>
          )}
        </>
      )}
    </PageContainer>
  );
}
