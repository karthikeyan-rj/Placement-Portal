import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui';
import { ShieldOff } from 'lucide-react';

export default function UnauthorizedPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex items-center justify-center bg-neutral-50 px-4">
      <div className="text-center max-w-sm">
        <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-danger-50 border border-danger-100 mb-4">
          <ShieldOff size={26} className="text-danger-500" />
        </div>
        <h1 className="text-[30px] font-semibold text-neutral-900">Access Denied</h1>
        <p className="mt-1 text-[15px] font-semibold text-danger-600">403 — Forbidden</p>
        <p className="mt-2 text-[15px] text-neutral-500 leading-relaxed">
          You don't have permission to view this page. Contact your administrator if you believe this is an error.
        </p>
        <div className="mt-6">
          <Button onClick={() => navigate('/')}>Return to Dashboard</Button>
        </div>
      </div>
    </div>
  );
}
