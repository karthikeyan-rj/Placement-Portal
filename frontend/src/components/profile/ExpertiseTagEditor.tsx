import { useState, type KeyboardEvent } from 'react';
import { X } from 'lucide-react';
import { notify } from '../ui';

const MAX_TAGS = 12;
const MAX_ITEM_LENGTH = 60;

/**
 * Tag editor for the staff expertise list.
 *
 * Entries are entered one at a time and become removable chips. Nothing is
 * persisted here - the parent owns the value and the save.
 *
 * Accessibility: every chip has a real focusable remove button with a label
 * that names the tag, and the text field is wired to a label and described by
 * the live count.
 */
export default function ExpertiseTagEditor({
  value,
  onChange,
  disabled = false,
}: {
  value: string[];
  onChange: (next: string[]) => void;
  disabled?: boolean;
}) {
  const [draft, setDraft] = useState('');

  const atLimit = value.length >= MAX_TAGS;

  const addTag = () => {
    const trimmed = draft.trim();
    if (!trimmed) return;
    if (atLimit) {
      notify.error(`You can list at most ${MAX_TAGS} areas of expertise`);
      return;
    }
    if (trimmed.length > MAX_ITEM_LENGTH) {
      notify.error(`Each area of expertise must be at most ${MAX_ITEM_LENGTH} characters`);
      return;
    }
    if (value.some((v) => v.toLowerCase() === trimmed.toLowerCase())) {
      notify.error('That area of expertise is already listed');
      setDraft('');
      return;
    }
    onChange([...value, trimmed]);
    setDraft('');
  };

  const removeTag = (tag: string) => {
    onChange(value.filter((v) => v !== tag));
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      // Keep the tag on the same line instead of submitting the surrounding form.
      e.preventDefault();
      addTag();
    } else if (e.key === 'Backspace' && draft === '' && value.length > 0) {
      onChange(value.slice(0, -1));
    }
  };

  return (
    <div>
      <label htmlFor="expertise-input" className="block text-[13px] font-medium text-neutral-700 mb-1.5">
        Areas of expertise
      </label>

      {value.length > 0 && (
        <ul className="flex flex-wrap gap-1.5 mb-2.5" aria-label="Added areas of expertise">
          {value.map((tag) => (
            <li key={tag}>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-primary-50 text-primary-700 text-[12.5px] font-medium pl-2.5 pr-1 py-1">
                {tag}
                <button
                  type="button"
                  onClick={() => removeTag(tag)}
                  disabled={disabled}
                  aria-label={`Remove ${tag}`}
                  className="inline-flex items-center justify-center w-4 h-4 rounded-full text-primary-600 hover:bg-primary-100 hover:text-primary-800 focus:outline-none focus:ring-2 focus:ring-primary-500/40 disabled:opacity-50"
                >
                  <X size={12} aria-hidden="true" />
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}

      <div className="flex gap-2">
        <input
          id="expertise-input"
          type="text"
          value={draft}
          disabled={disabled || atLimit}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={atLimit ? `Maximum of ${MAX_TAGS} reached` : 'e.g. Java, Campus Recruitment'}
          aria-describedby="expertise-count"
          className="flex-1 min-w-0 px-3.5 py-2.5 text-[14px] rounded-[10px] bg-white text-neutral-900 border border-border-strong placeholder:text-neutral-400 focus:outline-none focus:border-primary-400 focus:ring-[3px] focus:ring-primary-500/10 transition-all duration-150 disabled:bg-neutral-50 disabled:text-neutral-400"
        />
        <button
          type="button"
          onClick={addTag}
          disabled={disabled || atLimit || draft.trim() === ''}
          className="shrink-0 px-3.5 py-2.5 rounded-[10px] border border-neutral-300 text-[13.5px] font-medium text-neutral-700 hover:bg-neutral-50 disabled:opacity-50 disabled:hover:bg-transparent focus:outline-none focus:ring-2 focus:ring-primary-500/40"
        >
          Add
        </button>
      </div>

      <p id="expertise-count" className="mt-1.5 text-[12px] text-neutral-500">
        {value.length} of {MAX_TAGS} added. Press Enter to add an entry.
      </p>
    </div>
  );
}
