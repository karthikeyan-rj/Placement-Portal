import { createContext, useContext, useState, type ReactNode } from 'react';

export type PreviewRole = 'PO' | 'PC' | 'PR' | 'STUDENT';

export const DEV_MODE_ENABLED = import.meta.env.VITE_DEV_MODE === 'true';

const STORAGE_KEY = 'dev-preview-role';

function readStoredPreviewRole(): PreviewRole | null {
  if (!DEV_MODE_ENABLED) return null;
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored && ['PO', 'PC', 'PR', 'STUDENT'].includes(stored)
      ? (stored as PreviewRole)
      : null;
  } catch {
    return null;
  }
}

interface DevModeContextType {
  devMode: boolean;
  previewRole: PreviewRole | null;
  setPreviewRole: (role: PreviewRole | null) => void;
  isPreviewing: boolean;
  exitPreview: () => void;
}

const DevModeContext = createContext<DevModeContextType | undefined>(undefined);

export function DevModeProvider({ children }: { children: ReactNode }) {
  const [previewRole, setPreviewRoleState] = useState<PreviewRole | null>(
    () => readStoredPreviewRole()
  );

  const setPreviewRole = (role: PreviewRole | null) => {
    setPreviewRoleState(role);
    try {
      if (role) localStorage.setItem(STORAGE_KEY, role);
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  };

  const exitPreview = () => setPreviewRole(null);

  return (
    <DevModeContext.Provider
      value={{
        devMode: DEV_MODE_ENABLED,
        previewRole,
        setPreviewRole,
        isPreviewing: DEV_MODE_ENABLED && !!previewRole,
        exitPreview,
      }}
    >
      {children}
    </DevModeContext.Provider>
  );
}

export function useDevMode() {
  const context = useContext(DevModeContext);
  if (!context) {
    throw new Error('useDevMode must be used within a DevModeProvider');
  }
  return context;
}
