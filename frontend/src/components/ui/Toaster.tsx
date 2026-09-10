import { Toaster as HotToaster } from 'react-hot-toast';

export default function AppToaster() {
  return (
    <HotToaster
      position="top-right"
      toastOptions={{
        duration: 4000,
        style: {
          borderRadius: '12px',
          padding: '14px 18px',
          fontSize: '14px',
          fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif',
          background: 'rgba(255,255,255,0.85)',
          backdropFilter: 'blur(16px) saturate(180%)',
          WebkitBackdropFilter: 'blur(16px) saturate(180%)',
          color: '#111827',
          border: '1px solid rgba(255,255,255,0.5)',
          boxShadow: '0 8px 32px rgba(0,0,0,0.08), 0 2px 8px rgba(0,0,0,0.04)',
        },
        success: {
          iconTheme: { primary: '#665CF6', secondary: '#fff' },
        },
        error: {
          iconTheme: { primary: '#DC2626', secondary: '#fff' },
        },
      }}
    />
  );
}
