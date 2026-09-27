import { useState, useEffect, type FormEvent } from 'react';
import { Briefcase, ExternalLink, MapPin, Phone } from 'lucide-react';
import { Button, Input, Modal, Textarea } from '../ui';
import ExpertiseTagEditor from './ExpertiseTagEditor';
import type { StaffProfile } from '../../types';

const LIMITS = {
  phone: 25,
  designation: 120,
  officeLocation: 150,
  bio: 1000,
  linkedinUrl: 500,
} as const;

const BLANK = {
  phone: '',
  designation: '',
  officeLocation: '',
  bio: '',
  linkedinUrl: '',
  expertise: [] as string[],
};

/**
 * The single edit surface for a staff professional profile.
 *
 * It is opened from the Overview and Professional tabs and edits the same six
 * editable fields in one place, so a staff member never has to hunt across tabs
 * for a field. Account facts (name, email, role, department) are deliberately
 * absent: those are not self-editable.
 *
 * Values are sent as trimmed strings, and an empty string clears the field on
 * the server, so a cleared input really does clear the stored value.
 */
export default function EditStaffProfileModal({
  isOpen,
  staffProfile,
  saving,
  onClose,
  onSave,
}: {
  isOpen: boolean;
  staffProfile: StaffProfile | null;
  saving: boolean;
  onClose: () => void;
  onSave: (values: { phone: string; designation: string; officeLocation: string; bio: string; linkedinUrl: string; expertise: string[] }) => void;
}) {
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Re-seed the form each time the modal opens so it never shows stale state
  // from a previous edit or from the profile that has since changed.
  useEffect(() => {
    if (!isOpen) return;
    setForm({
      phone: staffProfile?.phone ?? '',
      designation: staffProfile?.designation ?? '',
      officeLocation: staffProfile?.officeLocation ?? '',
      bio: staffProfile?.bio ?? '',
      linkedinUrl: staffProfile?.linkedinUrl ?? '',
      expertise: staffProfile?.expertise ?? [],
    });
    setErrors({});
  }, [isOpen, staffProfile]);

  const validate = () => {
    const next: Record<string, string> = {};
    if (form.phone.trim() !== '' && form.phone.trim().length < 6) {
      next.phone = 'Enter at least 6 characters, or leave blank to remove.';
    }
    if (form.designation.length > LIMITS.designation) {
      next.designation = `Keep this under ${LIMITS.designation} characters.`;
    }
    if (form.officeLocation.length > LIMITS.officeLocation) {
      next.officeLocation = `Keep this under ${LIMITS.officeLocation} characters.`;
    }
    if (form.bio.length > LIMITS.bio) {
      next.bio = `Keep this under ${LIMITS.bio} characters.`;
    }
    const url = form.linkedinUrl.trim();
    if (url !== '') {
      if (!/^https?:\/\/\S+$/i.test(url)) {
        next.linkedinUrl = 'Enter a full link starting with http:// or https://';
      } else {
        try {
          const parsed = new URL(url);
          if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
            next.linkedinUrl = 'Only http:// and https:// links are supported.';
          }
        } catch {
          next.linkedinUrl = 'That does not look like a valid link.';
        }
      }
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    onSave({
      phone: form.phone.trim(),
      designation: form.designation.trim(),
      officeLocation: form.officeLocation.trim(),
      bio: form.bio.trim(),
      linkedinUrl: form.linkedinUrl.trim(),
      expertise: form.expertise,
    });
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Edit professional profile"
      description="These details are visible to students and departments. Leave a field blank to remove it."
      size="md"
      actions={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={saving}>
            {saving ? 'Saving...' : 'Save changes'}
          </Button>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Input
            id="staff-phone"
            label="Phone"
            icon={<Phone size={16} />}
            placeholder="e.g. +91 98765 43210"
            maxLength={LIMITS.phone}
            value={form.phone}
            error={errors.phone}
            onChange={(e) => setForm({ ...form, phone: e.target.value })}
          />
          <Input
            id="staff-designation"
            label="Designation"
            icon={<Briefcase size={16} />}
            placeholder="e.g. Assistant Placement Officer"
            maxLength={LIMITS.designation}
            value={form.designation}
            error={errors.designation}
            onChange={(e) => setForm({ ...form, designation: e.target.value })}
          />
        </div>

        <Input
          id="staff-office"
          label="Office location"
          icon={<MapPin size={16} />}
          placeholder="e.g. Block C, Room 214"
          maxLength={LIMITS.officeLocation}
          value={form.officeLocation}
          error={errors.officeLocation}
          onChange={(e) => setForm({ ...form, officeLocation: e.target.value })}
        />

        <Textarea
          id="staff-bio"
          label="Professional bio"
          rows={4}
          placeholder="A short introduction to your role and how you support students."
          maxLength={LIMITS.bio}
          value={form.bio}
          error={errors.bio}
          onChange={(e) => setForm({ ...form, bio: e.target.value })}
        />
        <p className="-mt-2 text-[12px] text-neutral-500 text-right">
          {form.bio.length} / {LIMITS.bio}
        </p>

        <Input
          id="staff-linkedin"
          label="LinkedIn profile"
          icon={<ExternalLink size={16} />}
          type="url"
          placeholder="https://www.linkedin.com/in/your-name"
          maxLength={LIMITS.linkedinUrl}
          value={form.linkedinUrl}
          error={errors.linkedinUrl}
          onChange={(e) => setForm({ ...form, linkedinUrl: e.target.value })}
        />

        <ExpertiseTagEditor
          value={form.expertise}
          onChange={(expertise) => setForm({ ...form, expertise })}
          disabled={saving}
        />
      </form>
    </Modal>
  );
}

/** Exported for the page's own reset handling. */
export { BLANK as STAFF_PROFILE_FORM_BLANK };
