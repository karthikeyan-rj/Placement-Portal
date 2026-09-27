import { Briefcase, ExternalLink, MapPin, Sparkles } from 'lucide-react';
import { Button } from '../ui';
import { Field, SectionCard, ValueOrEmpty, isBlank } from './staffProfileParts';
import type { StaffProfile } from '../../types';

/**
 * Read-only view of the editable professional fields. Shared by the Overview
 * and Professional tabs so both show exactly the same data the modal edits.
 */
export function ProfessionalSummary({ staffProfile }: { staffProfile: StaffProfile | null }) {
  const designation = staffProfile?.designation ?? null;
  const officeLocation = staffProfile?.officeLocation ?? null;
  const phone = staffProfile?.phone ?? null;
  const bio = staffProfile?.bio ?? null;
  const linkedinUrl = staffProfile?.linkedinUrl ?? null;
  const expertise = staffProfile?.expertise ?? [];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5">
      <SectionCard title="Role Details" icon={<Briefcase size={17} />}>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
          <Field label="Designation">
            <ValueOrEmpty value={designation} />
          </Field>
          <Field label="Office Location">
            <ValueOrEmpty value={officeLocation} />
          </Field>
          <Field label="Phone">
            <ValueOrEmpty value={phone} />
          </Field>
          <Field label="LinkedIn">
            {isBlank(linkedinUrl) ? (
              <ValueOrEmpty value={linkedinUrl} />
            ) : (
              <a
                href={linkedinUrl as string}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 text-primary-600 hover:text-primary-700 hover:underline break-all"
              >
                <ExternalLink size={13} className="shrink-0" aria-hidden="true" />
                {linkedinUrl}
              </a>
            )}
          </Field>
        </div>
      </SectionCard>

      <SectionCard title="Professional Bio" icon={<MapPin size={17} />}>
        {isBlank(bio) ? (
          <p className="text-[13.5px] text-neutral-400">No professional bio added yet.</p>
        ) : (
          <p className="text-[13.5px] text-neutral-700 leading-relaxed whitespace-pre-line">{bio}</p>
        )}
      </SectionCard>

      <SectionCard
        title="Areas of Expertise"
        icon={<Sparkles size={17} />}
        className="md:col-span-2"
      >
        {expertise.length === 0 ? (
          <p className="text-[13.5px] text-neutral-400">No areas of expertise added yet.</p>
        ) : (
          <ul className="flex flex-wrap gap-1.5">
            {expertise.map((tag) => (
              <li
                key={tag}
                className="rounded-full bg-primary-50 text-primary-700 text-[12.5px] font-medium px-2.5 py-1"
              >
                {tag}
              </li>
            ))}
          </ul>
        )}
      </SectionCard>
    </div>
  );
}

export function EditProfileButton({ onClick }: { onClick: () => void }) {
  return (
    <Button variant="secondary" onClick={onClick}>
      Edit profile
    </Button>
  );
}
