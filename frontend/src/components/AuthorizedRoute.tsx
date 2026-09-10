import { Navigate, Outlet } from 'react-router-dom';
import { useEffectiveRole } from '../hooks/useEffectiveRole';

type Role = 'PO' | 'PC' | 'PR' | 'STUDENT';

interface AuthorizedRouteProps {
  allowedRoles: Role[];
}

export default function AuthorizedRoute({ allowedRoles }: AuthorizedRouteProps) {
  const role = useEffectiveRole();

  if (role && allowedRoles.includes(role)) {
    return <Outlet />;
  }

  return <Navigate to="/unauthorized" replace />;
}
