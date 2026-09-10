import { useAuth } from '../context/AuthContext';
import { useDevMode, type PreviewRole } from '../context/DevModeContext';

export type EffectiveRole = PreviewRole;

export function useEffectiveRole(): EffectiveRole | null {
  const { user, isAuthenticated } = useAuth();
  const { isPreviewing, previewRole } = useDevMode();

  if (isAuthenticated && user?.role) {
    return user.role as EffectiveRole;
  }
  if (isPreviewing && previewRole) {
    return previewRole;
  }
  return null;
}
