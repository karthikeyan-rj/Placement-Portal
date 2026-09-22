import { useState } from 'react';
import { authApi } from '../../api/api';
import { getErrorMessage } from '../../api/axios';
import { Button, Input, notify } from '../ui';
import { Lock, Eye, AlertCircle, CheckCircle2 } from 'lucide-react';

interface PasswordForm {
  current: string;
  next: string;
  confirm: string;
}

export default function ChangePasswordCard() {
  const [pw, setPw] = useState<PasswordForm>({ current: '', next: '', confirm: '' });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [showNew, setShowNew] = useState(false);

  const submit = async () => {
    setError(null);
    if (!pw.current) { setError('Current password is required.'); return; }
    if (!pw.next) { setError('New password is required.'); return; }
    if (pw.next.length < 6) { setError('New password must be at least 6 characters.'); return; }
    if (pw.next !== pw.confirm) { setError('New passwords do not match.'); return; }
    setSaving(true);
    try {
      await authApi.changePassword(pw.current, pw.next);
      notify.success('Password changed successfully.');
      setSuccess(true);
      setPw({ current: '', next: '', confirm: '' });
    } catch (e) {
      const msg = getErrorMessage(e);
      setError(msg === 'Validation failed' ? 'New password must be at least 6 characters.' : (msg || 'Failed to change password.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-white rounded-[16px] border border-neutral-200/80 shadow-soft p-4 sm:p-5">
      <div className="flex items-center gap-2 mb-4">
        <Lock size={15} className="text-neutral-500" />
        <h4 className="text-[15px] font-semibold text-neutral-900">Change Password</h4>
      </div>

      <div className="space-y-4">
        <Input
          label="Current Password"
          type="password"
          autoComplete="current-password"
          value={pw.current}
          onChange={(e) => { setPw({ ...pw, current: e.target.value }); setSuccess(false); }}
          placeholder="Enter your current password"
        />
        <div className="relative">
          <Input
            label="New Password"
            type={showNew ? 'text' : 'password'}
            autoComplete="new-password"
            value={pw.next}
            onChange={(e) => { setPw({ ...pw, next: e.target.value }); setSuccess(false); }}
            placeholder="At least 6 characters"
          />
          <button
            type="button"
            onClick={() => setShowNew(!showNew)}
            className="absolute right-3 top-[38px] text-neutral-500 hover:text-neutral-600"
            aria-label={showNew ? 'Hide new password' : 'Show new password'}
          >
            <Eye size={16} />
          </button>
        </div>
        <Input
          label="Confirm New Password"
          type="password"
          autoComplete="new-password"
          value={pw.confirm}
          onChange={(e) => { setPw({ ...pw, confirm: e.target.value }); setSuccess(false); }}
          placeholder="Re-enter your new password"
        />

        {error && (
          <div className="flex items-start gap-2 rounded-[10px] bg-danger-50 border border-danger-100 px-3.5 py-2.5">
            <AlertCircle size={15} className="text-danger-500 mt-0.5 shrink-0" />
            <p className="text-[13px] font-medium text-danger-600">{error}</p>
          </div>
        )}
        {success && (
          <div className="flex items-start gap-2 rounded-[10px] bg-success-50 border border-success-100 px-3.5 py-2.5">
            <CheckCircle2 size={15} className="text-success-500 mt-0.5 shrink-0" />
            <p className="text-[13px] font-medium text-success-700">Password changed successfully.</p>
          </div>
        )}

        <Button onClick={submit} loading={saving} disabled={saving} className="w-full sm:w-auto">
          <Lock size={14} /> Update Password
        </Button>
      </div>
    </div>
  );
}
