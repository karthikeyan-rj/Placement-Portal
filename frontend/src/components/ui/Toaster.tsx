import { Toaster as HotToaster } from 'react-hot-toast';

export default function AppToaster() {
  return (
    <HotToaster
      position="top-right"
      toastOptions={{
        duration: 4000,
        style: {
          borderRadius: '10px',
          padding: '14px 18px',
          fontSize: '14px',
          fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif',
          background: '#ffffff',
          color: '#0F172A',
          border: '1px solid #E2E8F0',
          boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
        },
        success: {
          iconTheme: { primary: '#4F46E5', secondary: '#fff' },
        },
        error: {
          iconTheme: { primary: '#DC2626', secondary: '#fff' },
        },
      }}
    />
  );
}
