import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../../api/api';
import { Button, Input } from '../../components/ui';
import { getErrorMessage } from '../../api/axios';
import {
  AlertCircle,
  GraduationCap,
  ChevronDown,
  Mail,
  Lock,
  ArrowRight,
  RotateCcw,
  CheckCircle2,
  KeyRound,
  ShieldCheck,
  Check,
} from 'lucide-react';
import { LiquidBlob } from '../../components/ui/Liquid';

type RegistrationStep = 'request' | 'verify' | 'password' | 'success';

export default function RegisterPage() {
  const [step, setStep] = useState<RegistrationStep>('request');

  // Step 1: Request Code
  const [email, setEmail] = useState('');
  const [registerNumber, setRegisterNumber] = useState('');
  const [step1Errors, setStep1Errors] = useState<Record<string, string>>({});
  const [maskedEmail, setMaskedEmail] = useState('');
  const [isAlreadyRegistered, setIsAlreadyRegistered] = useState(false);

  // Step 2: Verification Code
  const [digits, setDigits] = useState<string[]>(['', '', '', '', '', '']);
  const digitRefs = useRef<(HTMLInputElement | null)[]>([]);
  const [cooldown, setCooldown] = useState(0);
  const [resending, setResending] = useState(false);
  const [registrationToken, setRegistrationToken] = useState('');

  // Step 3: Password
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [step3Errors, setStep3Errors] = useState<Record<string, string>>({});

  // Step 4: Success countdown
  const [countdown, setCountdown] = useState(3);

  // General state
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  // Cooldown countdown effect
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((c) => {
        if (c <= 1) {
          clearInterval(timer);
          return 0;
        }
        return c - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  // Auto-redirect on success
  useEffect(() => {
    if (step !== 'success') return;
    const interval = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          clearInterval(interval);
          navigate('/login?registered=true');
          return 0;
        }
        return c - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [step, navigate]);

  // Step 1: Validate & Request Code
  const handleRequestCode = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsAlreadyRegistered(false);

    const errs: Record<string, string> = {};
    const trimmedEmail = email.trim().toLowerCase();
    const trimmedRegNo = registerNumber.trim();

    if (!trimmedEmail) {
      errs.email = 'College email is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      errs.email = 'Enter a valid email address.';
    } else if (!trimmedEmail.endsWith('tce.edu')) {
      errs.email = 'Must be an official TCE institutional email (@*.tce.edu).';
    }

    if (!trimmedRegNo) {
      errs.registerNumber = 'Register number is required.';
    }

    setStep1Errors(errs);
    if (Object.keys(errs).length > 0) return;

    setSubmitting(true);
    try {
      const res = await authApi.requestCode({
        email: trimmedEmail,
        registerNumber: trimmedRegNo,
      });
      setMaskedEmail(res.data.maskedEmail || maskLocal(trimmedEmail));
      setCooldown(60);
      setDigits(['', '', '', '', '', '']);
      setStep('verify');
    } catch (err: unknown) {
      const msg = getErrorMessage(err);
      if (
        msg.toLowerCase().includes('already registered') ||
        (err as { response?: { status?: number } }).response?.status === 409
      ) {
        setIsAlreadyRegistered(true);
        setError('This account is already registered. Sign in instead.');
      } else {
        setError(msg || "We couldn't send the verification email. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  // Resend code handler
  const handleResendCode = async () => {
    if (cooldown > 0 || resending) return;
    setError('');
    setResending(true);
    try {
      const res = await authApi.requestCode({
        email: email.trim().toLowerCase(),
        registerNumber: registerNumber.trim(),
      });
      setMaskedEmail(res.data.maskedEmail || maskLocal(email.trim().toLowerCase()));
      setCooldown(60);
      setDigits(['', '', '', '', '', '']);
      if (digitRefs.current[0]) {
        digitRefs.current[0].focus();
      }
    } catch (err) {
      setError(getErrorMessage(err) || "Failed to resend verification code. Please try again.");
    } finally {
      setResending(false);
    }
  };

  // Step 2: Handle digit input navigation
  const handleDigitChange = (index: number, val: string) => {
    const clean = val.replace(/\D/g, '');
    const newDigits = [...digits];

    if (clean.length > 1) {
      // User pasted multiple characters into a digit box
      const chars = clean.slice(0, 6).split('');
      for (let i = 0; i < 6; i++) {
        newDigits[i] = chars[i] || '';
      }
      setDigits(newDigits);
      const nextIndex = Math.min(chars.length, 5);
      digitRefs.current[nextIndex]?.focus();
      return;
    }

    newDigits[index] = clean;
    setDigits(newDigits);

    if (clean && index < 5) {
      digitRefs.current[index + 1]?.focus();
    }
  };

  const handleDigitKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      digitRefs.current[index - 1]?.focus();
    }
  };

  const handleDigitPaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasteData = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (!pasteData) return;
    const newDigits = [...digits];
    for (let i = 0; i < 6; i++) {
      newDigits[i] = pasteData[i] || '';
    }
    setDigits(newDigits);
    const targetIdx = Math.min(pasteData.length, 5);
    digitRefs.current[targetIdx]?.focus();
  };

  // Step 2: Verify Code
  const handleVerifyCode = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const code = digits.join('');
    if (code.length < 6) {
      setError('Please enter the complete 6-digit verification code.');
      return;
    }

    setSubmitting(true);
    try {
      const res = await authApi.verifyCode({
        email: email.trim().toLowerCase(),
        registerNumber: registerNumber.trim(),
        code,
      });
      setRegistrationToken(res.data.registrationToken);
      setStep('password');
    } catch (err) {
      setError(getErrorMessage(err) || 'Invalid verification code.');
    } finally {
      setSubmitting(false);
    }
  };

  // Step 3: Complete Registration
  const handleCompleteRegistration = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const errs: Record<string, string> = {};
    if (!password) {
      errs.password = 'Password is required.';
    } else if (password.length < 8) {
      errs.password = 'Password must be at least 8 characters long.';
    }

    if (!confirmPassword) {
      errs.confirmPassword = 'Confirm your password.';
    } else if (confirmPassword !== password) {
      errs.confirmPassword = 'Passwords do not match.';
    }

    setStep3Errors(errs);
    if (Object.keys(errs).length > 0) return;

    setSubmitting(true);
    try {
      await authApi.completeRegistration({
        registrationToken,
        password,
        confirmPassword,
      });
      setStep('success');
    } catch (err) {
      setError(getErrorMessage(err) || 'Registration failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  function maskLocal(em: string): string {
    const at = em.indexOf('@');
    if (at <= 1) return em;
    const name = em.substring(0, at);
    const domain = em.substring(at);
    return name.charAt(0) + '*'.repeat(Math.max(1, name.length - 1)) + domain;
  }

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
                <div
                  className={`flex h-10 w-56 items-center justify-center rounded-[10px] text-[13px] font-medium transition-colors ${
                    role.active
                      ? 'bg-white/10 text-white border border-white/10'
                      : 'bg-white/5 text-white/70 border border-white/5'
                  }`}
                >
                  {role.active ? (
                    <span className="flex items-center gap-2">
                      <span className="h-1.5 w-1.5 rounded-full bg-primary-400" />
                      {role.label}
                    </span>
                  ) : (
                    role.label
                  )}
                </div>
                {i < 3 && <ChevronDown size={14} className="text-white/20" />}
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

          {/* Stepper Progress Bar */}
          {step !== 'success' && (
            <div className="mb-8">
              <div className="flex items-center justify-between text-[12px] font-semibold text-text-secondary uppercase tracking-wider mb-2">
                <span className={step === 'request' ? 'text-primary-600' : 'text-neutral-500'}>
                  1. Details
                </span>
                <span className={step === 'verify' ? 'text-primary-600' : 'text-neutral-500'}>
                  2. Verification
                </span>
                <span className={step === 'password' ? 'text-primary-600' : 'text-neutral-500'}>
                  3. Password
                </span>
              </div>
              <div className="h-1.5 w-full bg-neutral-200/80 rounded-full overflow-hidden flex">
                <div
                  className="bg-primary-600 h-full transition-all duration-300 ease-out"
                  style={{
                    width:
                      step === 'request'
                        ? '33.3%'
                        : step === 'verify'
                        ? '66.6%'
                        : '100%',
                  }}
                />
              </div>
            </div>
          )}

          {/* Card Container */}
          <div className="bg-white rounded-[18px] border border-neutral-200/60 shadow-card p-8 animate-fadeIn">
            {/* STEP 1: Request Code */}
            {step === 'request' && (
              <div>
                <div className="text-center mb-6">
                  <h1 className="text-[26px] font-bold tracking-tight text-neutral-900">
                    Create Student Account
                  </h1>
                  <p className="mt-2 text-[14.5px] leading-relaxed text-text-secondary">
                    Enter your registered college email and register number to receive a verification code.
                  </p>
                </div>

                {error && (
                  <div
                    className={`flex items-start gap-2.5 rounded-[12px] p-3.5 mb-5 border animate-slideUp ${
                      isAlreadyRegistered
                        ? 'bg-primary-50/70 border-primary-200/80 text-primary-800'
                        : 'bg-danger-50 border-danger-100 text-danger-700'
                    }`}
                  >
                    <AlertCircle
                      size={18}
                      className={`shrink-0 mt-0.5 ${
                        isAlreadyRegistered ? 'text-primary-600' : 'text-danger-500'
                      }`}
                    />
                    <div className="flex-1">
                      <p className="text-[14px] font-medium">{error}</p>
                      {isAlreadyRegistered && (
                        <div className="mt-3">
                          <Link
                            to="/login"
                            className="inline-flex items-center gap-1.5 rounded-[8px] bg-primary-600 px-3.5 py-1.5 text-[13px] font-semibold text-white shadow-soft hover:bg-primary-700 transition-colors"
                          >
                            Go to Sign In
                            <ArrowRight size={14} />
                          </Link>
                        </div>
                      )}
                    </div>
                  </div>
                )}

                <form onSubmit={handleRequestCode} className="space-y-5">
                  <Input
                    label="College Email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@student.tce.edu"
                    error={step1Errors.email}
                    required
                    autoComplete="email"
                    icon={<Mail size={18} />}
                  />

                  <Input
                    label="Register Number"
                    value={registerNumber}
                    onChange={(e) => setRegisterNumber(e.target.value)}
                    placeholder="e.g. 24C21031"
                    error={step1Errors.registerNumber}
                    required
                    autoComplete="off"
                    icon={<KeyRound size={18} />}
                  />

                  <p className="text-[13px] text-text-secondary leading-relaxed bg-neutral-50 p-3 rounded-[10px] border border-neutral-100">
                    We'll send a one-time 6-digit verification code to your registered college email address.
                  </p>

                  <Button type="submit" loading={submitting} className="w-full">
                    Send Verification Code
                    <ArrowRight size={16} />
                  </Button>
                </form>

                <div className="mt-6 border-t border-neutral-100 pt-5 text-center">
                  <p className="text-[14px] text-text-secondary">
                    Already have an account?{' '}
                    <Link
                      to="/login"
                      className="font-semibold text-primary-600 hover:text-primary-700 transition-colors"
                    >
                      Sign in
                    </Link>
                  </p>
                </div>
              </div>
            )}

            {/* STEP 2: Verify Code */}
            {step === 'verify' && (
              <div>
                <div className="text-center mb-6">
                  <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-primary-50 text-primary-600 mb-3 border border-primary-100">
                    <ShieldCheck size={26} />
                  </div>
                  <h1 className="text-[24px] font-bold tracking-tight text-neutral-900">
                    Verify Your Email
                  </h1>
                  <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
                    Enter the 6-digit verification code sent to
                  </p>
                  <p className="mt-1 font-semibold text-neutral-900 text-[14.5px]">
                    {maskedEmail}
                  </p>
                </div>

                {error && (
                  <div className="flex items-start gap-2.5 rounded-[12px] bg-danger-50 p-3.5 mb-5 border border-danger-100 text-danger-700 animate-slideUp">
                    <AlertCircle size={18} className="shrink-0 mt-0.5 text-danger-500" />
                    <p className="text-[14px]">{error}</p>
                  </div>
                )}

                <form onSubmit={handleVerifyCode} className="space-y-6">
                  {/* 6 Digit Input Group */}
                  <div className="flex justify-center gap-2.5 sm:gap-3">
                    {digits.map((digit, idx) => (
                      <input
                        key={idx}
                        ref={(el) => {
                          digitRefs.current[idx] = el;
                        }}
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        maxLength={1}
                        value={digit}
                        onChange={(e) => handleDigitChange(idx, e.target.value)}
                        onKeyDown={(e) => handleDigitKeyDown(idx, e)}
                        onPaste={handleDigitPaste}
                        className="w-11 h-13 sm:w-12 sm:h-14 text-center font-mono text-[22px] font-bold text-neutral-900 border border-neutral-300 rounded-[12px] focus:outline-none focus:ring-2 focus:ring-primary-500/20 focus:border-primary-600 transition-all bg-neutral-50/50 focus:bg-white"
                        autoFocus={idx === 0}
                      />
                    ))}
                  </div>

                  <Button
                    type="submit"
                    loading={submitting}
                    disabled={digits.join('').length < 6}
                    className="w-full"
                  >
                    Verify Code
                    <ArrowRight size={16} />
                  </Button>

                  {/* Resend & Change Email Actions */}
                  <div className="flex flex-col items-center gap-3 pt-2 text-center">
                    <div className="text-[13.5px] text-text-secondary">
                      Didn't receive the code?{' '}
                      {cooldown > 0 ? (
                        <span className="font-semibold text-neutral-500">
                          Resend in {cooldown}s
                        </span>
                      ) : (
                        <button
                          type="button"
                          onClick={handleResendCode}
                          disabled={resending}
                          className="font-semibold text-primary-600 hover:text-primary-700 transition-colors disabled:opacity-50"
                        >
                          {resending ? 'Sending...' : 'Resend code'}
                        </button>
                      )}
                    </div>

                    <button
                      type="button"
                      onClick={() => {
                        setError('');
                        setStep('request');
                      }}
                      className="inline-flex items-center gap-1.5 text-[13px] text-neutral-500 hover:text-neutral-800 transition-colors"
                    >
                      <RotateCcw size={13} />
                      Change email or register number
                    </button>
                  </div>
                </form>
              </div>
            )}

            {/* STEP 3: Create Password */}
            {step === 'password' && (
              <div>
                <div className="text-center mb-6">
                  <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-success-50 text-success-600 mb-3 border border-success-100">
                    <Lock size={24} />
                  </div>
                  <h1 className="text-[24px] font-bold tracking-tight text-neutral-900">
                    Create Your Password
                  </h1>
                  <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
                    Set a secure password for your account to finish registration.
                  </p>
                </div>

                {error && (
                  <div className="flex items-start gap-2.5 rounded-[12px] bg-danger-50 p-3.5 mb-5 border border-danger-100 text-danger-700 animate-slideUp">
                    <AlertCircle size={18} className="shrink-0 mt-0.5 text-danger-500" />
                    <p className="text-[14px]">{error}</p>
                  </div>
                )}

                <form onSubmit={handleCompleteRegistration} className="space-y-5">
                  <Input
                    label="New Password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="At least 8 characters"
                    error={step3Errors.password}
                    required
                    autoComplete="new-password"
                  />

                  <Input
                    label="Confirm Password"
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Re-enter your password"
                    error={step3Errors.confirmPassword}
                    required
                    autoComplete="new-password"
                  />

                  {/* Password requirements helper */}
                  <div className="rounded-[10px] bg-neutral-50 p-3 border border-neutral-100 space-y-1.5 text-[12.5px]">
                    <div
                      className={`flex items-center gap-2 ${
                        password.length >= 8 ? 'text-success-700 font-medium' : 'text-neutral-500'
                      }`}
                    >
                      <Check
                        size={14}
                        className={password.length >= 8 ? 'text-success-600' : 'text-neutral-300'}
                      />
                      At least 8 characters long
                    </div>
                    <div
                      className={`flex items-center gap-2 ${
                        password && confirmPassword && password === confirmPassword
                          ? 'text-success-700 font-medium'
                          : 'text-neutral-500'
                      }`}
                    >
                      <Check
                        size={14}
                        className={
                          password && confirmPassword && password === confirmPassword
                            ? 'text-success-600'
                            : 'text-neutral-300'
                        }
                      />
                      Passwords match
                    </div>
                  </div>

                  <Button type="submit" loading={submitting} className="w-full">
                    Create Account
                    <ArrowRight size={16} />
                  </Button>
                </form>
              </div>
            )}

            {/* STEP 4: Success State */}
            {step === 'success' && (
              <div className="text-center py-4 animate-scaleUp">
                <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-success-50 text-success-600 mb-4 border border-success-200/80 shadow-soft">
                  <CheckCircle2 size={36} />
                </div>
                <h1 className="text-[26px] font-bold tracking-tight text-neutral-900">
                  Account Created!
                </h1>
                <p className="mt-2.5 text-[14.5px] leading-relaxed text-text-secondary max-w-[360px] mx-auto">
                  Your student account is now active. You can now sign in using your college email and password.
                </p>

                <div className="mt-8 space-y-4">
                  <Link
                    to="/login?registered=true"
                    className="inline-flex items-center justify-center w-full rounded-[12px] bg-primary-600 px-5 py-3 text-[14.5px] font-semibold text-white shadow-soft hover:bg-primary-700 transition-colors"
                  >
                    Sign In Now
                    <ArrowRight size={16} className="ml-2" />
                  </Link>

                  <p className="text-[13px] text-text-secondary">
                    Redirecting to login in{' '}
                    <span className="font-semibold text-neutral-900">{countdown}s</span>...
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
