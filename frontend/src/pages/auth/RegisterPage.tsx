import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../../api/api';
import { Button, Input } from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import { AlertCircle, KeyRound, GraduationCap, ChevronDown } from 'lucide-react';
import { LiquidBlob } from '../../components/ui/Liquid';

interface FormState {
  registerNumber: string;
  email: string;
  accessCode: string;
  password: string;
  confirmPassword: string;
}

const emptyForm: FormState = {
  registerNumber: '',
  email: '',
  accessCode: '',
  password: '',
  confirmPassword: '',
};

export default function RegisterPage() {
  const [form, setForm] = useState<FormState>(emptyForm);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const validate = (): boolean => {
    const e: Record<string, string> = {};
    if (!form.registerNumber.trim())
      e.registerNumber = 'Register number is required.';
    if (!form.email.trim()) e.email = 'Email is required.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
      e.email = 'Enter a valid email address.';
    if (!form.accessCode.trim()) e.accessCode = 'Access code is required.';
    if (!form.password) e.password = 'Password is required.';
    else if (form.password.length < 6)
      e.password = 'Password must be at least 6 characters.';
    if (form.confirmPassword !== form.password)
      e.confirmPassword = 'Passwords do not match.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setError('');
    if (!validate()) return;
    setSubmitting(true);
    try {
      await authApi.register({
        registerNumber: form.registerNumber.trim(),
        email: form.email.trim(),
        accessCode: form.accessCode.trim(),
        password: form.password,
      });
      navigate('/login?registered=true');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex bg-background">
      {/* Left branding panel — hidden on mobile */}
      <div className="hidden lg:flex lg:w-[480px] xl:w-[540px] relative overflow-hidden bg-gradient-to-br from-[#11152B] via-[#0E1230] to-[#161A3A] flex-col items-center justify-center p-12">
        <LiquidBlob
          color="rgba(102,92,246,0.12)"
          size={400}
          className="!top-[-80px] !left-[-100px]"
          style={{ position: 'absolute' }}
        />
        <LiquidBlob
          color="rgba(56,189,248,0.08)"
          size={300}
          className="!bottom-[-60px] !right-[-80px]"
          style={{ position: 'absolute', animationDelay: '2s' }}
        />
        <div className="relative z-10 text-center animate-fadeIn">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-br from-primary-500 to-primary-600 text-white mb-5 shadow-raised">
            <GraduationCap size={28} />
          </div>
          <h1 className="text-[30px] font-bold text-white leading-tight tracking-tight">
            Placement Portal
          </h1>
          <p className="mt-3 text-[15px] leading-relaxed text-white/70 max-w-[300px] mx-auto">
            Campus placement management, without the chaos.
          </p>

          {/* Role hierarchy */}
          <div className="mt-10 flex flex-col items-center gap-2.5">
            {[
              { label: 'Placement Officer', active: false },
              { label: 'Placement Coordinator', active: false },
              { label: 'Placement Representative', active: false },
              { label: 'Students', active: true },
            ].map((role, i) => (
              <div key={role.label} className="flex flex-col items-center gap-2.5">
                <div className={`flex h-10 w-56 items-center justify-center rounded-[10px] text-[13px] font-medium transition-colors ${
                  role.active
                    ? 'bg-white/10 text-white border border-white/10'
                    : 'bg-white/5 text-white/50 border border-white/5'
                }`}>
                  {role.active ? (
                    <span className="flex items-center gap-2">
                      <span className="h-1.5 w-1.5 rounded-full bg-primary-400" />
                      {role.label}
                    </span>
                  ) : (
                    role.label
                  )}
                </div>
                {i < 3 && (
                  <ChevronDown size={14} className="text-white/20" />
                )}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right form panel */}
      <div className="flex-1 flex items-center justify-center px-5 py-12 relative overflow-hidden">
        <div className="absolute top-[-120px] left-[-80px] w-[400px] h-[400px] rounded-full bg-primary-500/[0.04] blur-[80px] pointer-events-none" />
        <div className="absolute bottom-[-100px] right-[-60px] w-[350px] h-[350px] rounded-full bg-accent-300/[0.04] blur-[80px] pointer-events-none" />

        <div className="w-full max-w-[480px] relative z-10 animate-fadeInScale">
          <div className="lg:hidden text-center mb-8">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-br from-primary-500 to-primary-600 text-white mb-3 shadow-soft">
              <GraduationCap size={24} />
            </div>
          </div>

          <div className="text-center mb-8 animate-slideUp">
            <h1 className="text-[30px] font-bold tracking-tight text-neutral-900">
              Create your student account
            </h1>
            <p className="mt-2 text-[15px] leading-relaxed text-text-secondary">
              Use the access code you received to register for the Placement Portal.
            </p>
          </div>

          <div className="bg-white rounded-[18px] border border-neutral-200/60 shadow-card p-8 animate-fadeIn">
            <form onSubmit={handleSubmit} className="flex flex-col" style={{ gap: '24px' }}>
              {error && (
                <div className="flex items-start gap-2.5 rounded-[10px] bg-danger-50 p-3 border border-danger-100 animate-slideUp">
                  <AlertCircle size={18} className="text-danger-500 shrink-0 mt-0.5" />
                  <p className="text-[14px] text-danger-700">{error}</p>
                </div>
              )}

              {/* Section: Verify your identity */}
              <div>
                <h3 className="text-[13px] font-semibold uppercase tracking-wider text-neutral-400 mb-4">
                  Verify your identity
                </h3>
                <div className="flex flex-col" style={{ gap: '20px' }}>
                  <Input
                    label="College Email"
                    type="email"
                    value={form.email}
                    onChange={(e) => setForm({ ...form, email: e.target.value })}
                    placeholder="you@tce.edu"
                    error={errors.email}
                    required
                    autoComplete="email"
                  />
                  <Input
                    label="Register Number"
                    value={form.registerNumber}
                    onChange={(e) =>
                      setForm({ ...form, registerNumber: e.target.value })
                    }
                    placeholder="e.g. 22CS001"
                    error={errors.registerNumber}
                    required
                    autoComplete="off"
                  />
                  <Input
                    label="Access Code"
                    value={form.accessCode}
                    onChange={(e) => setForm({ ...form, accessCode: e.target.value })}
                    placeholder="Enter the 8-character access code"
                    error={errors.accessCode}
                    required
                    autoComplete="off"
                    icon={<KeyRound size={18} />}
                  />
                  <p className="text-[13px] text-text-secondary -mt-1">
                    Don't have an access code? Contact your Placement Officer (PO).
                  </p>
                </div>
              </div>

              {/* Divider */}
              <div className="border-t border-neutral-100" />

              {/* Section: Create your password */}
              <div>
                <h3 className="text-[13px] font-semibold uppercase tracking-wider text-neutral-400 mb-4">
                  Create your password
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                  <Input
                    label="Password"
                    type="password"
                    value={form.password}
                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                    placeholder="At least 6 characters"
                    error={errors.password}
                    required
                    autoComplete="new-password"
                  />
                  <Input
                    label="Confirm Password"
                    type="password"
                    value={form.confirmPassword}
                    onChange={(e) =>
                      setForm({ ...form, confirmPassword: e.target.value })
                    }
                    placeholder="Re-enter your password"
                    error={errors.confirmPassword}
                    required
                    autoComplete="new-password"
                  />
                </div>
              </div>

              <Button type="submit" loading={submitting} className="w-full">
                Create Account
              </Button>
            </form>

            <div className="mt-6 border-t border-neutral-100 pt-5 text-center">
              <p className="text-[14px] text-text-secondary">
                Already have an account?{' '}
                <Link to="/login" className="font-semibold text-primary-600 hover:text-primary-700 transition-colors">
                  Sign in
                </Link>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
