import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui';
import { FileQuestion } from 'lucide-react';

export default function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4 relative overflow-hidden">
      <div className="absolute top-[-120px] left-[-80px] w-[400px] h-[400px] rounded-full bg-primary-500/[0.04] blur-[80px] pointer-events-none" />
      <div className="absolute bottom-[-100px] right-[-60px] w-[350px] h-[350px] rounded-full bg-accent-300/[0.04] blur-[80px] pointer-events-none" />

      <div className="text-center max-w-sm relative z-10 animate-fadeInScale">
        <div className="glass rounded-[20px] border border-white/50 shadow-overlay p-10">
          <p className="text-[72px] font-bold leading-none text-primary-500/[0.12] select-none">
            404
          </p>
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-primary-50 border border-primary-100 -mt-2 mb-4">
            <FileQuestion size={26} className="text-primary-400" />
          </div>
          <h1 className="text-[26px] font-semibold text-neutral-900">Page Not Found</h1>
          <p className="mt-2 text-[15px] text-neutral-500 leading-relaxed">
            The page you're looking for doesn't exist or has been moved.
          </p>
          <div className="mt-6">
            <Button onClick={() => navigate('/')}>Back to Dashboard</Button>
          </div>
        </div>
      </div>
    </div>
  );
}
