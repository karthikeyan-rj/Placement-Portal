import { Outlet } from 'react-router-dom';

export default function PublicLayout() {
  return (
    <div className="min-h-screen bg-background text-neutral-900">
      <Outlet />
    </div>
  );
}
