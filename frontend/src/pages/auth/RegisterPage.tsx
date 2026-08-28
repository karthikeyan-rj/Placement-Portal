import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi, publicApi } from '../../api/api';
import { Button, Input, Select } from '../../components/ui';
import type { Department } from '../../types';
import { getErrorMessage } from '../../api/axios';
import { AlertCircle } from 'lucide-react';

interface FormState {
  name: string;
  registerNumber: string;
  email: string;
  departmentId: string;
  batch: string;
  password: string;
  confirmPassword: string;
}

const emptyForm: FormState = {
  name: '',
  registerNumber: '',
  email: '',
  departmentId: '',
  batch: '',
  password: '',
  confirmPassword: '',
};

export default function RegisterPage() {
  const [form, setForm] = useState<FormState>(emptyForm);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [departments, setDepartments] = useState<Department[]>([]);
  const [deptLoading, setDeptLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    publicApi
      .departments()
      .then((res) => {
        const data = res.data;
        setDepartments(Array.isArray(data) ? data : (data as any)?.data ?? []);
      })
      .catch(() => setError('Could not load departments. Please refresh and try again.'))
      .finally(() => setDeptLoading(false));
  }, []);

  const currentYear = new Date().getFullYear();
  const batchOptions = [currentYear, currentYear + 1, currentYear + 2, currentYear + 3].map(
    (y) => ({ label: String(y), value: String(y) })
  );

  const validate = (): boolean => {
    const e: Record<string, string> = {};
    if (!form.name.trim()) e.name = 'Full name is required.';
    if (!form.registerNumber.trim()) e.registerNumber = 'Register number is required.';
    if (!form.email.trim()) e.email = 'Email is required.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) e.email = 'Enter a valid email address.';
    if (!form.departmentId) e.departmentId = 'Select a department.';
    if (!form.password) e.password = 'Password is required.';
    else if (form.password.length < 6) e.password = 'Password must be at least 6 characters.';
    if (form.confirmPassword !== form.password) e.confirmPassword = 'Passwords do not match.';
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
        name: form.name.trim(),
        registerNumber: form.registerNumber.trim(),
        email: form.email.trim(),
        departmentId: Number(form.departmentId),
        batch: form.batch || undefined,
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
    <div className="flex flex-col items-center px-4 py-14 bg-background min-h-[calc(100vh-64px)]">
      <div className="w-full max-w-[680px]">
        <div className="text-center mb-8">
          <h1 className="text-[30px] font-bold tracking-tight text-neutral-900">
            Create your student account
          </h1>
          <p className="mt-2 text-[15px] leading-relaxed text-neutral-500">
            Register to access placement opportunities and communication.
          </p>
        </div>

        <div className="bg-white rounded-[14px] border border-border shadow-card p-8">
          <form onSubmit={handleSubmit} className="flex flex-col gap-6">
            {error && (
              <div className="flex items-start gap-2.5 rounded-[8px] bg-danger-50 p-3 border border-danger-100">
                <AlertCircle size={18} className="text-danger-500 shrink-0 mt-0.5" />
                <p className="text-[14px] text-danger-700">{error}</p>
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              <Input
                label="Full Name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="Your full name"
                error={errors.name}
                required
                autoComplete="name"
              />
              <Input
                label="Register Number"
                value={form.registerNumber}
                onChange={(e) => setForm({ ...form, registerNumber: e.target.value })}
                placeholder="e.g. 22CS001"
                error={errors.registerNumber}
                required
                autoComplete="off"
              />
              <Input
                label="College Email"
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                placeholder="you@college.edu"
                error={errors.email}
                required
                autoComplete="email"
              />
              <Select
                label="Department"
                options={departments.map((d) => ({ label: d.name, value: d.id }))}
                placeholder={deptLoading ? 'Loading…' : 'Select department'}
                value={form.departmentId}
                onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                error={errors.departmentId}
                required
              />
              <Select
                label="Batch / Graduation Year"
                options={batchOptions}
                placeholder="Select year"
                value={form.batch}
                onChange={(e) => setForm({ ...form, batch: e.target.value })}
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 pt-2 border-t border-neutral-100">
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
                onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
                placeholder="Re-enter your password"
                error={errors.confirmPassword}
                required
                autoComplete="new-password"
              />
            </div>

            <Button type="submit" loading={submitting} className="w-full">
              Create Account
            </Button>
          </form>

          <div className="mt-7 border-t border-neutral-100 pt-5 text-center">
            <p className="text-[14px] text-neutral-600">
              Already have an account?{' '}
              <Link to="/login" className="font-semibold text-primary-600 hover:text-primary-700">
                Sign in
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
