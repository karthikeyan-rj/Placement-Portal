import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui';
import { FileQuestion } from 'lucide-react';

export default function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex items-center justify-center bg-neutral-50 px-4">
      <div className="text-center max-w-sm">
        <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-neutral-100 border border-neutral-200/80 mb-4">
          <FileQuestion size={26} className="text-neutral-400" />
        </div>
        <h1 className="text-[30px] font-semibold text-neutral-900">Page Not Found</h1>
        <p className="mt-1 text-[15px] font-semibold text-primary-600">404</p>
        <p className="mt-2 text-[15px] text-neutral-500 leading-relaxed">
          The page you're looking for doesn't exist or has been moved.
        </p>
        <div className="mt-6">
          <Button onClick={() => navigate('/')}>Return to Dashboard</Button>
        </div>
      </div>
    </div>
  );
}
