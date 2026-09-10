import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useDevMode } from '../context/DevModeContext';
import { Skeleton } from './ui';

export default function ProtectedRoute() {
  const { isAuthenticated, loading } = useAuth();
  const { isPreviewing } = useDevMode();

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-background relative overflow-hidden">
        <div className="absolute top-[-120px] left-[-80px] w-[400px] h-[400px] rounded-full bg-primary-500/[0.04] blur-[80px] pointer-events-none" />
        <div className="absolute bottom-[-100px] right-[-60px] w-[350px] h-[350px] rounded-full bg-accent-300/[0.04] blur-[80px] pointer-events-none" />

        <div className="glass rounded-[20px] border border-white/50 shadow-overlay p-10 flex flex-col items-center gap-4 relative z-10 animate-fadeInScale">
          <div className="flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 text-white font-bold text-lg shadow-soft">
            PP
          </div>
          <Skeleton className="h-4 w-32" />
        </div>
      </div>
    );
  }

  if (!isAuthenticated && !isPreviewing) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
