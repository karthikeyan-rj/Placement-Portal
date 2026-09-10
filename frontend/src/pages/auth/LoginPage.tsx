import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Button, Input } from '../../components/ui';
import { LiquidBlob } from '../../components/ui/Liquid';
import { AlertCircle, Mail, Lock, GraduationCap, ChevronDown } from 'lucide-react';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const registered = searchParams.get('registered') === 'true';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(email, password);
      navigate('/dashboard');
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      if (!e.response) {
        setError('Unable to connect to the server. Please check your connection and try again.');
      } else {
        setError(e.response.data?.message || 'Invalid email or password.');
      }
    } finally {
      setLoading(false);
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
              { label: 'Placement Officer', active: true },
              { label: 'Placement Coordinator' },
              { label: 'Placement Representative' },
              { label: 'Students' },
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

        <div className="w-full max-w-[420px] relative z-10 animate-fadeInScale">
          <div className="lg:hidden text-center mb-8">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-br from-primary-500 to-primary-600 text-white mb-3 shadow-soft">
              <GraduationCap size={24} />
            </div>
          </div>

          <div className="text-center mb-8 animate-slideUp">
            <h1 className="text-[30px] font-bold tracking-tight text-neutral-900">
              Welcome back
            </h1>
            <p className="mt-2 text-[15px] leading-relaxed text-text-secondary">
              Sign in to continue to the Placement Portal.
            </p>
          </div>

          <div className="bg-white rounded-[18px] border border-neutral-200/60 shadow-card p-8 animate-fadeIn">
            {registered && (
              <div className="mb-5 flex items-start gap-2.5 rounded-[10px] bg-success-50 p-3 border border-success-100 animate-slideUp">
                <svg
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="text-success-600 shrink-0 mt-0.5"
                >
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                  <path d="m9 11 3 3L22 4" />
                </svg>
                <p className="text-[14px] text-success-700">
                  Account created successfully. Please sign in.
                </p>
              </div>
            )}

            <form onSubmit={handleSubmit} className="flex flex-col" style={{ gap: '20px' }}>
              {error && (
                <div className="flex items-start gap-2.5 rounded-[10px] bg-danger-50 p-3 border border-danger-100 animate-slideUp">
                  <AlertCircle size={18} className="text-danger-500 shrink-0 mt-0.5" />
                  <p className="text-[14px] text-danger-700">{error}</p>
                </div>
              )}

              <Input
                label="Email address"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@tce.edu"
                icon={<Mail size={18} />}
                required
                autoComplete="email"
              />
              <Input
                label="Password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter your password"
                icon={<Lock size={18} />}
                required
                autoComplete="current-password"
              />

              <Button type="submit" loading={loading} className="w-full mt-1">
                Sign In
              </Button>
            </form>

            <div className="mt-6 border-t border-neutral-100 pt-5 text-center">
              <p className="text-[14px] text-text-secondary">
                New student?{' '}
                <Link
                  to="/register"
                  className="font-semibold text-primary-600 hover:text-primary-700 transition-colors"
                >
                  Create an account
                </Link>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
