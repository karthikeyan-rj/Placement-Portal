import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Button, Input } from '../../components/ui';
import { AlertCircle, Mail, Lock } from 'lucide-react';

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
    <div className="flex flex-col items-center justify-center px-4 py-16 bg-background min-h-[calc(100vh-64px)]">
      <div className="w-full max-w-[440px]">
        <div className="text-center mb-8">
          <h1 className="text-[30px] font-bold tracking-tight text-neutral-900">
            Welcome back
          </h1>
          <p className="mt-2 text-[15px] leading-relaxed text-neutral-500">
            Sign in to continue to the Placement Portal.
          </p>
        </div>

        <div className="bg-white rounded-[14px] border border-border shadow-card p-8">
          {registered && (
            <div className="mb-5 flex items-start gap-2.5 rounded-[8px] bg-success-50 p-3 border border-success-100">
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

          <form onSubmit={handleSubmit} className="flex flex-col gap-5">
            {error && (
              <div className="flex items-start gap-2.5 rounded-[8px] bg-danger-50 p-3 border border-danger-100">
                <AlertCircle size={18} className="text-danger-500 shrink-0 mt-0.5" />
                <p className="text-[14px] text-danger-700">{error}</p>
              </div>
            )}

            <Input
              label="Email address"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
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

          <div className="mt-7 border-t border-neutral-100 pt-5 text-center">
            <p className="text-[14px] text-neutral-600">
              New student?{' '}
              <Link
                to="/register"
                className="font-semibold text-primary-600 hover:text-primary-700"
              >
                Create an account
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
