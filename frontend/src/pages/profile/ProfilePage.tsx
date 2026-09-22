import { useEffectiveRole } from '../../hooks/useEffectiveRole';
import StudentProfileView from '../student/ProfilePage';
import StaffProfilePage from './StaffProfilePage';

export default function ProfilePage() {
  const role = useEffectiveRole();
  if (role === 'PO' || role === 'PC') return <StaffProfilePage />;
  return <StudentProfileView />;
}
